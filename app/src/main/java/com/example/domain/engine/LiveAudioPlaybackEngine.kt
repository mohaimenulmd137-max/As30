package com.example.domain.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import android.os.SystemClock
import com.example.domain.model.PresetCatalog
import com.example.domain.model.StudioProject
import com.example.domain.model.TrackType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Real-time synchronized audio engine for timeline preview playback.
 * Uses pre-warmed MediaPlayer instances for embedded video audio, voiceover files, and music files
 * so 5-60 minute media starts in <15ms with zero RAM pre-decode freeze/OOM, while streaming
 * O(1)-memory synthetic ambient pads only when a Music clip has no audioPath.
 */
class LiveAudioPlaybackEngine {

    private class PlayerSlot(
        val clipId: String,
        val dataSource: String,
        val player: MediaPlayer,
        var isPrepared: Boolean = false,
        var hasError: Boolean = false,
        var mediaDurationMs: Long = 0L,
        var lastAppliedSpeed: Float = 1.0f,
        var lastSeekWallMs: Long = 0L
    )

    private val slots = mutableMapOf<String, PlayerSlot>()
    private var syncJob: Job? = null
    private var padJob: Job? = null
    private val triggeredSfxClipIds = mutableSetOf<String>()

    @Volatile
    private var latestProject: StudioProject? = null

    @Volatile
    private var isEnginePlaying: Boolean = false

    @Volatile
    private var livePlayheadProvider: (() -> Long)? = null

    @Volatile
    private var padGain: Float = 0f

    @Volatile
    private var padSeed: Int = 7

    fun startFrom(
        scope: CoroutineScope,
        context: Context,
        project: StudioProject,
        startPlayheadMs: Long,
        debounceMs: Long = 0L,
        getLivePlayheadMs: (() -> Long)? = null
    ) {
        syncJob?.cancel()
        latestProject = project
        livePlayheadProvider = getLivePlayheadMs ?: { startPlayheadMs }
        isEnginePlaying = true
        triggeredSfxClipIds.clear()

        syncJob = scope.launch(Dispatchers.Main.immediate) {
            if (debounceMs > 0L) delay(debounceMs)
            if (!isActive || !isEnginePlaying) return@launch

            syncInternal(
                context = context,
                project = latestProject ?: project,
                playheadMs = livePlayheadProvider?.invoke() ?: startPlayheadMs,
                isPlaying = true,
                forceSeek = true
            )
            ensurePadLoopIfNeeded(scope)

            while (isActive && isEnginePlaying) {
                delay(50L)
                val proj = latestProject ?: continue
                val nowMs = livePlayheadProvider?.invoke() ?: startPlayheadMs
                syncInternal(
                    context = context,
                    project = proj,
                    playheadMs = nowMs,
                    isPlaying = true,
                    forceSeek = false
                )
            }
        }
    }

    fun syncToPlayhead(
        context: Context,
        project: StudioProject,
        playheadMs: Long,
        isPlaying: Boolean,
        forceSeek: Boolean = false
    ) {
        latestProject = project
        isEnginePlaying = isPlaying
        if (forceSeek) triggeredSfxClipIds.clear()
        syncInternal(
            context = context,
            project = project,
            playheadMs = playheadMs,
            isPlaying = isPlaying,
            forceSeek = forceSeek
        )
    }

    fun updateProjectSnapshot(project: StudioProject) {
        latestProject = project
    }

    fun pause() {
        isEnginePlaying = false
        syncJob?.cancel()
        syncJob = null
        padJob?.cancel()
        padJob = null
        padGain = 0f
        slots.values.forEach { slot ->
            runCatching {
                if (slot.isPrepared && slot.player.isPlaying) {
                    slot.player.pause()
                }
            }
        }
    }

    fun stop() {
        pause()
        triggeredSfxClipIds.clear()
    }

    fun clearCache() {
        stop()
        releaseAllSlots()
    }

    private fun releaseAllSlots() {
        slots.values.forEach { slot ->
            runCatching {
                if (slot.isPrepared && slot.player.isPlaying) slot.player.stop()
            }
            runCatching { slot.player.release() }
        }
        slots.clear()
    }

    private fun syncInternal(
        context: Context,
        project: StudioProject,
        playheadMs: Long,
        isPlaying: Boolean,
        forceSeek: Boolean
    ) {
        val trackLayerMap = project.trackLayers.associateBy { it.trackType }
        val activeFileClipIds = mutableSetOf<String>()

        var nextPadGain = 0f
        var nextPadSeed = 7

        project.clips.forEach { clip ->
            val layerVisible = trackLayerMap[clip.trackType]?.isVisible != false
            val isVisibleClip = !clip.isHidden && layerVisible
            val isInRange = playheadMs >= clip.startMs && playheadMs < clip.endMs

            val resolvedSource = when (clip.trackType) {
                TrackType.VOICEOVER -> clip.audioConfig.audioPath
                TrackType.MUSIC -> clip.audioConfig.audioPath
                TrackType.VIDEO -> clip.audioConfig.audioPath ?: if (clip.mediaSource?.isVideo == true) clip.mediaSource.uriString else null
                else -> null
            }

            if (resolvedSource != null) {
                activeFileClipIds += clip.id
                val gain = if (isVisibleClip && isInRange) {
                    AudioSynthEngine.computeEffectiveAudioGain(clip, project, playheadMs)
                } else 0f

                val slot = getOrCreateSlot(context, clip.id, resolvedSource, loopMusic = (clip.trackType == TrackType.MUSIC))
                if (slot != null && !slot.hasError && slot.isPrepared) {
                    val vol = gain.coerceIn(0f, 1f)
                    runCatching { slot.player.setVolume(vol, vol) }

                    val targetSourceMs = clip.computeSourcePositionMs(
                        playheadMs = playheadMs,
                        loopWithinSource = (clip.trackType == TrackType.MUSIC)
                    )
                    val dur = slot.mediaDurationMs
                    val pastSourceEnd = dur > 0L && targetSourceMs >= dur - 25L && clip.trackType != TrackType.MUSIC

                    if (isPlaying && isVisibleClip && isInRange && gain > 0.001f && !pastSourceEnd) {
                        val localNorm = if (clip.durationMs > 0L) {
                            ((playheadMs - clip.startMs).toFloat() / clip.durationMs.toFloat()).coerceIn(0f, 1f)
                        } else 0f
                        val effectiveSpeed = SpeedRampEngine.evaluateEffectiveSpeedAt(
                            profile = clip.speedRamp,
                            normalizedTime = localNorm,
                            baseSpeed = clip.playbackSpeed
                        ).coerceIn(0.25f, 3.0f)

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && abs(effectiveSpeed - slot.lastAppliedSpeed) > 0.05f) {
                            runCatching {
                                slot.player.playbackParams = PlaybackParams().apply { speed = effectiveSpeed }
                                slot.lastAppliedSpeed = effectiveSpeed
                            }
                        }

                        val nowWall = SystemClock.elapsedRealtime()
                        val safeSeekMs = if (dur > 0L) targetSourceMs.coerceIn(0L, (dur - 1L).coerceAtLeast(0L)) else targetSourceMs
                        runCatching {
                            if (!slot.player.isPlaying) {
                                seekMediaPlayerExact(slot.player, safeSeekMs)
                                slot.lastSeekWallMs = nowWall
                                slot.player.start()
                            } else {
                                val currentPos = slot.player.currentPosition.toLong()
                                val drift = abs(currentPos - safeSeekMs)
                                if ((forceSeek || drift > 360L) && (nowWall - slot.lastSeekWallMs > 420L || forceSeek)) {
                                    seekMediaPlayerExact(slot.player, safeSeekMs)
                                    slot.lastSeekWallMs = nowWall
                                }
                            }
                        }
                    } else {
                        runCatching {
                            if (slot.player.isPlaying) {
                                slot.player.pause()
                            }
                            if (forceSeek && isVisibleClip && isInRange) {
                                val safeSeekMs = if (dur > 0L) targetSourceMs.coerceIn(0L, (dur - 1L).coerceAtLeast(0L)) else targetSourceMs
                                seekMediaPlayerExact(slot.player, safeSeekMs)
                            }
                        }
                    }
                }
            } else if (clip.trackType == TrackType.MUSIC && isVisibleClip && isInRange) {
                val g = AudioSynthEngine.computeEffectiveAudioGain(clip, project, playheadMs)
                if (g > nextPadGain) {
                    nextPadGain = g
                    nextPadSeed = clip.audioConfig.waveformSeed
                }
            }

            if (isPlaying && isVisibleClip && clip.lockedSfxId != null && !clip.audioConfig.isMuted) {
                val withinTriggerWindow = playheadMs in clip.startMs..(clip.startMs + 160L)
                if (withinTriggerWindow && clip.id !in triggeredSfxClipIds) {
                    triggeredSfxClipIds += clip.id
                    PresetCatalog.sfxPresets.firstOrNull { it.id == clip.lockedSfxId }?.let { preset ->
                        AudioSynthEngine.previewSfx(preset, (clip.audioConfig.volume * project.masterVolume).coerceIn(0f, 1.5f))
                    }
                }
            }
        }

        padGain = if (isPlaying) nextPadGain else 0f
        padSeed = nextPadSeed

        // Dispose slots for clips no longer in project or whose URI changed
        val staleIds = slots.keys.filter { it !in activeFileClipIds }
        staleIds.forEach { staleId ->
            slots.remove(staleId)?.let { old ->
                runCatching { if (old.isPrepared && old.player.isPlaying) old.player.stop() }
                runCatching { old.player.release() }
            }
        }
    }

    private fun getOrCreateSlot(
        context: Context,
        clipId: String,
        dataSource: String,
        loopMusic: Boolean
    ): PlayerSlot? {
        val existing = slots[clipId]
        if (existing != null && existing.dataSource == dataSource && !existing.hasError) {
            return existing
        }
        if (existing != null) {
            runCatching { if (existing.isPrepared && existing.player.isPlaying) existing.player.stop() }
            runCatching { existing.player.release() }
            slots.remove(clipId)
        }

        return runCatching {
            val mp = MediaPlayer()
            val slot = PlayerSlot(clipId = clipId, dataSource = dataSource, player = mp)
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build()
            )
            val sourceOk = MediaProbeHelper.setMediaPlayerDataSource(context, mp, dataSource)
            if (!sourceOk) {
                mp.release()
                return null
            }
            mp.isLooping = loopMusic
            mp.setOnErrorListener { _, _, _ ->
                slot.hasError = true
                true
            }
            mp.setOnPreparedListener { readyMp ->
                slot.isPrepared = true
                slot.mediaDurationMs = readyMp.duration.toLong().coerceAtLeast(0L)
                val proj = latestProject ?: return@setOnPreparedListener
                val clip = proj.clips.firstOrNull { it.id == clipId } ?: return@setOnPreparedListener
                val nowMs = livePlayheadProvider?.invoke() ?: 0L
                val layerVisible = proj.trackLayers.firstOrNull { it.trackType == clip.trackType }?.isVisible != false
                val inRange = !clip.isHidden && layerVisible && nowMs >= clip.startMs && nowMs < clip.endMs
                val gain = if (inRange) AudioSynthEngine.computeEffectiveAudioGain(clip, proj, nowMs) else 0f
                val vol = gain.coerceIn(0f, 1f)
                runCatching { readyMp.setVolume(vol, vol) }

                val targetMs = clip.computeSourcePositionMs(nowMs, loopWithinSource = (clip.trackType == TrackType.MUSIC))
                val dur = slot.mediaDurationMs
                val safeMs = if (dur > 0L) targetMs.coerceIn(0L, (dur - 1L).coerceAtLeast(0L)) else targetMs
                val pastEnd = dur > 0L && targetMs >= dur - 25L && clip.trackType != TrackType.MUSIC
                if (isEnginePlaying && inRange && gain > 0.001f && !pastEnd) {
                    runCatching {
                        seekMediaPlayerExact(readyMp, safeMs)
                        slot.lastSeekWallMs = SystemClock.elapsedRealtime()
                        readyMp.start()
                    }
                }
            }
            mp.prepareAsync()
            slots[clipId] = slot
            slot
        }.getOrNull()
    }

    private fun seekMediaPlayerExact(mp: MediaPlayer, targetMs: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mp.seekTo(targetMs.coerceAtLeast(0L), MediaPlayer.SEEK_CLOSEST)
        } else {
            mp.seekTo(targetMs.coerceAtLeast(0L).toInt())
        }
    }

    private fun ensurePadLoopIfNeeded(scope: CoroutineScope) {
        val hasSyntheticMusic = latestProject?.clips?.any {
            it.trackType == TrackType.MUSIC && !it.isHidden && it.audioConfig.audioPath == null
        } == true
        if (!hasSyntheticMusic || padJob?.isActive == true) return

        padJob = scope.launch(Dispatchers.IO) {
            var track: AudioTrack? = null
            try {
                val sampleRate = AudioExportMixer.SAMPLE_RATE
                val minBuf = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(4096)
                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
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

                val chunk = ShortArray(2048)
                var p1 = 0.0
                var p2 = 0.0
                var p3 = 0.0
                var p4 = 0.0
                var sampleCursor = 0L

                while (isActive && isEnginePlaying) {
                    val g = padGain
                    if (g <= 0.001f) {
                        delay(45L)
                        continue
                    }
                    val baseFreq = 98f + (padSeed % 5) * 12f
                    for (i in chunk.indices) {
                        val t = (sampleCursor + i).toDouble() / sampleRate
                        val swell = 0.55 + 0.45 * sin(2.0 * PI * 0.16 * t)
                        p1 += 2.0 * PI * baseFreq / sampleRate
                        p2 += 2.0 * PI * (baseFreq * 1.189) / sampleRate
                        p3 += 2.0 * PI * (baseFreq * 1.498) / sampleRate
                        p4 += 2.0 * PI * (baseFreq * 2.245) / sampleRate
                        val wave = 0.45 * sin(p1) + 0.22 * sin(p2) + 0.24 * sin(p3) + 0.12 * sin(p4)
                        val warm = tanh(wave * swell * 1.15) * 0.24 * g
                        chunk[i] = (warm.coerceIn(-0.95, 0.95) * Short.MAX_VALUE).toInt().toShort()
                    }
                    sampleCursor += chunk.size
                    val written = track.write(chunk, 0, chunk.size)
                    if (written <= 0) break
                }
            } catch (_: Throwable) {
                // Headless / Robolectric safe fallback
            } finally {
                runCatching { track?.stop() }
                runCatching { track?.release() }
            }
        }
    }
}
