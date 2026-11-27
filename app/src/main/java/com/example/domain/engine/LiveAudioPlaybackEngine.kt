package com.example.domain.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.domain.model.StudioProject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Streams real-time mixed PCM (Voiceover + Auto-Ducked BGM + Locked SFX)
 * through Android AudioTrack synchronized with the editor playhead,
 * caching the mixed PCM buffer so scrubbing and pause/resume have zero decode lag.
 */
class LiveAudioPlaybackEngine {

    private var streamJob: Job? = null
    @Volatile
    private var cachedFingerprint: String? = null
    @Volatile
    private var cachedPcm: ShortArray? = null

    fun startFrom(
        scope: CoroutineScope,
        context: Context,
        project: StudioProject,
        startPlayheadMs: Long,
        debounceMs: Long = 0L
    ) {
        stop()
        streamJob = scope.launch(Dispatchers.IO) {
            if (debounceMs > 0L) delay(debounceMs)
            if (!isActive) return@launch

            var track: AudioTrack? = null
            try {
                val fingerprint = AudioExportMixer.buildAudioFingerprint(project)
                val mixedPcm = if (fingerprint == cachedFingerprint && cachedPcm != null) {
                    cachedPcm!!
                } else {
                    AudioExportMixer.mixProjectAudioPcm(context, project).also { freshlyMixed ->
                        cachedFingerprint = fingerprint
                        cachedPcm = freshlyMixed
                    }
                }
                if (!isActive || mixedPcm.isEmpty()) return@launch

                val sampleRate = AudioExportMixer.SAMPLE_RATE
                val startSample = ((startPlayheadMs.coerceAtLeast(0L) * sampleRate) / 1000L)
                    .toInt()
                    .coerceIn(0, (mixedPcm.size - 1).coerceAtLeast(0))

                val minBuf = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(4096)

                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(sampleRate)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBuf)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                track.play()

                var cursor = startSample
                val chunkSamples = 2048
                while (isActive) {
                    val remaining = mixedPcm.size - cursor
                    if (remaining <= 0) {
                        cursor = 0
                        continue
                    }
                    val toWrite = minOf(chunkSamples, remaining)
                    val written = track.write(mixedPcm, cursor, toWrite)
                    if (written <= 0) break
                    cursor += written
                }
            } catch (_: Throwable) {
                // Safe fallback on headless/unit environments where AudioTrack hardware is absent
            } finally {
                runCatching { track?.stop() }
                runCatching { track?.release() }
            }
        }
    }

    fun clearCache() {
        cachedFingerprint = null
        cachedPcm = null
    }

    fun stop() {
        streamJob?.cancel()
        streamJob = null
    }
}
