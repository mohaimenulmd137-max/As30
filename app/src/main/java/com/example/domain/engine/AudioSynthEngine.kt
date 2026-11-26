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
        val count = (SAMPLE_RATE * (preset.durationMs / 1000f)).toInt().coerceAtLeast(512)
        val out = ShortArray(count)
        var phase = 0.0
        val rand = Random(preset.id.hashCode())

        for (i in 0 until count) {
            val t = i.toFloat() / count.toFloat()
            val freq = preset.freqStartHz + (preset.freqEndHz - preset.freqStartHz) * t
            phase += 2.0 * PI * freq / SAMPLE_RATE

            val envelope = when (preset.waveform) {
                SynthWaveform.SWEEP_UP -> sin(PI * t).toFloat()
                SynthWaveform.SWEEP_DOWN -> exp(-4.5 * t).toFloat()
                else -> (1f - t) * exp(-2.8 * t).toFloat()
            }

            val tone = when (preset.waveform) {
                SynthWaveform.SINE, SynthWaveform.SWEEP_UP, SynthWaveform.SWEEP_DOWN -> sin(phase).toFloat()
                SynthWaveform.TRIANGLE -> (2f / PI.toFloat()) * kotlin.math.asin(sin(phase)).toFloat()
                SynthWaveform.SQUARE -> if (sin(phase) >= 0.0) 0.7f else -0.7f
                SynthWaveform.NOISE_BURST -> (rand.nextFloat() * 2f - 1f)
            }

            val noise = (rand.nextFloat() * 2f - 1f) * preset.noiseMix
            val mixed = ((tone * (1f - preset.noiseMix * 0.5f) + noise) * envelope * volume)
                .coerceIn(-1f, 1f)
            out[i] = (mixed * Short.MAX_VALUE).toInt().toShort()
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
        if (playheadMs < clip.startMs || playheadMs > clip.endMs) return 0f
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
            project.clips.filter { it.trackType == TrackType.VOICEOVER && !it.isHidden }.forEach { vo ->
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

        return gain.coerceIn(0f, 2f)
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
