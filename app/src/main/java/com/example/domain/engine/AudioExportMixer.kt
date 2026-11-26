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

    /**
     * Mixes Voiceover clips, Auto-Ducked Music clips, and Locked SFX across the project timeline
     * into a 44.1 kHz Mono 16-bit PCM array with smooth Auto-Ducking and soft limiting.
     */
    fun mixProjectAudioPcm(context: Context, project: StudioProject): ShortArray {
        val totalDurationMs = project.totalDurationMs.coerceIn(1000L, 120_000L)
        val totalSamples = ((totalDurationMs * SAMPLE_RATE) / 1000L).toInt().coerceAtLeast(SAMPLE_RATE)
        val mixBuffer = FloatArray(totalSamples)

        val trackLayerMap = project.trackLayers.associateBy { it.trackType }
        val activeClips = project.clips.filter { clip ->
            !clip.isHidden && (trackLayerMap[clip.trackType]?.isVisible != false)
        }

        // 1. Mix Voiceover & Music tracks
        activeClips.filter { it.trackType == TrackType.VOICEOVER || it.trackType == TrackType.MUSIC }
            .forEach { clip ->
                val clipSamplesCount = ((clip.durationMs * SAMPLE_RATE) / 1000L).toInt().coerceAtLeast(1)
                val sourcePcm = decodeClipAudio(context, clip.audioConfig.audioPath, clipSamplesCount)
                    ?: if (clip.trackType == TrackType.MUSIC) {
                        generateAmbientCinemaPad(clipSamplesCount, clip.audioConfig.waveformSeed)
                    } else {
                        null
                    }

                if (sourcePcm != null && sourcePcm.isNotEmpty()) {
                    val startSample = ((clip.startMs * SAMPLE_RATE) / 1000L).toInt().coerceAtLeast(0)
                    val endSample = min(totalSamples, startSample + clipSamplesCount)
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
                            val srcIdx = (s - startSample) % sourcePcm.size
                            val frac = (s - idx).toFloat() / blockSpan
                            val gain = gainStart + (gainEnd - gainStart) * frac
                            val sampleNorm = sourcePcm[srcIdx] / 32767f
                            mixBuffer[s] += sampleNorm * gain
                        }
                        idx = blockEnd
                    }
                }
            }

        // 2. Mix Locked SFX attached to any clip
        activeClips.forEach { clip ->
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

            while (!outputDone) {
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
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(bufferInfo, 5_000L)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        outputFormat = codec.outputFormat
                    }
                    outIdx >= 0 -> {
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

    private fun decodeClipAudio(context: Context, pathOrUri: String?, maxSamples: Int): ShortArray? {
        if (pathOrUri.isNullOrBlank()) return null
        if (pathOrUri.endsWith(".wav", ignoreCase = true) && !pathOrUri.startsWith("content://")) {
            val wavFile = File(pathOrUri)
            if (wavFile.exists() && wavFile.length() > 44L) {
                return readWavPcm(wavFile, maxSamples)
            }
        }
        return decodeViaMediaExtractor(context, pathOrUri, maxSamples)
    }

    private fun readWavPcm(file: File, maxSamples: Int): ShortArray? {
        return runCatching {
            FileInputStream(file).use { fis ->
                fis.skip(44L)
                val bytes = fis.readBytes()
                val sampleCount = min(bytes.size / 2, maxSamples)
                val bb = ByteBuffer.wrap(bytes, 0, sampleCount * 2).order(ByteOrder.LITTLE_ENDIAN)
                ShortArray(sampleCount) { bb.short }
            }
        }.getOrNull()
    }

    private fun decodeViaMediaExtractor(context: Context, pathOrUri: String, maxSamples: Int): ShortArray? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            if (pathOrUri.startsWith("content://")) {
                extractor.setDataSource(context, Uri.parse(pathOrUri), null)
            } else {
                extractor.setDataSource(pathOrUri)
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
            val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: return null
            val srcRate = if (trackFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) else SAMPLE_RATE
            val srcChannels = if (trackFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 1

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(trackFormat, null, null, 0)
            codec.start()

            val decodedMono = ArrayList<Short>(min(maxSamples, SAMPLE_RATE * 10))
            val info = MediaCodec.BufferInfo()
            var sawInputEOS = false
            var sawOutputEOS = false

            while (!sawOutputEOS && decodedMono.size < maxSamples) {
                if (!sawInputEOS) {
                    val inIdx = codec.dequeueInputBuffer(4_000L)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx) ?: continue
                        val sampleSize = extractor.readSampleData(buf, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEOS = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 4_000L)
                if (outIdx >= 0) {
                    val outBuf = codec.getOutputBuffer(outIdx)
                    if (outBuf != null && info.size > 0) {
                        outBuf.position(info.offset)
                        outBuf.limit(info.offset + info.size)
                        outBuf.order(ByteOrder.LITTLE_ENDIAN)
                        val ch = srcChannels.coerceAtLeast(1)
                        val totalShorts = info.size / 2
                        var s = 0
                        while (s + ch - 1 < totalShorts) {
                            var sum = 0
                            for (c in 0 until ch) {
                                sum += outBuf.short.toInt()
                            }
                            decodedMono.add((sum / ch).toShort())
                            s += ch
                        }
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        sawOutputEOS = true
                    }
                }
            }

            if (decodedMono.isEmpty()) return null
            if (srcRate == SAMPLE_RATE) {
                decodedMono.take(maxSamples).toShortArray()
            } else {
                // Simple linear resample from srcRate to 44100 Hz
                val ratio = srcRate.toDouble() / SAMPLE_RATE.toDouble()
                val outLen = min(maxSamples, (decodedMono.size / ratio).toInt().coerceAtLeast(1))
                ShortArray(outLen) { i ->
                    val srcPos = i * ratio
                    val idx0 = srcPos.toInt().coerceIn(0, decodedMono.lastIndex)
                    val idx1 = (idx0 + 1).coerceIn(0, decodedMono.lastIndex)
                    val frac = (srcPos - idx0).toFloat()
                    (decodedMono[idx0] * (1f - frac) + decodedMono[idx1] * frac).toInt().toShort()
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
        val baseFreq = 110f + (seed % 5) * 14f
        val out = ShortArray(sampleCount)
        for (i in 0 until sampleCount) {
            val t = i.toFloat() / SAMPLE_RATE
            val swell = 0.55f + 0.45f * sin(2.0 * PI * 0.25 * t).toFloat()
            val wave = 0.50f * sin(2.0 * PI * baseFreq * t).toFloat() +
                0.30f * sin(2.0 * PI * (baseFreq * 1.5f) * t).toFloat() +
                0.20f * sin(2.0 * PI * (baseFreq * 2.0f) * t).toFloat()
            out[i] = (wave * swell * 0.22f * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }
}
