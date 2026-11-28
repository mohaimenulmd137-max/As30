package com.example.domain.engine

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri

data class ProbedMediaInfo(
    val durationMs: Long,
    val isVideo: Boolean,
    val hasAudio: Boolean,
    val width: Int = 0,
    val height: Int = 0,
    val mimeType: String? = null
)

object MediaProbeHelper {

    /** 2 hours max timeline/clip ceiling (replaces the old 10-minute / 5-second bottlenecks). */
    const val MAX_TIMELINE_DURATION_MS = 7_200_000L

    fun probeMedia(
        context: Context,
        uri: Uri,
        hintIsVideo: Boolean? = null,
        fallbackDurationMs: Long = 5_000L
    ): ProbedMediaInfo {
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        if (mime?.startsWith("image/") == true && hintIsVideo != true) {
            return ProbedMediaInfo(
                durationMs = fallbackDurationMs.coerceIn(500L, MAX_TIMELINE_DURATION_MS),
                isVideo = false,
                hasAudio = false,
                mimeType = mime
            )
        }

        val uriStr = uri.toString()
        var extractorVideo = false
        var extractorAudio = false
        var extractorDurationMs: Long? = null
        var width = 0
        var height = 0

        val extractor = MediaExtractor()
        try {
            if (setExtractorDataSource(context, extractor, uriStr)) {
                var maxDurUs = 0L
                for (i in 0 until extractor.trackCount) {
                    val fmt = extractor.getTrackFormat(i)
                    val trackMime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                    if (trackMime.startsWith("video/")) {
                        extractorVideo = true
                        if (fmt.containsKey(MediaFormat.KEY_WIDTH)) {
                            width = fmt.getInteger(MediaFormat.KEY_WIDTH)
                        }
                        if (fmt.containsKey(MediaFormat.KEY_HEIGHT)) {
                            height = fmt.getInteger(MediaFormat.KEY_HEIGHT)
                        }
                    } else if (trackMime.startsWith("audio/")) {
                        extractorAudio = true
                    }
                    if (fmt.containsKey(MediaFormat.KEY_DURATION)) {
                        maxDurUs = maxOf(maxDurUs, fmt.getLong(MediaFormat.KEY_DURATION))
                    }
                }
                if (maxDurUs > 0L) {
                    extractorDurationMs = maxDurUs / 1000L
                }
            }
        } catch (_: Throwable) {
        } finally {
            runCatching { extractor.release() }
        }

        var mmrVideo = false
        var mmrAudio = false
        var mmrDurationMs: Long? = null
        val mmr = MediaMetadataRetriever()
        try {
            if (setRetrieverDataSource(context, mmr, uriStr)) {
                mmrDurationMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                    ?.takeIf { it > 0L }
                mmrVideo = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
                    ?.equals("yes", ignoreCase = true) == true
                mmrAudio = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
                    ?.equals("yes", ignoreCase = true) == true
                if (width <= 0) {
                    width = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                }
                if (height <= 0) {
                    height = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                }
            }
        } catch (_: Throwable) {
        } finally {
            runCatching { mmr.release() }
        }

        val detectedIsVideo = extractorVideo ||
            mmrVideo ||
            (mime?.startsWith("video/") == true) ||
            (hintIsVideo == true && !extractorAudio && !mmrAudio)
        val detectedHasAudio = extractorAudio ||
            mmrAudio ||
            (mime?.startsWith("audio/") == true) ||
            detectedIsVideo

        var mpDurationMs: Long? = null
        if ((mmrDurationMs == null && extractorDurationMs == null) && (detectedIsVideo || detectedHasAudio)) {
            val mp = MediaPlayer()
            try {
                if (setMediaPlayerDataSource(context, mp, uriStr)) {
                    mp.prepare()
                    val d = mp.duration.toLong()
                    if (d > 0L) mpDurationMs = d
                }
            } catch (_: Throwable) {
            } finally {
                runCatching { mp.release() }
            }
        }

        val resolvedDuration = listOfNotNull(mmrDurationMs, extractorDurationMs, mpDurationMs)
            .filter { it > 0L }
            .maxOrNull()
            ?: fallbackDurationMs

        return ProbedMediaInfo(
            durationMs = resolvedDuration.coerceIn(500L, MAX_TIMELINE_DURATION_MS),
            isVideo = detectedIsVideo,
            hasAudio = detectedHasAudio,
            width = width,
            height = height,
            mimeType = mime
        )
    }

    fun probeDurationMs(context: Context, uri: Uri, fallbackMs: Long): Long {
        return probeMedia(context, uri, hintIsVideo = null, fallbackDurationMs = fallbackMs).durationMs
    }

    fun setExtractorDataSource(context: Context, extractor: MediaExtractor, pathOrUri: String): Boolean {
        val isContentOrFileUri = pathOrUri.startsWith("content://") || pathOrUri.startsWith("file://")
        if (isContentOrFileUri) {
            val uri = Uri.parse(pathOrUri)
            val directOk = runCatching { extractor.setDataSource(context, uri, null) }.isSuccess
            if (directOk) return true
            return runCatching {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    extractor.setDataSource(pfd.fileDescriptor)
                } ?: throw IllegalStateException("Null PFD")
            }.isSuccess
        }
        return runCatching { extractor.setDataSource(pathOrUri) }.isSuccess
    }

    fun setRetrieverDataSource(context: Context, mmr: MediaMetadataRetriever, pathOrUri: String): Boolean {
        val isContentOrFileUri = pathOrUri.startsWith("content://") || pathOrUri.startsWith("file://")
        if (isContentOrFileUri) {
            val uri = Uri.parse(pathOrUri)
            val directOk = runCatching { mmr.setDataSource(context, uri) }.isSuccess
            if (directOk) return true
            return runCatching {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    mmr.setDataSource(pfd.fileDescriptor)
                } ?: throw IllegalStateException("Null PFD")
            }.isSuccess
        }
        return runCatching { mmr.setDataSource(pathOrUri) }.isSuccess
    }

    fun setMediaPlayerDataSource(context: Context, mp: MediaPlayer, pathOrUri: String): Boolean {
        val isContentOrFileUri = pathOrUri.startsWith("content://") || pathOrUri.startsWith("file://")
        if (isContentOrFileUri) {
            val uri = Uri.parse(pathOrUri)
            val directOk = runCatching { mp.setDataSource(context, uri) }.isSuccess
            if (directOk) return true
            return runCatching {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    mp.setDataSource(pfd.fileDescriptor)
                } ?: throw IllegalStateException("Null PFD")
            }.isSuccess
        }
        return runCatching { mp.setDataSource(pathOrUri) }.isSuccess
    }

    fun formatDurationReadable(ms: Long): String {
        val clamped = ms.coerceAtLeast(0L)
        val totalSec = clamped / 1000L
        val hours = totalSec / 3600L
        val mins = (totalSec % 3600L) / 60L
        val secs = totalSec % 60L
        return if (hours > 0L) {
            "%d:%02d:%02d".format(hours, mins, secs)
        } else {
            "%02d:%02d".format(mins, secs)
        }
    }
}
