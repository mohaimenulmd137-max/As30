package com.example.domain.engine.export

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import com.example.domain.engine.AudioSynthEngine
import com.example.domain.engine.MediaProbeHelper
import com.example.domain.model.PresetCatalog
import com.example.domain.model.StudioProject
import com.example.domain.model.TimelineClip
import com.example.domain.model.TrackType
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Constant-memory (~2 MB) streaming audio mixer & AAC hardware/software encoder.
 * Replaces full-duration FloatArray/ShortArray allocations so 2-hour / 5-6 GB exports
 * run smoothly inside a 256 MB heap limit with interleaved A/V muxing.
 */
class StreamingAudioExportSession(
    private val context: Context,
    private val project: StudioProject,
    chunkSamples: Int = 4096,
    private val sampleRate: Int = DEFAULT_SAMPLE_RATE
) {
    companion object {
        const val DEFAULT_SAMPLE_RATE = 44100
        private const val AAC_FRAME_SAMPLES = 1024
    }

    private val totalDurationMs = project.totalDurationMs.coerceIn(1000L, MediaProbeHelper.MAX_TIMELINE_DURATION_MS)
    private val totalSamples: Long = (totalDurationMs * sampleRate) / 1000L
    private val chunkSize = chunkSamples.coerceIn(1024, 8192)
    private val mixFloat = FloatArray(chunkSize)
    private val mixShort = ShortArray(chunkSize)

    private val trackLayerMap = project.trackLayers.associateBy { it.trackType }
    private val activeClips = project.clips.filter { clip ->
        !clip.isHidden && (trackLayerMap[clip.trackType]?.isVisible != false)
    }

    private val anySoloActive = activeClips.any {
        !it.audioConfig.isMuted && it.audioConfig.isSolo &&
            (it.trackType == TrackType.VIDEO || it.trackType == TrackType.VOICEOVER || it.trackType == TrackType.MUSIC)
    }

    private val audioClips = activeClips.filter {
        it.trackType == TrackType.VIDEO || it.trackType == TrackType.VOICEOVER || it.trackType == TrackType.MUSIC
    }.map { ClipStreamHolder(it) }

    private val sfxClips = activeClips.mapNotNull { clip ->
        val sfxId = clip.lockedSfxId ?: return@mapNotNull null
        val preset = PresetCatalog.sfxPresets.firstOrNull { it.id == sfxId } ?: return@mapNotNull null
        SfxStreamHolder(clip, preset)
    }

    private var aacCodec: MediaCodec? = null
    private val bufferInfo = MediaCodec.BufferInfo()
    private var sampleCursor: Long = 0L
    private var inputEosSent = false
    private var outputEosSeen = false
    var audioOutputFormat: MediaFormat? = null
        private set

    // Tiny pre-start packet queue (only holds < 0.2s until MediaMuxer receives video format & starts)
    private val pendingPreStartFrames = ArrayDeque<Pair<ByteArray, MediaCodec.BufferInfo>>(16)

    fun prepare(): Boolean {
        return try {
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }
            val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            aacCodec = codec
            // Prime first small chunk so AAC encoder emits INFO_OUTPUT_FORMAT_CHANGED right away
            feedChunkToEncoder()
            drainAac(muxer = null, audioTrackIndex = -1, muxerStarted = false)
            true
        } catch (_: Throwable) {
            release()
            false
        }
    }

    fun pumpAudioUpToPtsUs(
        targetPtsUs: Long,
        muxer: MediaMuxer?,
        audioTrackIndex: Int,
        muxerStarted: Boolean
    ) {
        val codec = aacCodec ?: return
        if (outputEosSeen) return
        flushPendingFrames(muxer, audioTrackIndex, muxerStarted)

        val targetSample = ((targetPtsUs.coerceAtLeast(0L) * sampleRate) / 1_000_000L).coerceAtMost(totalSamples)
        while (sampleCursor < targetSample && !inputEosSent) {
            feedChunkToEncoder()
            drainAac(muxer, audioTrackIndex, muxerStarted)
        }
        // Drain any trailing output without blocking
        drainAac(muxer, audioTrackIndex, muxerStarted)
    }

    fun finishAndDrain(
        muxer: MediaMuxer?,
        audioTrackIndex: Int,
        muxerStarted: Boolean
    ) {
        val codec = aacCodec ?: return
        flushPendingFrames(muxer, audioTrackIndex, muxerStarted)

        while (sampleCursor < totalSamples && !inputEosSent) {
            feedChunkToEncoder()
            drainAac(muxer, audioTrackIndex, muxerStarted)
        }

        var eosRetries = 0
        while (!inputEosSent && eosRetries < 100) {
            val inIdx = codec.dequeueInputBuffer(5_000L)
            if (inIdx >= 0) {
                val ptsUs = (sampleCursor * 1_000_000L) / sampleRate
                codec.queueInputBuffer(inIdx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                inputEosSent = true
            } else {
                eosRetries++
                drainAac(muxer, audioTrackIndex, muxerStarted)
            }
        }

        var drainRetries = 0
        while (!outputEosSeen && drainRetries < 160) {
            val progressed = drainAac(muxer, audioTrackIndex, muxerStarted, timeoutUs = 5_000L)
            drainRetries = if (progressed) 0 else drainRetries + 1
        }
        flushPendingFrames(muxer, audioTrackIndex, muxerStarted)
    }

    private fun feedChunkToEncoder() {
        val codec = aacCodec ?: return
        if (inputEosSent) return

        val remaining = (totalSamples - sampleCursor).toInt().coerceAtLeast(0)
        if (remaining <= 0) {
            val inIdx = codec.dequeueInputBuffer(4_000L)
            if (inIdx >= 0) {
                val ptsUs = (sampleCursor * 1_000_000L) / sampleRate
                codec.queueInputBuffer(inIdx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                inputEosSent = true
            }
            return
        }

        val count = min(chunkSize, remaining)
        mixWindow(sampleCursor, count)

        var offset = 0
        while (offset < count) {
            val inIdx = codec.dequeueInputBuffer(5_000L)
            if (inIdx < 0) break
            val inBuf = codec.getInputBuffer(inIdx) ?: break
            inBuf.clear()
            inBuf.order(ByteOrder.LITTLE_ENDIAN)
            val maxInBuf = inBuf.remaining() / 2
            val batch = min(min(AAC_FRAME_SAMPLES, count - offset), maxInBuf)
            for (i in 0 until batch) {
                inBuf.putShort(mixShort[offset + i])
            }
            val ptsUs = ((sampleCursor + offset) * 1_000_000L) / sampleRate
            codec.queueInputBuffer(inIdx, 0, batch * 2, ptsUs, 0)
            offset += batch
        }
        sampleCursor += offset
    }

    private fun mixWindow(startSample: Long, count: Int) {
        for (i in 0 until count) mixFloat[i] = 0f

        // 1. Mix video/voiceover/music clips intersecting [startSample, startSample + count)
        audioClips.forEach { holder ->
            holder.mixInto(startSample, count, mixFloat)
        }

        // 2. Mix locked SFX bursts intersecting [startSample, startSample + count)
        sfxClips.forEach { sfx ->
            if (sfx.clip.audioConfig.isMuted) return@forEach
            if (anySoloActive && !sfx.clip.audioConfig.isSolo) return@forEach
            sfx.mixInto(startSample, count, mixFloat)
        }

        // 3. Soft-limit via tanh -> 16-bit PCM
        for (i in 0 until count) {
            val limited = tanh(mixFloat[i].toDouble()).toFloat().coerceIn(-1f, 1f)
            mixShort[i] = (limited * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private var lastMuxedAudioPtsUs: Long = -1L

    private fun nextSafeAudioPtsUs(rawPtsUs: Long): Long {
        val safe = if (rawPtsUs <= lastMuxedAudioPtsUs) lastMuxedAudioPtsUs + 23_219L else rawPtsUs.coerceAtLeast(0L)
        lastMuxedAudioPtsUs = safe
        return safe
    }

    internal fun mixChunkForTest(startSample: Long, count: Int): ShortArray {
        val safeCount = count.coerceIn(1, chunkSize)
        mixWindow(startSample, safeCount)
        return mixShort.copyOf(safeCount)
    }

    private fun drainAac(
        muxer: MediaMuxer?,
        audioTrackIndex: Int,
        muxerStarted: Boolean,
        timeoutUs: Long = 0L
    ): Boolean {
        val codec = aacCodec ?: return false
        var anyProgress = false
        var guard = 0
        while (guard++ < 32) {
            val outIdx = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
            when {
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    audioOutputFormat = codec.outputFormat
                    anyProgress = true
                }
                outIdx >= 0 -> {
                    anyProgress = true
                    val outBuf = codec.getOutputBuffer(outIdx)
                    if (outBuf != null && bufferInfo.size > 0 && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        outBuf.position(bufferInfo.offset)
                        outBuf.limit(bufferInfo.offset + bufferInfo.size)
                        if (muxerStarted && muxer != null && audioTrackIndex >= 0) {
                            bufferInfo.presentationTimeUs = nextSafeAudioPtsUs(bufferInfo.presentationTimeUs)
                            muxer.writeSampleData(audioTrackIndex, outBuf, bufferInfo)
                        } else if (pendingPreStartFrames.size < 48) {
                            val copy = ByteArray(bufferInfo.size)
                            outBuf.get(copy)
                            val infoCopy = MediaCodec.BufferInfo().apply {
                                set(0, copy.size, bufferInfo.presentationTimeUs, bufferInfo.flags)
                            }
                            pendingPreStartFrames.addLast(copy to infoCopy)
                        }
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputEosSeen = true
                        break
                    }
                }
                else -> break
            }
        }
        return anyProgress
    }

    private fun flushPendingFrames(muxer: MediaMuxer?, audioTrackIndex: Int, muxerStarted: Boolean) {
        if (!muxerStarted || muxer == null || audioTrackIndex < 0 || pendingPreStartFrames.isEmpty()) return
        while (pendingPreStartFrames.isNotEmpty()) {
            val (bytes, info) = pendingPreStartFrames.removeFirst()
            info.presentationTimeUs = nextSafeAudioPtsUs(info.presentationTimeUs)
            muxer.writeSampleData(audioTrackIndex, ByteBuffer.wrap(bytes), info)
        }
    }

    fun release() {
        pendingPreStartFrames.clear()
        audioClips.forEach { it.close() }
        sfxClips.forEach { it.release() }
        runCatching { aacCodec?.stop() }
        runCatching { aacCodec?.release() }
        aacCodec = null
    }

    private inner class SfxStreamHolder(
        val clip: TimelineClip,
        private val preset: com.example.domain.model.SfxPreset
    ) {
        private val clipStartSample = (clip.startMs * sampleRate) / 1000L
        private var cachedPcm: ShortArray? = null

        fun mixInto(windowStart: Long, count: Int, dst: FloatArray) {
            val estEnd = clipStartSample + ((preset.durationMs * sampleRate) / 1000L) + 1024L
            val windowEnd = windowStart + count
            if (windowEnd <= clipStartSample || windowStart >= estEnd) {
                if (windowStart >= estEnd) cachedPcm = null
                return
            }
            val pcm = cachedPcm ?: AudioSynthEngine.generateSfxPcm(
                preset = preset,
                volume = (clip.audioConfig.volume * project.masterVolume).coerceIn(0f, 1.5f)
            ).also { cachedPcm = it }

            val startInWindow = maxOf(0L, clipStartSample - windowStart).toInt()
            val endInWindow = minOf(count.toLong(), (clipStartSample + pcm.size) - windowStart).toInt()
            for (i in startInWindow until endInWindow) {
                val srcIdx = (windowStart + i - clipStartSample).toInt()
                if (srcIdx in pcm.indices) {
                    dst[i] += pcm[srcIdx] / 32767f
                }
            }
            if (windowEnd >= clipStartSample + pcm.size) {
                cachedPcm = null
            }
        }

        fun release() {
            cachedPcm = null
        }
    }

    private inner class ClipStreamHolder(private val clip: TimelineClip) {
        private val clipStartSample = (clip.startMs * sampleRate) / 1000L
        private val clipEndSample = clipStartSample + ((clip.durationMs * sampleRate) / 1000L)
        private val speed = clip.playbackSpeed.coerceIn(0.25f, 4.0f)
        private val isLoopingMusic = clip.trackType == TrackType.MUSIC
        private val sourcePath: String? = clip.audioConfig.audioPath
            ?: if (clip.trackType == TrackType.VIDEO && clip.mediaSource?.isVideo == true) clip.mediaSource.uriString else null

        private var reader: IncrementalAudioSourceReader? = null
        private var openAttempted = false
        private var useAmbientFallback = false
        private var ambientPhase1 = 0.0
        private var ambientPhase2 = 0.0
        private var ambientPhase3 = 0.0
        private var ambientPhase4 = 0.0
        private var lastAmbientSampleIdx = -1L

        fun mixInto(windowStart: Long, count: Int, dst: FloatArray) {
            val windowEnd = windowStart + count
            if (windowEnd <= clipStartSample || windowStart >= clipEndSample) {
                if (windowStart >= clipEndSample) close()
                return
            }

            if (!openAttempted) {
                openAttempted = true
                if (!sourcePath.isNullOrBlank()) {
                    reader = IncrementalAudioSourceReader(
                        context = context,
                        pathOrUri = sourcePath,
                        trimStartMs = clip.trimStartMs.coerceAtLeast(0L),
                        targetSampleRate = sampleRate,
                        loopOnEos = isLoopingMusic
                    ).takeIf { it.open() }
                }
                if (reader == null && isLoopingMusic) {
                    useAmbientFallback = true
                }
            }

            val startInWindow = maxOf(0L, clipStartSample - windowStart).toInt()
            val endInWindow = minOf(count.toLong(), clipEndSample - windowStart).toInt()
            if (startInWindow >= endInWindow) return

            val activeReader = reader
            if (activeReader == null && !useAmbientFallback) return

            val tStartMs = ((windowStart + startInWindow) * 1000L) / sampleRate
            val tEndMs = ((windowStart + endInWindow) * 1000L) / sampleRate
            val gainStart = AudioSynthEngine.computeEffectiveAudioGain(clip, project, tStartMs)
            val gainEnd = AudioSynthEngine.computeEffectiveAudioGain(clip, project, tEndMs)
            val span = (endInWindow - startInWindow).coerceAtLeast(1)

            if (activeReader != null) {
                for (i in startInWindow until endInWindow) {
                    val absSample = windowStart + i
                    val relTimeline = absSample - clipStartSample
                    val neededSrcIdx = (relTimeline * speed).toLong()
                    val sampleShort = activeReader.readSampleAt(neededSrcIdx)
                    val frac = (i - startInWindow).toFloat() / span
                    val gain = gainStart + (gainEnd - gainStart) * frac
                    dst[i] += (sampleShort / 32767f) * gain
                }
            } else if (useAmbientFallback) {
                val baseFreq = 98f + (clip.audioConfig.waveformSeed % 5) * 12f
                for (i in startInWindow until endInWindow) {
                    val absSample = windowStart + i
                    val s = generateAmbientPadSample(absSample, baseFreq)
                    val frac = (i - startInWindow).toFloat() / span
                    val gain = gainStart + (gainEnd - gainStart) * frac
                    dst[i] += s * gain
                }
            }

            if (windowEnd >= clipEndSample) {
                close()
            }
        }

        private fun generateAmbientPadSample(sampleIdx: Long, baseFreq: Float): Float {
            if (lastAmbientSampleIdx != sampleIdx - 1L) {
                val t = sampleIdx.toDouble() / sampleRate
                ambientPhase1 = (2.0 * PI * baseFreq * t) % (2.0 * PI)
                ambientPhase2 = (2.0 * PI * (baseFreq * 1.189) * t) % (2.0 * PI)
                ambientPhase3 = (2.0 * PI * (baseFreq * 1.498) * t) % (2.0 * PI)
                ambientPhase4 = (2.0 * PI * (baseFreq * 2.245) * t) % (2.0 * PI)
            } else {
                ambientPhase1 += 2.0 * PI * baseFreq / sampleRate
                ambientPhase2 += 2.0 * PI * (baseFreq * 1.189) / sampleRate
                ambientPhase3 += 2.0 * PI * (baseFreq * 1.498) / sampleRate
                ambientPhase4 += 2.0 * PI * (baseFreq * 2.245) / sampleRate
            }
            lastAmbientSampleIdx = sampleIdx
            val t = sampleIdx.toDouble() / sampleRate
            val swell = 0.55 + 0.45 * sin(2.0 * PI * 0.16 * t)
            val wave = 0.45 * sin(ambientPhase1) + 0.22 * sin(ambientPhase2) + 0.24 * sin(ambientPhase3) + 0.12 * sin(ambientPhase4)
            return (tanh(wave * swell * 1.15) * 0.24).toFloat()
        }

        fun close() {
            reader?.close()
            reader = null
        }
    }
}

/**
 * Decodes audio incrementally in ~1s sliding windows (~88 KB) instead of buffering an entire 2-hour track.
 */
private class IncrementalAudioSourceReader(
    private val context: Context,
    private val pathOrUri: String,
    private val trimStartMs: Long,
    private val targetSampleRate: Int,
    private val loopOnEos: Boolean
) {
    private var wavStream: FileInputStream? = null
    private var extractor: MediaExtractor? = null
    private var decoder: MediaCodec? = null
    private val info = MediaCodec.BufferInfo()
    private var srcSampleRate = targetSampleRate
    private var srcChannels = 1
    private var sawInputEos = false
    private var sawOutputEos = false
    private var sourceExhausted = false

    private val ringCapacity = targetSampleRate * 2 // ~176 KB sliding window (2 seconds max in RAM)
    private val ring = ShortArray(ringCapacity)
    private var ringStartSample: Long = 0L
    private var ringCount: Int = 0
    private var totalSourceRead: Long = 0L

    fun open(): Boolean {
        if (pathOrUri.endsWith(".wav", ignoreCase = true) && !pathOrUri.startsWith("content://")) {
            val f = File(pathOrUri)
            if (f.exists() && f.length() > 44L) {
                return runCatching {
                    val fis = FileInputStream(f)
                    val skipSamples = (trimStartMs * targetSampleRate) / 1000L
                    fis.skip(44L + skipSamples * 2L)
                    wavStream = fis
                    true
                }.getOrDefault(false)
            }
        }
        return openMediaCodecExtractor()
    }

    private fun openMediaCodecExtractor(): Boolean {
        return try {
            val ext = MediaExtractor()
            if (!MediaProbeHelper.setExtractorDataSource(context, ext, pathOrUri)) {
                ext.release()
                return false
            }
            var trackIndex = -1
            var trackFormat: MediaFormat? = null
            for (i in 0 until ext.trackCount) {
                val fmt = ext.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    trackFormat = fmt
                    break
                }
            }
            if (trackIndex < 0 || trackFormat == null) {
                ext.release()
                return false
            }
            ext.selectTrack(trackIndex)
            val trimUs = trimStartMs * 1000L
            if (trimUs > 0L) ext.seekTo(trimUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: run { ext.release(); return false }
            srcSampleRate = if (trackFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(8000) else targetSampleRate
            srcChannels = if (trackFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1) else 1

            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(trackFormat, null, null, 0)
            codec.start()

            extractor = ext
            decoder = codec
            sawInputEos = false
            sawOutputEos = false
            sourceExhausted = false
            true
        } catch (_: Throwable) {
            close()
            false
        }
    }

    fun readSampleAt(sampleIndex: Long): Short {
        if (sampleIndex < ringStartSample || sourceExhausted) return 0
        while (sampleIndex >= ringStartSample + ringCount) {
            val refilled = decodeMoreIntoRing()
            if (!refilled) {
                if (loopOnEos && restartSourceForLoop()) continue
                sourceExhausted = true
                return 0
            }
        }
        val offset = (sampleIndex - ringStartSample).toInt()
        return if (offset in 0 until ringCount) ring[offset] else 0
    }

    private fun decodeMoreIntoRing(): Boolean {
        // Shift old consumed samples out when ring is over half full
        if (ringCount > ringCapacity / 2) {
            val drop = ringCount / 2
            System.arraycopy(ring, drop, ring, 0, ringCount - drop)
            ringStartSample += drop
            ringCount -= drop
        }

        wavStream?.let { fis ->
            val maxReadSamples = min(4096, ringCapacity - ringCount)
            if (maxReadSamples <= 0) return true
            val buf = ByteArray(maxReadSamples * 2)
            val readBytes = runCatching { fis.read(buf) }.getOrDefault(-1)
            if (readBytes <= 1) return false
            val samplesRead = readBytes / 2
            val bb = ByteBuffer.wrap(buf, 0, samplesRead * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until samplesRead) {
                ring[ringCount++] = bb.short
            }
            return true
        }

        val codec = decoder ?: return false
        val ext = extractor ?: return false
        if (sawOutputEos) return false

        val trimUs = trimStartMs * 1000L
        var loops = 0
        val beforeCount = ringCount
        while (ringCount < ringCapacity - 4096 && !sawOutputEos && loops++ < 64) {
            if (!sawInputEos) {
                val inIdx = codec.dequeueInputBuffer(3_000L)
                if (inIdx >= 0) {
                    val inBuf = codec.getInputBuffer(inIdx)
                    if (inBuf != null) {
                        val size = ext.readSampleData(inBuf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEos = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, size, ext.sampleTime, 0)
                            ext.advance()
                        }
                    }
                }
            }

            val outIdx = codec.dequeueOutputBuffer(info, 3_000L)
            if (outIdx >= 0) {
                val outBuf = codec.getOutputBuffer(outIdx)
                if (outBuf != null && info.size > 0) {
                    outBuf.position(info.offset)
                    outBuf.limit(info.offset + info.size)
                    outBuf.order(ByteOrder.LITTLE_ENDIAN)
                    val ch = srcChannels
                    val totalShorts = info.size / 2
                    val framesInChunk = totalShorts / ch
                    val chunkEndUs = info.presentationTimeUs + ((framesInChunk * 1_000_000L) / srcSampleRate)
                    if (framesInChunk > 0 && (totalSourceRead > 0L || trimUs <= 0L || chunkEndUs >= trimUs)) {
                        val skipFrames = if (totalSourceRead == 0L && trimUs > info.presentationTimeUs) {
                            (((trimUs - info.presentationTimeUs) * srcSampleRate) / 1_000_000L)
                                .toInt()
                                .coerceIn(0, framesInChunk)
                        } else 0
                        val ratio = targetSampleRate.toDouble() / srcSampleRate.toDouble()
                        var acc = 0.0
                        for (f in 0 until framesInChunk) {
                            var sum = 0
                            for (c in 0 until ch) sum += outBuf.short.toInt()
                            if (f >= skipFrames) {
                                totalSourceRead++
                                val avg = (sum / ch).toShort()
                                acc += ratio
                                while (acc >= 1.0 && ringCount < ringCapacity) {
                                    ring[ringCount++] = avg
                                    acc -= 1.0
                                }
                            }
                        }
                    }
                }
                codec.releaseOutputBuffer(outIdx, false)
                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    sawOutputEos = true
                }
            } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val fmt = codec.outputFormat
                if (fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                    srcSampleRate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(8000)
                }
                if (fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                    srcChannels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                }
            }
        }
        return ringCount > beforeCount
    }

    private fun restartSourceForLoop(): Boolean {
        wavStream?.let { old ->
            runCatching { old.close() }
            wavStream = null
            totalSourceRead = 0L
            return open()
        }
        val ext = extractor ?: return false
        val codec = decoder ?: return false
        return runCatching {
            ext.seekTo(trimStartMs * 1000L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            codec.flush()
            sawInputEos = false
            sawOutputEos = false
            totalSourceRead = 0L
            true
        }.getOrDefault(false)
    }

    fun close() {
        runCatching { wavStream?.close() }
        wavStream = null
        runCatching { decoder?.stop() }
        runCatching { decoder?.release() }
        decoder = null
        runCatching { extractor?.release() }
        extractor = null
    }
}
