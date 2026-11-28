package com.example.domain.engine

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.example.domain.model.PresetCatalog
import com.example.domain.model.StudioProject
import com.example.domain.model.TrackType
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tanh

data class EncodedAacFrame(
    val data: ByteArray,
    val presentationTimeUs: Long,
    val flags: Int
)

data class EncodedAudioTrack(
    val format: MediaFormat,
    val frames: List<EncodedAacFrame>
)

object AudioExportMixer {

    const val SAMPLE_RATE = 44100

    fun buildAudioFingerprint(project: StudioProject): String {
        val sb = StringBuilder(256)
        sb.append(project.totalDurationMs).append('|').append(project.masterVolume).append('|')
        project.trackLayers.sortedBy { it.trackType.name }.forEach {
            sb.append(it.trackType.name).append(':').append(it.isVisible).append(';')
        }
        project.clips.forEach { clip ->
            val hasAudio = !clip.isHidden && (
                clip.trackType == TrackType.VIDEO ||
                    clip.trackType == TrackType.VOICEOVER ||
                    clip.trackType == TrackType.MUSIC ||
                    clip.lockedSfxId != null
                )
            if (hasAudio) {
                sb.append(clip.id).append(',')
                    .append(clip.trackType.name).append(',')
                    .append(clip.startMs).append(',')
                    .append(clip.durationMs).append(',')
                    .append(clip.trimStartMs).append(',')
                    .append(clip.sourceDurationMs).append(',')
                    .append(clip.playbackSpeed).append(',')
                    .append(clip.lockedSfxId ?: "").append(',')
                    .append(clip.mediaSource?.uriString ?: "").append(',')
                    .append(clip.audioConfig.audioPath ?: "").append(',')
                    .append(clip.audioConfig.volume).append(',')
                    .append(clip.audioConfig.isMuted).append(',')
                    .append(clip.audioConfig.isSolo).append(',')
                    .append(clip.audioConfig.autoDuckingEnabled).append(',')
                    .append(clip.audioConfig.duckingFloor).append(',')
                    .append(clip.audioConfig.fadeInMs).append(',')
                    .append(clip.audioConfig.fadeOutMs).append(',')
                    .append(clip.audioConfig.noiseReductionEnabled).append(',')
                    .append(clip.audioConfig.waveformSeed).append(';')
            }
        }
        return sb.toString()
    }

    /**
     * Mixes Video audio + Voiceover clips, Auto-Ducked Music clips, and Locked SFX across the project timeline
     * into a 44.1 kHz Mono 16-bit PCM array honoring trimStartMs, playbackSpeed, Auto-Ducking, and soft limiting.
     */
    fun mixProjectAudioPcm(context: Context, project: StudioProject): ShortArray {
        val totalDurationMs = project.totalDurationMs.coerceIn(1000L, MediaProbeHelper.MAX_TIMELINE_DURATION_MS)
        val totalSamples = ((totalDurationMs * SAMPLE_RATE) / 1000L).toInt().coerceAtLeast(SAMPLE_RATE)
        val mixBuffer = FloatArray(totalSamples)

        val trackLayerMap = project.trackLayers.associateBy { it.trackType }
        val activeClips = project.clips.filter { clip ->
            !clip.isHidden && (trackLayerMap[clip.trackType]?.isVisible != false)
        }

        // 1. Mix Embedded Video Audio + Voiceover + Music tracks
        activeClips.filter {
            it.trackType == TrackType.VIDEO || it.trackType == TrackType.VOICEOVER || it.trackType == TrackType.MUSIC
        }.forEach { clip ->
            val clipSamplesCount = ((clip.durationMs * SAMPLE_RATE) / 1000L).toInt().coerceAtLeast(1)
            val speed = clip.playbackSpeed.coerceIn(0.25f, 4.0f)
            val neededSourceMs = if (clip.trackType == TrackType.MUSIC) {
                (clip.sourceDurationMs.takeIf { it > 0L } ?: (clip.durationMs * speed).toLong()).coerceAtLeast(1000L)
            } else {
                (clip.durationMs * speed).toLong().coerceAtLeast(200L)
            }
            val maxSourceSamples = ((neededSourceMs * SAMPLE_RATE) / 1000L).toInt().coerceAtLeast(1)
            val resolvedAudioSource = clip.audioConfig.audioPath
                ?: if (clip.trackType == TrackType.VIDEO && clip.mediaSource?.isVideo == true) clip.mediaSource.uriString else null

            val sourcePcm = decodeClipAudio(
                context = context,
                pathOrUri = resolvedAudioSource,
                trimStartMs = clip.trimStartMs.coerceAtLeast(0L),
                maxSamples = maxSourceSamples
            ) ?: if (clip.trackType == TrackType.MUSIC) {
                generateAmbientCinemaPad(min(clipSamplesCount, SAMPLE_RATE * 60), clip.audioConfig.waveformSeed)
            } else {
                null
            }

            if (sourcePcm != null && sourcePcm.isNotEmpty()) {
                val startSample = ((clip.startMs * SAMPLE_RATE) / 1000L).toInt().coerceAtLeast(0)
                val maxPlayableSamples = if (clip.trackType == TrackType.MUSIC) {
                    clipSamplesCount
                } else {
                    min(clipSamplesCount, (sourcePcm.size / speed).toInt().coerceAtLeast(1))
                }
                val endSample = min(totalSamples, startSample + maxPlayableSamples)
                val blockSize = 256
                var idx = startSample
                while (idx < endSample) {
                    val blockEnd = min(endSample, idx + blockSize)
                    val tStartMs = (idx.toLong() * 1000L) / SAMPLE_RATE
                    val tEndMs = (blockEnd.toLong() * 1000L) / SAMPLE_RATE
                    val gainStart = AudioSynthEngine.computeEffectiveAudioGain(clip, project, tStartMs)
                    val gainEnd = AudioSynthEngine.computeEffectiveAudioGain(clip, project, tEndMs)
                    val blockSpan = (blockEnd - idx).coerceAtLeast(1)

                    for (s in idx until blockEnd) {
                        val rel = s - startSample
                        val srcIndex = (rel * speed).toInt()
                        val srcSample = if (clip.trackType == TrackType.MUSIC) {
                            sourcePcm[srcIndex % sourcePcm.size]
                        } else {
                            if (srcIndex in sourcePcm.indices) sourcePcm[srcIndex] else 0
                        }
                        val frac = (s - idx).toFloat() / blockSpan
                        val gain = gainStart + (gainEnd - gainStart) * frac
                        val sampleNorm = srcSample / 32767f
                        mixBuffer[s] += sampleNorm * gain
                    }
                    idx = blockEnd
                }
            }
        }

        // 2. Mix Locked SFX attached to any clip
        val anySoloActive = activeClips.any {
            !it.audioConfig.isMuted && it.audioConfig.isSolo &&
                (it.trackType == TrackType.VIDEO || it.trackType == TrackType.VOICEOVER || it.trackType == TrackType.MUSIC)
        }
        activeClips.forEach { clip ->
            if (clip.audioConfig.isMuted) return@forEach
            if (anySoloActive && !clip.audioConfig.isSolo) return@forEach
            val sfxId = clip.lockedSfxId ?: return@forEach
            val preset = PresetCatalog.sfxPresets.firstOrNull { it.id == sfxId } ?: return@forEach
            val sfxPcm = AudioSynthEngine.generateSfxPcm(
                preset = preset,
                volume = (clip.audioConfig.volume * project.masterVolume).coerceIn(0f, 1.5f)
            )
            val startSample = ((clip.startMs * SAMPLE_RATE) / 1000L).toInt().coerceAtLeast(0)
            val endSample = min(totalSamples, startSample + sfxPcm.size)
            for (s in startSample until endSample) {
                mixBuffer[s] += sfxPcm[s - startSample] / 32767f
            }
        }

        // 3. Soft-clip & convert to 16-bit PCM
        val out = ShortArray(totalSamples)
        for (i in 0 until totalSamples) {
            val limited = tanh(mixBuffer[i].toDouble()).toFloat().coerceIn(-1f, 1f)
            out[i] = (limited * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    /**
     * Encodes mixed 44.1 kHz Mono PCM samples into AAC frames ready for dual-track MediaMuxer.
     */
    fun encodePcmToAacFrames(pcmSamples: ShortArray, sampleRate: Int = SAMPLE_RATE): EncodedAudioTrack? {
        var codec: MediaCodec? = null
        return try {
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }
            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            val frames = mutableListOf<EncodedAacFrame>()
            var outputFormat: MediaFormat? = null
            val bufferInfo = MediaCodec.BufferInfo()

            val totalSamples = pcmSamples.size
            var sampleCursor = 0
            var inputDone = false
            var outputDone = false
            val samplesPerChunk = 1024

            var idleIterations = 0
            while (!outputDone && idleIterations < 600) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(5_000L)
                    if (inIdx >= 0) {
                        val inputBuf = codec.getInputBuffer(inIdx)
                        if (inputBuf != null) {
                            inputBuf.clear()
                            if (sampleCursor >= totalSamples) {
                                codec.queueInputBuffer(
                                    inIdx, 0, 0,
                                    (sampleCursor.toLong() * 1_000_000L) / sampleRate,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputDone = true
                            } else {
                                val remaining = totalSamples - sampleCursor
                                val maxSamplesInBuf = inputBuf.remaining() / 2
                                val count = min(min(samplesPerChunk, remaining), maxSamplesInBuf)
                                inputBuf.order(ByteOrder.LITTLE_ENDIAN)
                                for (i in 0 until count) {
                                    inputBuf.putShort(pcmSamples[sampleCursor + i])
                                }
                                val ptsUs = (sampleCursor.toLong() * 1_000_000L) / sampleRate
                                sampleCursor += count
                                codec.queueInputBuffer(inIdx, 0, count * 2, ptsUs, 0)
                            }
                            idleIterations = 0
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(bufferInfo, 5_000L)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        outputFormat = codec.outputFormat
                        idleIterations = 0
                    }
                    outIdx >= 0 -> {
                        idleIterations = 0
                        val outBuf = codec.getOutputBuffer(outIdx)
                        if (outBuf != null && bufferInfo.size > 0 && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            val bytes = ByteArray(bufferInfo.size)
                            outBuf.get(bytes)
                            frames.add(EncodedAacFrame(bytes, bufferInfo.presentationTimeUs, bufferInfo.flags))
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                    else -> idleIterations++
                }
            }

            runCatching { codec.stop() }
            runCatching { codec.release() }
            codec = null

            outputFormat?.let { EncodedAudioTrack(it, frames) }
        } catch (_: Throwable) {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            null
        }
    }

    private fun decodeClipAudio(
        context: Context,
        pathOrUri: String?,
        trimStartMs: Long,
        maxSamples: Int
    ): ShortArray? {
        if (pathOrUri.isNullOrBlank()) return null
        if (pathOrUri.endsWith(".wav", ignoreCase = true) && !pathOrUri.startsWith("content://")) {
            val wavFile = File(pathOrUri)
            if (wavFile.exists() && wavFile.length() > 44L) {
                return readWavPcm(wavFile, trimStartMs, maxSamples)
            }
        }
        return decodeViaMediaExtractor(context, pathOrUri, trimStartMs, maxSamples)
    }

    private fun readWavPcm(file: File, trimStartMs: Long, maxSamples: Int): ShortArray? {
        return runCatching {
            FileInputStream(file).use { fis ->
                val skipSamples = ((trimStartMs.coerceAtLeast(0L) * SAMPLE_RATE) / 1000L)
                fis.skip(44L + skipSamples * 2L)
                val bytesToRead = min(maxSamples * 2, (file.length() - 44L - skipSamples * 2L).coerceAtLeast(0L).toInt())
                if (bytesToRead <= 1) return@use ShortArray(0)
                val bytes = ByteArray(bytesToRead)
                var readTotal = 0
                while (readTotal < bytesToRead) {
                    val r = fis.read(bytes, readTotal, bytesToRead - readTotal)
                    if (r <= 0) break
                    readTotal += r
                }
                val sampleCount = readTotal / 2
                val bb = ByteBuffer.wrap(bytes, 0, sampleCount * 2).order(ByteOrder.LITTLE_ENDIAN)
                ShortArray(sampleCount) { bb.short }
            }
        }.getOrNull()
    }

    private fun decodeViaMediaExtractor(
        context: Context,
        pathOrUri: String,
        trimStartMs: Long,
        maxSamples: Int
    ): ShortArray? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            if (!MediaProbeHelper.setExtractorDataSource(context, extractor, pathOrUri)) {
                return null
            }
            var trackIndex = -1
            var trackFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    trackFormat = fmt
                    break
                }
            }
            if (trackIndex < 0 || trackFormat == null) return null

            extractor.selectTrack(trackIndex)
            val trimStartUs = trimStartMs.coerceAtLeast(0L) * 1000L
            if (trimStartUs > 0L) {
                extractor.seekTo(trimStartUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            }
            val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: return null
            val srcRate = if (trackFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) else SAMPLE_RATE
            val srcChannels = if (trackFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 1

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(trackFormat, null, null, 0)
            codec.start()

            val srcMaxSamples = ((maxSamples.toLong() * srcRate.coerceAtLeast(8000)) / SAMPLE_RATE)
                .toInt()
                .coerceAtLeast(maxSamples)
            var decodedBuf = ShortArray(min(srcMaxSamples, srcRate * 8).coerceAtLeast(4096))
            var decodedCount = 0
            val info = MediaCodec.BufferInfo()
            var sawInputEOS = false
            var sawOutputEOS = false
            var stallCount = 0

            while (!sawOutputEOS && decodedCount < srcMaxSamples && stallCount < 450) {
                var progressed = false
                if (!sawInputEOS) {
                    val inIdx = codec.dequeueInputBuffer(4_000L)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx)
                        if (buf != null) {
                            val sampleSize = extractor.readSampleData(buf, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                sawInputEOS = true
                            } else {
                                codec.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                            progressed = true
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 4_000L)
                if (outIdx >= 0) {
                    progressed = true
                    val outBuf = codec.getOutputBuffer(outIdx)
                    if (outBuf != null && info.size > 0) {
                        outBuf.position(info.offset)
                        outBuf.limit(info.offset + info.size)
                        outBuf.order(ByteOrder.LITTLE_ENDIAN)
                        val ch = srcChannels.coerceAtLeast(1)
                        val totalShorts = info.size / 2
                        val framesInChunk = totalShorts / ch
                        val beforeTrimEnd = info.presentationTimeUs + ((framesInChunk * 1_000_000L) / srcRate.coerceAtLeast(8000))
                        if (framesInChunk > 0 && (trimStartUs <= 0L || beforeTrimEnd >= trimStartUs)) {
                            val skipFrames = if (trimStartUs > info.presentationTimeUs) {
                                (((trimStartUs - info.presentationTimeUs) * srcRate) / 1_000_000L)
                                    .toInt()
                                    .coerceIn(0, framesInChunk)
                            } else 0
                            val keepFrames = framesInChunk - skipFrames
                            if (keepFrames > 0) {
                                val needed = decodedCount + keepFrames
                                if (needed > decodedBuf.size) {
                                    var nextCap = decodedBuf.size * 2
                                    while (nextCap < needed) nextCap *= 2
                                    decodedBuf = decodedBuf.copyOf(min(nextCap, srcMaxSamples + 4096))
                                }
                                var s = 0
                                while (s + ch - 1 < totalShorts) {
                                    var sum = 0
                                    for (c in 0 until ch) {
                                        sum += outBuf.short.toInt()
                                    }
                                    val frameIdx = s / ch
                                    if (frameIdx >= skipFrames && decodedCount < decodedBuf.size) {
                                        decodedBuf[decodedCount++] = (sum / ch).toShort()
                                    }
                                    s += ch
                                }
                            }
                        }
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        sawOutputEOS = true
                    }
                } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    progressed = true
                }
                stallCount = if (progressed) 0 else stallCount + 1
            }

            if (decodedCount <= 0) return null
            if (srcRate == SAMPLE_RATE) {
                decodedBuf.copyOf(min(decodedCount, maxSamples))
            } else {
                // Simple linear resample from srcRate to 44100 Hz
                val ratio = srcRate.toDouble() / SAMPLE_RATE.toDouble()
                val outLen = min(maxSamples, (decodedCount / ratio).toInt().coerceAtLeast(1))
                val lastIdx = decodedCount - 1
                ShortArray(outLen) { i ->
                    val srcPos = i * ratio
                    val idx0 = srcPos.toInt().coerceIn(0, lastIdx)
                    val idx1 = (idx0 + 1).coerceIn(0, lastIdx)
                    val frac = (srcPos - idx0).toFloat()
                    (decodedBuf[idx0] * (1f - frac) + decodedBuf[idx1] * frac).toInt().toShort()
                }
            }
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun generateAmbientCinemaPad(sampleCount: Int, seed: Int): ShortArray {
        val baseFreq = 98f + (seed % 5) * 12f // Deep warm root (G2-C3)
        val out = ShortArray(sampleCount)
        var p1 = 0.0; var p2 = 0.0; var p3 = 0.0; var p4 = 0.0
        for (i in 0 until sampleCount) {
            val t = i.toDouble() / SAMPLE_RATE
            val swell = 0.55 + 0.45 * sin(2.0 * PI * 0.16 * t)
            p1 += 2.0 * PI * baseFreq / SAMPLE_RATE
            p2 += 2.0 * PI * (baseFreq * 1.189) / SAMPLE_RATE // minor 3rd
            p3 += 2.0 * PI * (baseFreq * 1.498) / SAMPLE_RATE // 5th
            p4 += 2.0 * PI * (baseFreq * 2.245) / SAMPLE_RATE // warm 9th shimmer
            val wave = 0.45 * sin(p1) + 0.22 * sin(p2) + 0.24 * sin(p3) + 0.12 * sin(p4)
            val warm = kotlin.math.tanh(wave * swell * 1.15) * 0.24
            out[i] = (warm.coerceIn(-0.95, 0.95) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }
}
