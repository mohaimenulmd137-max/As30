package com.example.domain.engine

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.MediaRecorder
import com.example.domain.model.SfxPreset
import com.example.domain.model.StudioProject
import com.example.domain.model.SynthWaveform
import com.example.domain.model.TimelineClip
import com.example.domain.model.TrackType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

object AudioSynthEngine {

    private const val SAMPLE_RATE = 44100

    /**
     * Synthesizes real PCM audio for any of the 52 SFX presets and plays it asynchronously.
     */
    fun previewSfx(preset: SfxPreset, volume: Float = 0.85f) {
        CoroutineScope(Dispatchers.Default).launch {
            val samples = generateSfxPcm(preset, volume.coerceIn(0f, 1.5f))
            val minBuf = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(samples.size * 2)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            try {
                track.write(samples, 0, samples.size)
                track.play()
                kotlinx.coroutines.delay(preset.durationMs.toLong() + 120L)
            } catch (_: Throwable) {
            } finally {
                runCatching { track.stop() }
                runCatching { track.release() }
            }
        }
    }

    fun generateSfxPcm(preset: SfxPreset, volume: Float): ShortArray {
        val count = (SAMPLE_RATE * (preset.durationMs / 1000f)).toInt().coerceAtLeast(1024)
        val raw = DoubleArray(count)
        val rand = Random(preset.id.hashCode())

        // Pink noise filter state (Paul Kellet 3-pole approximation)
        var p0 = 0.0
        var p1 = 0.0
        var p2 = 0.0
        var brown = 0.0

        // Biquad bandpass/lowpass filter states
        var bpX1 = 0.0; var bpX2 = 0.0; var bpY1 = 0.0; var bpY2 = 0.0
        var lpY1 = 0.0
        var phase1 = 0.0
        var phase2 = 0.0
        var phase3 = 0.0

        val fadeInSamples = (SAMPLE_RATE * 0.005).toInt().coerceAtMost(count / 6).coerceAtLeast(1)
        val fadeOutSamples = (SAMPLE_RATE * 0.014).toInt().coerceAtMost(count / 4).coerceAtLeast(1)

        for (i in 0 until count) {
            val t = i.toDouble() / count.toDouble()
            val sec = i.toDouble() / SAMPLE_RATE

            val white = rand.nextDouble() * 2.0 - 1.0
            p0 = 0.99765 * p0 + white * 0.0990460
            p1 = 0.96300 * p1 + white * 0.2965164
            p2 = 0.57000 * p2 + white * 1.0526913
            val pink = ((p0 + p1 + p2 + white * 0.1848) * 0.22).coerceIn(-1.0, 1.0)
            brown = (brown * 0.985 + white * 0.08).coerceIn(-1.0, 1.0)

            val sample = when (preset.category) {
                com.example.domain.model.SfxCategory.WHOOSH -> {
                    // Organic air-whoosh: bell-shaped sweep across dual resonant bandpass pink noise + breath sub
                    val arch = sin(PI * t)
                    val sweepCurve = arch * arch
                    val centerHz = (preset.freqStartHz + (preset.freqEndHz - preset.freqStartHz) * t)
                        .toDouble()
                        .coerceIn(140.0, 6200.0)
                    val omega = 2.0 * PI * centerHz / SAMPLE_RATE
                    val q = 2.2
                    val alpha = sin(omega) / (2.0 * q)
                    val cosW = cos(omega)
                    val a0 = 1.0 + alpha
                    val bp = ((alpha / a0) * pink - (alpha / a0) * bpX2 - ((-2.0 * cosW) / a0) * bpY1 - ((1.0 - alpha) / a0) * bpY2)
                        .coerceIn(-2.0, 2.0)
                    bpX2 = bpX1; bpX1 = pink; bpY2 = bpY1; bpY1 = bp

                    phase1 += 2.0 * PI * (centerHz * 0.28) / SAMPLE_RATE
                    val airCore = sin(phase1) * 0.14 * sweepCurve
                    (bp * 1.45 + pink * 0.28 + brown * 0.35 + airCore) * sweepCurve
                }

                com.example.domain.model.SfxCategory.IMPACT -> {
                    // Cinema trailer impact: exponential pitch-drop sub + woody punch transient + warm lowpass tail
                    val subFreq = (preset.freqEndHz + (preset.freqStartHz - preset.freqEndHz) * exp(-14.0 * t))
                        .toDouble()
                        .coerceIn(28.0, 240.0)
                    phase1 += 2.0 * PI * subFreq / SAMPLE_RATE
                    phase2 += 2.0 * PI * (subFreq * 2.01) / SAMPLE_RATE
                    val subEnv = exp(-4.2 * t)
                    val subBody = (sin(phase1) * 0.85 + sin(phase2) * 0.22) * subEnv

                    val punchEnv = exp(-36.0 * t)
                    val lpAlpha = (0.04 + 0.32 * exp(-18.0 * t)).coerceIn(0.02, 0.5)
                    lpY1 += lpAlpha * ((pink * 0.7 + brown * 0.5) - lpY1)
                    val rumble = lpY1 * exp(-2.6 * t) * 0.75
                    val clickTrans = (pink * 0.45 + sin(phase2) * 0.45) * punchEnv
                    subBody * 0.8 + rumble + clickTrans
                }

                com.example.domain.model.SfxCategory.TYPING -> {
                    // Tactile damped mechanical key / typewriter impulse (zero square wave)
                    val hitEnv = exp(-38.0 * t)
                    val bodyEnv = exp(-19.0 * t)
                    val baseHz = preset.freqStartHz.toDouble().coerceIn(450.0, 2600.0)
                    phase1 += 2.0 * PI * baseHz / SAMPLE_RATE
                    phase2 += 2.0 * PI * (baseHz * 2.38) / SAMPLE_RATE
                    // Sharp initial woody transient passed through smoothing lowpass
                    lpY1 += 0.38 * (white - lpY1)
                    val crispClick = lpY1 * hitEnv * 0.75
                    val woodResonance = (sin(phase1) * 0.55 + sin(phase2) * 0.25) * bodyEnv
                    (crispClick + woodResonance) * 0.85
                }

                com.example.domain.model.SfxCategory.POP -> {
                    // Crisp liquid bubble pop & crystal FM UI chimes
                    val isChime = preset.durationMs >= 170
                    if (isChime) {
                        val env = (1.0 - exp(-90.0 * sec)) * exp(-5.2 * t)
                        val f0 = preset.freqStartHz.toDouble().coerceIn(280.0, 2400.0)
                        phase1 += 2.0 * PI * f0 / SAMPLE_RATE
                        phase2 += 2.0 * PI * (f0 * 2.0) / SAMPLE_RATE
                        phase3 += 2.0 * PI * (f0 * 3.01) / SAMPLE_RATE
                        val fm = sin(phase2) * exp(-10.0 * t) * 0.8
                        (sin(phase1 + fm) * 0.72 + sin(phase2) * 0.24 * exp(-7.0 * t) + sin(phase3) * 0.12 * exp(-11.0 * t)) * env
                    } else {
                        val fSweep = preset.freqStartHz + (preset.freqEndHz - preset.freqStartHz) * (1.0 - exp(-18.0 * t))
                        phase1 += 2.0 * PI * fSweep / SAMPLE_RATE
                        val env = sin(PI * t) * exp(-4.5 * t)
                        (sin(phase1) * 0.9 + pink * 0.08) * env
                    }
                }

                com.example.domain.model.SfxCategory.CINEMA -> {
                    // Lush cinematic fifth+octave pad swell / horizon sting
                    val arch = (sin(PI * t)).coerceAtLeast(0.0)
                    val swell = arch * arch * 0.85 + 0.15 * exp(-2.2 * t)
                    val fRoot = (preset.freqStartHz + (preset.freqEndHz - preset.freqStartHz) * t * 0.45)
                        .toDouble().coerceIn(80.0, 900.0)
                    phase1 += 2.0 * PI * fRoot / SAMPLE_RATE
                    phase2 += 2.0 * PI * (fRoot * 1.498) / SAMPLE_RATE
                    phase3 += 2.0 * PI * (fRoot * 2.004) / SAMPLE_RATE
                    lpY1 += 0.12 * (pink - lpY1)
                    val chord = sin(phase1) * 0.48 + sin(phase2) * 0.30 + sin(phase3) * 0.22
                    (chord + lpY1 * 0.18) * swell
                }

                com.example.domain.model.SfxCategory.GLITCH -> {
                    // Granular stepped cyber shards + resonant bandpass texture
                    val stepSlot = (t * 14.0).toInt()
                    val stepFrac = (t * 14.0) - stepSlot
                    val grainGate = sin(PI * stepFrac)
                    val grainFreq = (preset.freqStartHz * (0.65 + 0.75 * ((stepSlot * 37 % 11) / 10.0)))
                        .coerceIn(180.0, 3800.0)
                    phase1 += 2.0 * PI * grainFreq / SAMPLE_RATE
                    phase2 += 2.0 * PI * (grainFreq * 1.414) / SAMPLE_RATE
                    val fmShard = sin(phase1 + sin(phase2) * 2.2) * grainGate
                    val tex = (pink * 0.45 + fmShard * 0.65) * exp(-2.4 * t)
                    tex
                }
            }

            // Soft analog saturation + anti-click fade envelope
            val antiClick = when {
                i < fadeInSamples -> i.toDouble() / fadeInSamples
                i >= count - fadeOutSamples -> (count - i).toDouble() / fadeOutSamples
                else -> 1.0
            }.coerceIn(0.0, 1.0)
            val warm = kotlin.math.tanh(sample * 1.35 * volume.toDouble().coerceIn(0.0, 1.6))
            raw[i] = warm * antiClick
        }

        // Remove any residual DC offset cleanly
        val mean = raw.average()
        val out = ShortArray(count)
        for (i in 0 until count) {
            val centered = (raw[i] - mean).coerceIn(-0.98, 0.98)
            out[i] = (centered * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    /**
     * Computes real-time effective Music gain considering Auto-Ducking when any Voiceover is active,
     * plus Fade-In and Fade-Out envelopes.
     */
    fun computeEffectiveAudioGain(
        clip: TimelineClip,
        project: StudioProject,
        playheadMs: Long
    ): Float {
        if (clip.isHidden || clip.audioConfig.isMuted) return 0f
        if (playheadMs < clip.startMs || playheadMs > clip.endMs) return 0f

        val anySoloActive = project.clips.any {
            !it.isHidden && !it.audioConfig.isMuted && it.audioConfig.isSolo &&
                (it.trackType == TrackType.VIDEO || it.trackType == TrackType.VOICEOVER || it.trackType == TrackType.MUSIC)
        }
        if (anySoloActive && !clip.audioConfig.isSolo) return 0f

        val localMs = playheadMs - clip.startMs
        val remMs = clip.endMs - playheadMs

        var gain = clip.audioConfig.volume * project.masterVolume

        // Fade In
        if (clip.audioConfig.fadeInMs > 0 && localMs < clip.audioConfig.fadeInMs) {
            gain *= (localMs.toFloat() / clip.audioConfig.fadeInMs.toFloat()).coerceIn(0f, 1f)
        }
        // Fade Out
        if (clip.audioConfig.fadeOutMs > 0 && remMs < clip.audioConfig.fadeOutMs) {
            gain *= (remMs.toFloat() / clip.audioConfig.fadeOutMs.toFloat()).coerceIn(0f, 1f)
        }

        // Smooth Attack/Release Auto-Ducking: Music smoothly lowers during Voiceover and swells back up
        if (clip.trackType == TrackType.MUSIC && clip.audioConfig.autoDuckingEnabled) {
            val floor = clip.audioConfig.duckingFloor.coerceIn(0.05f, 0.9f)
            val attackMs = 250L
            val releaseMs = 350L
            var minDuckFactor = 1f
            project.clips.filter { it.trackType == TrackType.VOICEOVER && !it.isHidden && !it.audioConfig.isMuted }.forEach { vo ->
                val factor = when {
                    playheadMs in vo.startMs..vo.endMs -> floor
                    playheadMs in (vo.startMs - attackMs) until vo.startMs -> {
                        val t = (playheadMs - (vo.startMs - attackMs)).toFloat() / attackMs
                        1f - (1f - floor) * t
                    }
                    playheadMs in (vo.endMs + 1L)..(vo.endMs + releaseMs) -> {
                        val t = (playheadMs - vo.endMs).toFloat() / releaseMs
                        floor + (1f - floor) * t
                    }
                    else -> 1f
                }
                if (factor < minDuckFactor) minDuckFactor = factor
            }
            gain *= minDuckFactor
        }

        // Noise reduction clarity boost for Voiceover
        if (clip.trackType == TrackType.VOICEOVER && clip.audioConfig.noiseReductionEnabled) {
            gain *= 1.08f
        }

        return gain.coerceIn(0f, 2.2f)
    }

    fun playRecordedFile(path: String) {
        runCatching {
            val player = MediaPlayer().apply {
                setDataSource(path)
                prepare()
                start()
                setOnCompletionListener { it.release() }
            }
            player
        }
    }
}

class VoiceoverRecorder(private val context: Context) {

    private var recordJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var isRecordingFlag = false

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _liveAmplitude = MutableStateFlow(0f)
    val liveAmplitude: StateFlow<Float> = _liveAmplitude.asStateFlow()

    private var outputFile: File? = null

    @SuppressLint("MissingPermission")
    fun startRecording(onComplete: (filePath: String, durationMs: Long) -> Unit) {
        if (isRecordingFlag) return
        val dir = File(context.filesDir, "voiceovers").apply { mkdirs() }
        val file = File(dir, "vo_${System.currentTimeMillis()}.wav")
        outputFile = file

        val sampleRate = 44100
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return
        }

        audioRecord = recorder
        isRecordingFlag = true
        _isRecording.value = true
        val startedAt = System.currentTimeMillis()

        recordJob = CoroutineScope(Dispatchers.IO).launch {
            val pcmBuffer = ShortArray(bufferSize / 2)
            FileOutputStream(file).use { fos ->
                // Write placeholder 44-byte WAV header
                fos.write(ByteArray(44))
                var totalAudioBytes = 0L
                recorder.startRecording()

                while (isActive && isRecordingFlag) {
                    val read = recorder.read(pcmBuffer, 0, pcmBuffer.size)
                    if (read > 0) {
                        var peak = 0
                        val byteBuf = ByteArray(read * 2)
                        for (i in 0 until read) {
                            val s = pcmBuffer[i]
                            val a = abs(s.toInt())
                            if (a > peak) peak = a
                            byteBuf[i * 2] = (s.toInt() and 0xFF).toByte()
                            byteBuf[i * 2 + 1] = ((s.toInt() shr 8) and 0xFF).toByte()
                        }
                        _liveAmplitude.value = (peak / 32767f).coerceIn(0f, 1f)
                        fos.write(byteBuf)
                        totalAudioBytes += byteBuf.size
                    }
                }
                runCatching { recorder.stop() }
                runCatching { recorder.release() }
                writeWavHeader(file, totalAudioBytes, sampleRate)
            }

            val durMs = (System.currentTimeMillis() - startedAt).coerceAtLeast(1000L)
            _liveAmplitude.value = 0f
            _isRecording.value = false
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                onComplete(file.absolutePath, durMs)
            }
        }
    }

    fun stopRecording() {
        isRecordingFlag = false
    }

    private fun writeWavHeader(file: File, totalAudioLen: Long, sampleRate: Int) {
        val totalDataLen = totalAudioLen + 36
        val byteRate = sampleRate * 2L
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(0)
            raf.writeBytes("RIFF")
            raf.writeInt(Integer.reverseBytes(totalDataLen.toInt()))
            raf.writeBytes("WAVEfmt ")
            raf.writeInt(Integer.reverseBytes(16))
            raf.writeShort(java.lang.Short.reverseBytes(1.toShort()).toInt())
            raf.writeShort(java.lang.Short.reverseBytes(1.toShort()).toInt())
            raf.writeInt(Integer.reverseBytes(sampleRate))
            raf.writeInt(Integer.reverseBytes(byteRate.toInt()))
            raf.writeShort(java.lang.Short.reverseBytes(2.toShort()).toInt())
            raf.writeShort(java.lang.Short.reverseBytes(16.toShort()).toInt())
            raf.writeBytes("data")
            raf.writeInt(Integer.reverseBytes(totalAudioLen.toInt()))
        }
    }
}
