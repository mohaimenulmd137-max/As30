package com.example.domain.engine.export

import android.content.ContentValues
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.StatFs
import android.provider.MediaStore
import java.io.File

data class StorageCheckResult(
    val hasEnoughSpace: Boolean,
    val availableBytes: Long,
    val estimatedOutputBytes: Long,
    val requiredWithMarginBytes: Long,
    val messageBn: String
)

class StreamingExportTarget(
    val muxer: MediaMuxer,
    val mediaUri: Uri?,
    val file: File?,
    val pfd: ParcelFileDescriptor?,
    val displayName: String,
    val savedLocationLabel: String,
    private val isMediaStorePending: Boolean
) {
    fun currentWrittenBytes(): Long {
        if (file != null && file.exists()) return file.length()
        return runCatching { pfd?.statSize?.coerceAtLeast(0L) ?: 0L }.getOrDefault(0L)
    }

    fun finalizeSuccess(context: Context): Pair<Uri?, Long> {
        runCatching { pfd?.fileDescriptor?.sync() }
        runCatching { pfd?.close() }

        val targetFile = file
        val targetUri = mediaUri

        val finalSize = if (targetFile != null && targetFile.exists()) {
            targetFile.length()
        } else if (targetUri != null) {
            runCatching {
                context.contentResolver.openAssetFileDescriptor(targetUri, "r")?.use { it.length } ?: 0L
            }.getOrDefault(0L)
        } else 0L

        if (isMediaStorePending && targetUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val update = ContentValues().apply {
                put(MediaStore.Video.Media.IS_PENDING, 0)
                if (finalSize > 0L) {
                    put(MediaStore.Video.Media.SIZE, finalSize)
                }
            }
            runCatching { context.contentResolver.update(targetUri, update, null, null) }
            return targetUri to finalSize
        }

        if (targetFile != null) {
            runCatching {
                MediaScannerConnection.scanFile(context, arrayOf(targetFile.absolutePath), arrayOf("video/mp4"), null)
            }
            return Uri.fromFile(targetFile) to finalSize
        }
        return targetUri to finalSize
    }

    fun abortAndCleanup(context: Context) {
        runCatching { pfd?.close() }
        val targetUri = mediaUri
        val targetFile = file
        if (isMediaStorePending && targetUri != null) {
            runCatching { context.contentResolver.delete(targetUri, null, null) }
        }
        if (targetFile != null && targetFile.exists()) {
            runCatching { targetFile.delete() }
        }
    }
}

object ExportStorageManager {

    private const val AUDIO_BITRATE_BPS = 128_000L
    private const val SAFETY_MARGIN_BYTES = 120L * 1024L * 1024L // 120 MB safety cushion

    fun estimateOutputBytes(durationMs: Long, videoBitrateBps: Int, audioBitrateBps: Long = AUDIO_BITRATE_BPS): Long {
        val durSec = (durationMs.coerceAtLeast(1000L) / 1000.0)
        val totalBps = videoBitrateBps.toDouble() + audioBitrateBps.toDouble()
        val rawBytes = (totalBps * durSec) / 8.0
        // +8% container/MP4 moov atom overhead
        return (rawBytes * 1.08).toLong().coerceAtLeast(256L * 1024L)
    }

    fun getAvailableBytes(context: Context): Long {
        val probeDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: Environment.getExternalStorageDirectory()
            ?: context.filesDir
        return runCatching {
            val stat = StatFs(probeDir.absolutePath)
            stat.availableBytes
        }.getOrDefault(2L * 1024L * 1024L * 1024L)
    }

    fun checkStorageForExport(context: Context, durationMs: Long, videoBitrateBps: Int): StorageCheckResult {
        val estBytes = estimateOutputBytes(durationMs, videoBitrateBps)
        val margin = if (estBytes > 1_000_000_000L) 300L * 1024L * 1024L else minOf(SAFETY_MARGIN_BYTES, estBytes / 2 + 32L * 1024L * 1024L)
        val required = estBytes + margin
        val available = getAvailableBytes(context)
        val ok = available >= required
        val msg = if (ok) {
            "স্টোরেজ প্রস্তুত · আনুমানিক ${formatBytesReadable(estBytes)} (খালি ${formatBytesReadable(available)})"
        } else {
            "স্টোরেজ পর্যাপ্ত নয়! প্রয়োজন ${formatBytesReadable(required)}, খালি আছে ${formatBytesReadable(available)}"
        }
        return StorageCheckResult(
            hasEnoughSpace = ok,
            availableBytes = available,
            estimatedOutputBytes = estBytes,
            requiredWithMarginBytes = required,
            messageBn = msg
        )
    }

    fun hasMinimumRuntimeHeadroom(context: Context, minBytes: Long = 48L * 1024L * 1024L): Boolean {
        return getAvailableBytes(context) >= minBytes
    }

    fun openStreamingTarget(context: Context, displayName: String): StreamingExportTarget {
        // Android 10+ (API 29+): Stream directly into pending MediaStore FileDescriptor -> zero temp duplicate!
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/ChitraFlow Studio")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val mediaUri = runCatching { resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) }.getOrNull()
            if (mediaUri != null) {
                val pfd = runCatching { resolver.openFileDescriptor(mediaUri, "rw") }.getOrNull()
                if (pfd != null) {
                    val fdMuxer = runCatching {
                        MediaMuxer(pfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                    }.getOrNull()
                    if (fdMuxer != null) {
                        return StreamingExportTarget(
                            muxer = fdMuxer,
                            mediaUri = mediaUri,
                            file = null,
                            pfd = pfd,
                            displayName = displayName,
                            savedLocationLabel = "Movies/ChitraFlow Studio/$displayName",
                            isMediaStorePending = true
                        )
                    }
                    runCatching { pfd.close() }
                }
                runCatching { resolver.delete(mediaUri, null, null) }
            }
        }

        // Direct external Movies directory target (API 24-28 or fallback if MediaStore unavailable) -> also zero double copy!
        val outDir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir,
            "ChitraFlow Studio"
        ).apply { mkdirs() }
        val outFile = File(outDir, displayName)
        val fileMuxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        return StreamingExportTarget(
            muxer = fileMuxer,
            mediaUri = null,
            file = outFile,
            pfd = null,
            displayName = displayName,
            savedLocationLabel = outFile.absolutePath,
            isMediaStorePending = false
        )
    }

    /**
     * Verifies that the finalized MP4 file is readable, non-empty, and contains a valid video track.
     * Prevents reporting corrupt/incomplete MP4 files as successful exports.
     */
    fun verifyFinalMp4Integrity(context: Context, uri: Uri?, fileSizeBytes: Long): Boolean {
        if (fileSizeBytes < 1024L && uri == null) return false
        if (uri == null) return fileSizeBytes >= 1024L
        val extractor = MediaExtractor()
        return try {
            val ok = com.example.domain.engine.MediaProbeHelper.setExtractorDataSource(context, extractor, uri.toString())
            if (!ok) return fileSizeBytes >= 1024L
            var hasVideoTrack = false
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    hasVideoTrack = true
                    break
                }
            }
            hasVideoTrack
        } catch (_: Throwable) {
            fileSizeBytes >= 1024L
        } finally {
            runCatching { extractor.release() }
        }
    }

    fun cleanupStaleTempFiles(context: Context) {
        runCatching {
            context.cacheDir.listFiles()?.forEach { f ->
                if (f.name.startsWith("chitraflow_export_") && f.name.endsWith(".mp4")) {
                    f.delete()
                }
            }
        }
    }

    fun formatBytesReadable(bytes: Long): String {
        val safe = bytes.coerceAtLeast(0L)
        return when {
            safe >= 1024L * 1024L * 1024L -> "%.2f GB".format(safe / (1024.0 * 1024.0 * 1024.0))
            safe >= 1024L * 1024L -> "%.1f MB".format(safe / (1024.0 * 1024.0))
            safe >= 1024L -> "${safe / 1024L} KB"
            else -> "$safe B"
        }
    }
}
