package com.example.ui.editor

import android.app.Application
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ProjectRepository
import com.example.data.local.StudioDatabase
import com.example.data.local.StudioPreferences
import com.example.domain.engine.AudioSynthEngine
import com.example.domain.engine.CustomFontManager
import com.example.domain.engine.ExportResult
import com.example.domain.engine.LiveAudioPlaybackEngine
import com.example.domain.engine.SpeedRampEngine
import com.example.domain.engine.VideoExportEngine
import com.example.domain.engine.VoiceoverRecorder
import com.example.domain.model.AspectRatioPreset
import com.example.domain.model.AudioConfig
import com.example.domain.model.ClipAnimationProfile
import com.example.domain.model.ComparisonChartConfig
import com.example.domain.model.ExportFps
import com.example.domain.model.InvestigativeDocConfig
import com.example.domain.model.InvestigativeToolType
import com.example.domain.model.Keyframe
import com.example.domain.model.KeyframeEasing
import com.example.domain.model.LowerThirdTemplate
import com.example.domain.model.MediaSource
import com.example.domain.model.PresetCatalog
import com.example.domain.model.PreviewResolution
import com.example.domain.model.StudioFontOption
import com.example.domain.model.StudioProject
import com.example.domain.model.TextLayerConfig
import com.example.domain.model.TimelineClip
import com.example.domain.model.TrackLayerState
import com.example.domain.model.TrackType
import com.example.domain.model.TransitionType
import com.example.domain.model.UserAnimationPreset
import com.example.domain.model.VfxConfig
import com.example.domain.model.VfxEffectType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.abs

sealed interface ExportUiState {
    data object Idle : ExportUiState
    data class Exporting(val progress: Float, val status: String) : ExportUiState
    data class Completed(val result: ExportResult) : ExportUiState
}

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ProjectRepository(StudioDatabase.getInstance(application).projectDao())
    private val preferences = StudioPreferences(application)
    val voiceoverRecorder = VoiceoverRecorder(application)

    val isBanglaUi: StateFlow<Boolean> = preferences.isBanglaUi
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val userAnimationPresets: StateFlow<List<UserAnimationPreset>> = preferences.userAnimationPresets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _project = MutableStateFlow<StudioProject?>(null)
    val project: StateFlow<StudioProject?> = _project.asStateFlow()

    private val _selectedClipId = MutableStateFlow<String?>(null)
    val selectedClipId: StateFlow<String?> = _selectedClipId.asStateFlow()

    private val _playheadMs = MutableStateFlow(0L)
    val playheadMs: StateFlow<Long> = _playheadMs.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _beforeAfterBypassAnim = MutableStateFlow(false)
    val beforeAfterBypassAnim: StateFlow<Boolean> = _beforeAfterBypassAnim.asStateFlow()

    private val _copiedKeyframe = MutableStateFlow<Keyframe?>(null)
    val copiedKeyframe: StateFlow<Keyframe?> = _copiedKeyframe.asStateFlow()

    private val _autoSaveLabel = MutableStateFlow("Saved")
    val autoSaveLabel: StateFlow<String> = _autoSaveLabel.asStateFlow()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private val _exportState = MutableStateFlow<ExportUiState>(ExportUiState.Idle)
    val exportState: StateFlow<ExportUiState> = _exportState.asStateFlow()

    private val undoStack = ArrayDeque<StudioProject>()
    private val redoStack = ArrayDeque<StudioProject>()
    private val liveAudioEngine = LiveAudioPlaybackEngine()
    private var playbackJob: Job? = null
    private var replayStopJob: Job? = null
    private var saveJob: Job? = null
    private var exportJob: Job? = null
    private var lastTriggeredSfxClipId: String? = null

    fun loadProject(projectId: String) {
        if (_project.value?.id == projectId) return
        viewModelScope.launch {
            val loaded = repository.getProject(projectId) ?: return@launch
            val normalizedLayers = ensureAllTrackLayers(loaded.trackLayers)
            val ready = loaded.copy(trackLayers = normalizedLayers)
            undoStack.clear()
            redoStack.clear()
            _canUndo.value = false
            _canRedo.value = false
            _project.value = ready
            _selectedClipId.value = ready.clips.firstOrNull()?.id
            _playheadMs.value = 0L
        }
    }

    private fun ensureAllTrackLayers(existing: List<TrackLayerState>): List<TrackLayerState> {
        val map = existing.associateBy { it.trackType }
        return TrackType.entries.mapIndexed { idx, t ->
            map[t] ?: TrackLayerState(trackType = t, order = idx)
        }.sortedBy { it.order }
    }

    private fun commitProject(updated: StudioProject, pushUndo: Boolean = true) {
        val current = _project.value ?: return
        if (pushUndo) {
            undoStack.addLast(current)
            if (undoStack.size > 30) undoStack.removeFirst()
            redoStack.clear()
            _canUndo.value = undoStack.isNotEmpty()
            _canRedo.value = false
        }
        val recalculatedDuration = computeTotalDurationMs(updated.clips)
        val normalized = updated.copy(
            totalDurationMs = recalculatedDuration,
            lastEditedAt = System.currentTimeMillis(),
            trackLayers = ensureAllTrackLayers(updated.trackLayers)
        )
        _project.value = normalized
        scheduleAutoSave(normalized)
    }

    private fun scheduleAutoSave(project: StudioProject) {
        _autoSaveLabel.value = "Saving..."
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(450L)
            repository.saveProject(project)
            _autoSaveLabel.value = "Saved"
        }
    }

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        _project.value?.let { redoStack.addLast(it) }
        _project.value = prev
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
        scheduleAutoSave(prev)
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        _project.value?.let { undoStack.addLast(it) }
        _project.value = next
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
        scheduleAutoSave(next)
    }

    fun selectClip(clipId: String?) {
        _selectedClipId.value = clipId
    }

    fun seekTo(ms: Long) {
        val dur = _project.value?.totalDurationMs ?: 15_000L
        val clamped = ms.coerceIn(0L, dur)
        _playheadMs.value = clamped
        if (_isPlaying.value) {
            _project.value?.let { proj ->
                liveAudioEngine.startFrom(viewModelScope, getApplication(), proj, clamped, debounceMs = 55L)
            }
        }
    }

    fun stepFrame(deltaMs: Long) {
        replayStopJob?.cancel()
        _isPlaying.value = false
        playbackJob?.cancel()
        liveAudioEngine.stop()
        seekTo(_playheadMs.value + deltaMs)
    }

    fun togglePlayback() {
        replayStopJob?.cancel()
        if (_isPlaying.value) {
            _isPlaying.value = false
            playbackJob?.cancel()
            liveAudioEngine.stop()
        } else {
            val proj = _project.value ?: return
            if (_playheadMs.value >= proj.totalDurationMs) {
                _playheadMs.value = 0L
            }
            _isPlaying.value = true
            lastTriggeredSfxClipId = null
            liveAudioEngine.startFrom(viewModelScope, getApplication(), proj, _playheadMs.value)
            playbackJob = viewModelScope.launch {
                val frameStepMs = 33L
                while (isActive && _isPlaying.value) {
                    val activeProj = _project.value ?: break
                    val nowMs = _playheadMs.value

                    val activeVideo = activeProj.clips.firstOrNull {
                        it.trackType == TrackType.VIDEO && !it.isHidden && nowMs in it.startMs..it.endMs
                    }
                    val speedMult = if (activeVideo != null && activeVideo.durationMs > 0L) {
                        val norm = (nowMs - activeVideo.startMs).toFloat() / activeVideo.durationMs.toFloat()
                        SpeedRampEngine.evaluateSpeedAt(activeVideo.speedRamp, norm)
                    } else 1f

                    val nextMs = nowMs + (frameStepMs * speedMult).toLong().coerceAtLeast(8L)
                    if (nextMs >= activeProj.totalDurationMs) {
                        _playheadMs.value = 0L
                        lastTriggeredSfxClipId = null
                    } else {
                        _playheadMs.value = nextMs
                    }
                    delay(frameStepMs)
                }
            }
        }
    }

    fun toggleBeforeAfterBypassAnim() {
        _beforeAfterBypassAnim.value = !_beforeAfterBypassAnim.value
    }

    fun replayClipAnimation(clipId: String, previewDurationMs: Long? = null) {
        val proj = _project.value ?: return
        val clip = proj.clips.firstOrNull { it.id == clipId } ?: return
        replayStopJob?.cancel()
        _beforeAfterBypassAnim.value = false
        if (_isPlaying.value) {
            _isPlaying.value = false
            playbackJob?.cancel()
            liveAudioEngine.stop()
        }
        _playheadMs.value = clip.startMs
        togglePlayback()
        val windowMs = previewDurationMs ?: clip.durationMs.coerceAtMost(15_000L)
        replayStopJob = viewModelScope.launch {
            delay(windowMs.coerceAtLeast(250L))
            if (_isPlaying.value) {
                _isPlaying.value = false
                playbackJob?.cancel()
                liveAudioEngine.stop()
            }
        }
    }

    fun renameProject(newName: String) {
        val proj = _project.value ?: return
        if (newName.isBlank()) return
        commitProject(proj.copy(name = newName.trim()))
    }

    fun cyclePreviewResolution() {
        val proj = _project.value ?: return
        val entries = PreviewResolution.entries
        val next = entries[(entries.indexOf(proj.previewResolution) + 1) % entries.size]
        commitProject(proj.copy(previewResolution = next))
    }

    fun setAspectRatio(ratio: AspectRatioPreset) {
        val proj = _project.value ?: return
        commitProject(proj.copy(aspectRatio = ratio))
    }

    fun toggleVideoPreview() {
        val proj = _project.value ?: return
        commitProject(proj.copy(videoTrackPreviewOn = !proj.videoTrackPreviewOn), pushUndo = false)
    }

    fun toggleEditMode() {
        val proj = _project.value ?: return
        commitProject(proj.copy(editModeOn = !proj.editModeOn), pushUndo = false)
    }

    fun toggleSafeZoneGuides() {
        val proj = _project.value ?: return
        commitProject(proj.copy(showSafeZoneGuides = !proj.showSafeZoneGuides), pushUndo = false)
    }

    fun setMasterVolume(volume: Float) {
        val proj = _project.value ?: return
        commitProject(proj.copy(masterVolume = volume.coerceIn(0f, 1.5f)), pushUndo = false)
    }

    fun moveTrackOrder(trackType: TrackType, delta: Int) {
        val proj = _project.value ?: return
        val sorted = proj.trackLayers.sortedBy { it.order }.toMutableList()
        val idx = sorted.indexOfFirst { it.trackType == trackType }
        if (idx < 0) return
        val targetIdx = (idx + delta).coerceIn(0, sorted.lastIndex)
        if (idx == targetIdx) return
        val item = sorted.removeAt(idx)
        sorted.add(targetIdx, item)
        val reindexed = sorted.mapIndexed { i, state -> state.copy(order = i) }
        commitProject(proj.copy(trackLayers = reindexed))
    }

    fun toggleTrackVisibility(trackType: TrackType) {
        val proj = _project.value ?: return
        val updated = proj.trackLayers.map {
            if (it.trackType == trackType) it.copy(isVisible = !it.isVisible) else it
        }
        commitProject(proj.copy(trackLayers = updated))
    }

    fun toggleTrackLock(trackType: TrackType) {
        val proj = _project.value ?: return
        val updated = proj.trackLayers.map {
            if (it.trackType == trackType) it.copy(isLocked = !it.isLocked) else it
        }
        commitProject(proj.copy(trackLayers = updated))
    }

    private fun isClipLocked(proj: StudioProject, clip: TimelineClip): Boolean {
        if (clip.isLocked) return true
        return proj.trackLayers.firstOrNull { it.trackType == clip.trackType }?.isLocked == true
    }

    fun toggleClipLock(clipId: String) {
        val proj = _project.value ?: return
        val newClips = proj.clips.map { if (it.id == clipId) it.copy(isLocked = !it.isLocked) else it }
        commitProject(proj.copy(clips = newClips))
    }

    fun moveClipTime(clipId: String, newStartMs: Long, pushUndo: Boolean = false) {
        val proj = _project.value ?: return
        val clip = proj.clips.firstOrNull { it.id == clipId } ?: return
        if (isClipLocked(proj, clip)) return
        val clampedStart = newStartMs.coerceAtLeast(0L)
        val newClips = proj.clips.map { if (it.id == clipId) it.copy(startMs = clampedStart) else it }
        commitProject(proj.copy(clips = newClips), pushUndo = pushUndo)
    }

    fun trimClip(clipId: String, newStartMs: Long, newDurationMs: Long, pushUndo: Boolean = false) {
        val proj = _project.value ?: return
        val clip = proj.clips.firstOrNull { it.id == clipId } ?: return
        if (isClipLocked(proj, clip)) return
        val safeStart = newStartMs.coerceAtLeast(0L)
        val safeDur = newDurationMs.coerceIn(200L, 600_000L)
        val newClips = proj.clips.map {
            if (it.id == clipId) it.copy(startMs = safeStart, durationMs = safeDur) else it
        }
        commitProject(proj.copy(clips = newClips), pushUndo = pushUndo)
    }

    private fun findFreeLaneIndex(
        clips: List<TimelineClip>,
        trackType: TrackType,
        startMs: Long,
        endMs: Long,
        preferredLane: Int = 0,
        excludeId: String? = null
    ): Int {
        val trackClips = clips.filter { it.trackType == trackType && it.id != excludeId }
        fun laneOverlaps(lane: Int): Boolean {
            return trackClips.any { c ->
                c.laneIndex == lane && startMs < c.endMs - 40L && endMs > c.startMs + 40L
            }
        }
        if (!laneOverlaps(preferredLane)) return preferredLane
        for (candidate in preferredLane..12) {
            if (!laneOverlaps(candidate)) return candidate
        }
        for (candidate in 0 until preferredLane) {
            if (!laneOverlaps(candidate)) return candidate
        }
        return preferredLane
    }

    fun moveClipLane(clipId: String, delta: Int) {
        val proj = _project.value ?: return
        val clip = proj.clips.firstOrNull { it.id == clipId } ?: return
        if (isClipLocked(proj, clip)) return
        val nextLane = (clip.laneIndex + delta).coerceIn(0, 8)
        if (nextLane == clip.laneIndex) return
        val newClips = proj.clips.map {
            if (it.id == clipId) it.copy(laneIndex = nextLane) else it
        }
        commitProject(proj.copy(clips = newClips))
    }

    fun updateClip(clipId: String, pushUndo: Boolean = true, transform: (TimelineClip) -> TimelineClip) {
        val proj = _project.value ?: return
        val newClips = proj.clips.map { if (it.id == clipId) transform(it) else it }
        commitProject(proj.copy(clips = newClips), pushUndo = pushUndo)
    }

    fun updateDirectCanvasGesture(
        clipId: String,
        deltaXNorm: Float,
        deltaYNorm: Float,
        scaleFactor: Float,
        rotationDelta: Float
    ) {
        val proj = _project.value ?: return
        if (!proj.editModeOn) return
        val clip = proj.clips.firstOrNull { it.id == clipId } ?: return
        if (isClipLocked(proj, clip)) return
        val localMs = (_playheadMs.value - clip.startMs).coerceIn(0L, clip.durationMs)

        val nearbyIdx = clip.keyframes.indexOfFirst { abs(it.timeOffsetMs - localMs) <= 350L }
        val updatedClip = if (nearbyIdx >= 0) {
            val kfs = clip.keyframes.toMutableList()
            val kf = kfs[nearbyIdx]
            kfs[nearbyIdx] = kf.copy(
                posX = (kf.posX + deltaXNorm).coerceIn(-0.6f, 0.6f),
                posY = (kf.posY + deltaYNorm).coerceIn(-0.6f, 0.6f),
                scale = (kf.scale * scaleFactor).coerceIn(0.25f, 3.5f),
                rotation = (kf.rotation + rotationDelta) % 360f
            )
            clip.copy(keyframes = kfs)
        } else {
            clip.copy(
                posX = (clip.posX + deltaXNorm).coerceIn(-0.6f, 0.6f),
                posY = (clip.posY + deltaYNorm).coerceIn(-0.6f, 0.6f),
                scale = (clip.scale * scaleFactor).coerceIn(0.25f, 3.5f),
                rotation = (clip.rotation + rotationDelta) % 360f
            )
        }
        commitProject(proj.copy(clips = proj.clips.map { if (it.id == clipId) updatedClip else it }), pushUndo = false)
    }

    fun addKeyframeAtPlayhead(clipId: String) {
        val proj = _project.value ?: return
        val clip = proj.clips.firstOrNull { it.id == clipId } ?: return
        if (isClipLocked(proj, clip)) return
        val localMs = (_playheadMs.value - clip.startMs).coerceIn(0L, clip.durationMs)
        val existing = clip.keyframes.indexOfFirst { abs(it.timeOffsetMs - localMs) < 180L }
        val updatedKeyframes = if (existing >= 0) {
            clip.keyframes.toMutableList().apply { removeAt(existing) }
        } else {
            (clip.keyframes + Keyframe(
                timeOffsetMs = localMs,
                posX = clip.posX,
                posY = clip.posY,
                scale = clip.scale,
                rotation = clip.rotation,
                opacity = clip.opacity,
                blurRadius = clip.animationProfile.blurRadius
            )).sortedBy { it.timeOffsetMs }
        }
        updateClip(clipId) { it.copy(keyframes = updatedKeyframes) }
    }

    fun deleteKeyframe(clipId: String, kfId: String) {
        updateClip(clipId) { clip ->
            clip.copy(keyframes = clip.keyframes.filterNot { it.id == kfId })
        }
    }

    fun moveKeyframe(clipId: String, kfId: String, deltaMs: Long) {
        updateClip(clipId) { clip ->
            val updated = clip.keyframes.map { kf ->
                if (kf.id == kfId) kf.copy(timeOffsetMs = (kf.timeOffsetMs + deltaMs).coerceIn(0L, clip.durationMs)) else kf
            }.sortedBy { it.timeOffsetMs }
            clip.copy(keyframes = updated)
        }
    }

    fun updateKeyframeEasing(clipId: String, kfId: String, easing: KeyframeEasing) {
        updateClip(clipId) { clip ->
            clip.copy(keyframes = clip.keyframes.map { if (it.id == kfId) it.copy(easing = easing) else it })
        }
    }

    fun copyKeyframe(kf: Keyframe) {
        _copiedKeyframe.value = kf
    }

    fun pasteKeyframeAtPlayhead(clipId: String) {
        val src = _copiedKeyframe.value ?: return
        val proj = _project.value ?: return
        val clip = proj.clips.firstOrNull { it.id == clipId } ?: return
        val localMs = (_playheadMs.value - clip.startMs).coerceIn(0L, clip.durationMs)
        val pasted = src.copy(id = UUID.randomUUID().toString(), timeOffsetMs = localMs)
        val merged = (clip.keyframes.filterNot { abs(it.timeOffsetMs - localMs) < 120L } + pasted).sortedBy { it.timeOffsetMs }
        updateClip(clipId) { it.copy(keyframes = merged) }
    }

    fun saveUserAnimationPreset(name: String, profile: ClipAnimationProfile) {
        viewModelScope.launch {
            preferences.saveUserAnimationPreset(UserAnimationPreset(name = name.trim().ifBlank { "Custom Motion" }, profile = profile))
        }
    }

    fun renameUserAnimationPreset(presetId: String, newName: String) {
        viewModelScope.launch { preferences.renameUserAnimationPreset(presetId, newName) }
    }

    fun deleteUserAnimationPreset(presetId: String) {
        viewModelScope.launch { preferences.deleteUserAnimationPreset(presetId) }
    }

    fun applyUserAnimationPreset(clipId: String, preset: UserAnimationPreset) {
        val prof = preset.profile
        updateClip(clipId) { clip ->
            clip.copy(
                animationProfile = prof,
                inAnimationId = if (prof.inSlot.enabled) prof.inSlot.animationId else "in_none",
                loopAnimationId = if (prof.loopSlot.enabled) prof.loopSlot.animationId else "loop_none",
                outAnimationId = if (prof.outSlot.enabled) prof.outSlot.animationId else "out_none"
            )
        }
    }

    private fun probeMediaDurationMs(uri: Uri, fallbackMs: Long): Long {
        val mmrParsed = runCatching {
            val mmr = MediaMetadataRetriever()
            mmr.setDataSource(getApplication(), uri)
            val durStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            mmr.release()
            durStr?.toLongOrNull()
        }.getOrNull()

        if (mmrParsed != null && mmrParsed > 0L) {
            return mmrParsed.coerceIn(1_000L, 600_000L)
        }

        val extractorParsed = runCatching {
            val ex = MediaExtractor()
            ex.setDataSource(getApplication(), uri, null)
            var maxDurUs = 0L
            for (i in 0 until ex.trackCount) {
                val fmt = ex.getTrackFormat(i)
                if (fmt.containsKey(MediaFormat.KEY_DURATION)) {
                    maxDurUs = maxOf(maxDurUs, fmt.getLong(MediaFormat.KEY_DURATION))
                }
            }
            ex.release()
            if (maxDurUs > 0L) maxDurUs / 1000L else null
        }.getOrNull()

        return (extractorParsed ?: fallbackMs).coerceIn(1_000L, 600_000L)
    }

    fun addMediaClip(uri: Uri, isVideo: Boolean, label: String) {
        viewModelScope.launch {
            val realDuration = if (isVideo) {
                withContext(Dispatchers.IO) { probeMediaDurationMs(uri, 5_000L) }
            } else {
                5_000L
            }
            val proj = _project.value ?: return@launch
            val videoClips = proj.clips.filter { it.trackType == TrackType.VIDEO && it.laneIndex == 0 }
            val startAt = videoClips.maxOfOrNull { it.endMs } ?: 0L
            val lane = findFreeLaneIndex(proj.clips, TrackType.VIDEO, startAt, startAt + realDuration)
            val clip = TimelineClip(
                trackType = TrackType.VIDEO,
                laneIndex = lane,
                title = label.take(24),
                startMs = startAt,
                durationMs = realDuration,
                audioConfig = AudioConfig(volume = if (isVideo) 1.0f else 0f),
                mediaSource = MediaSource(uriString = uri.toString(), isVideo = isVideo, label = label)
            )
            commitProject(proj.copy(clips = proj.clips + clip))
            _selectedClipId.value = clip.id
            _playheadMs.value = clip.startMs + 200L
        }
    }

    fun addGraphicOverlayUri(uri: Uri, label: String) {
        val proj = _project.value ?: return
        val startAt = _playheadMs.value
        val dur = 4_500L
        val lane = findFreeLaneIndex(proj.clips, TrackType.GRAPHICS, startAt, startAt + dur)
        val clip = TimelineClip(
            trackType = TrackType.GRAPHICS,
            laneIndex = lane,
            title = label.take(22),
            startMs = startAt,
            durationMs = dur,
            scale = 0.65f,
            inAnimationId = "in_zoom_pop",
            outAnimationId = "out_fade",
            mediaSource = MediaSource(uriString = uri.toString(), isVideo = false, label = label)
        )
        commitProject(proj.copy(clips = proj.clips + clip))
        _selectedClipId.value = clip.id
    }

    fun addTextClip(template: LowerThirdTemplate = LowerThirdTemplate.NONE) {
        val proj = _project.value ?: return
        val isDoc = template != LowerThirdTemplate.NONE
        val track = if (isDoc) TrackType.GRAPHICS else TrackType.TEXT
        val startAt = _playheadMs.value
        val dur = 4_500L
        val lane = findFreeLaneIndex(proj.clips, track, startAt, startAt + dur)
        val clip = TimelineClip(
            trackType = track,
            laneIndex = lane,
            title = if (isDoc) template.labelBn else "বাংলা টেক্সট",
            startMs = startAt,
            durationMs = dur,
            posY = if (isDoc) 0.25f else 0f,
            inAnimationId = if (isDoc) "in_slide_left" else "in_zoom_pop",
            outAnimationId = "out_fade",
            lockedSfxId = if (isDoc) "sfx_pop_1" else null,
            textConfig = TextLayerConfig(
                text = when (template) {
                    LowerThirdTemplate.DOCUMENTARY_DATE_CARD -> "১৯৭১ · ঐতিহাসিক আর্কাইভ"
                    LowerThirdTemplate.NEWS_LOWER_THIRD -> "ঢাকা স্টুডিও · লাইভ ন্যারেটিভ"
                    LowerThirdTemplate.CINEMA_CHAPTER -> "অধ্যায় ১ · সূচনা"
                    LowerThirdTemplate.MINIMAL_CAPTION -> "সুন্দর বাংলা ক্যাপশন"
                    LowerThirdTemplate.NONE -> "নতুন মোশন টেক্সট"
                },
                subtitle = if (isDoc) "ChitraFlow Documentary" else "",
                fontId = if (isDoc) StudioFontOption.NOTO_SERIF_BENGALI.id else StudioFontOption.HIND_SILIGURI.id,
                karaokeHighlight = template == LowerThirdTemplate.NONE,
                lowerThirdTemplate = template
            )
        )
        commitProject(proj.copy(clips = proj.clips + clip))
        _selectedClipId.value = clip.id
    }

    fun importCustomFont(clipId: String, uri: Uri) {
        viewModelScope.launch {
            val imported = withContext(Dispatchers.IO) {
                CustomFontManager.importFontFromUri(getApplication(), uri)
            } ?: return@launch
            val (fontPath, fontName) = imported
            updateClip(clipId) { clip ->
                val currentText = clip.textConfig ?: TextLayerConfig()
                clip.copy(
                    textConfig = currentText.copy(
                        customFontPath = fontPath,
                        customFontName = fontName
                    )
                )
            }
        }
    }

    fun addAdjustmentLayer() {
        val proj = _project.value ?: return
        val startAt = _playheadMs.value
        val dur = 5_000L
        val lane = findFreeLaneIndex(proj.clips, TrackType.ADJUSTMENT, startAt, startAt + dur)
        val clip = TimelineClip(
            trackType = TrackType.ADJUSTMENT,
            laneIndex = lane,
            title = "Lumetri Adjustment",
            startMs = startAt,
            durationMs = dur,
            colorGrade = PresetCatalog.lumetriPresets[1].grade
        )
        commitProject(proj.copy(clips = proj.clips + clip))
        _selectedClipId.value = clip.id
    }

    fun addVfxClip(effectType: VfxEffectType = VfxEffectType.ANAMORPHIC_FLARE) {
        val proj = _project.value ?: return
        val startAt = _playheadMs.value
        val dur = 5_000L
        val lane = findFreeLaneIndex(proj.clips, TrackType.VFX, startAt, startAt + dur)
        val clip = TimelineClip(
            trackType = TrackType.VFX,
            laneIndex = lane,
            title = effectType.labelBn,
            startMs = startAt,
            durationMs = dur,
            vfxConfig = VfxConfig(effectType = effectType, intensity = 0.72f, speed = 1f)
        )
        commitProject(proj.copy(clips = proj.clips + clip))
        _selectedClipId.value = clip.id
    }

    fun addComparisonChartClip(barCount: Int = 0) {
        val proj = _project.value ?: return
        val safeCount = barCount.coerceIn(0, 100)
        val bars = if (safeCount == 0) emptyList() else PresetCatalog.generateDefaultBars(safeCount.coerceAtMost(60))
        val startAt = _playheadMs.value
        val dur = 6_000L
        val lane = findFreeLaneIndex(proj.clips, TrackType.GRAPHICS, startAt, startAt + dur)
        val clip = TimelineClip(
            trackType = TrackType.GRAPHICS,
            laneIndex = lane,
            title = if (safeCount == 0) "কাস্টম চার্ট (০)" else "তুলনামূলক চার্ট ($safeCount)",
            startMs = startAt,
            durationMs = dur,
            inAnimationId = "in_slide_up",
            outAnimationId = "out_fade",
            lockedSfxId = "sfx_whoosh_1",
            chartConfig = ComparisonChartConfig(
                title = "কাস্টম ডেটা ও গ্রাফ চার্ট",
                unitSuffix = "%",
                barCount = safeCount,
                isHorizontal = true,
                sortDescending = true,
                animateGrowth = true,
                bars = bars
            )
        )
        commitProject(proj.copy(clips = proj.clips + clip))
        _selectedClipId.value = clip.id
    }

    fun addInvestigativeClip(toolType: InvestigativeToolType) {
        val proj = _project.value ?: return
        val defaultConfig = when (toolType) {
            InvestigativeToolType.EVIDENCE_HIGHLIGHTER -> InvestigativeDocConfig(
                toolType = toolType,
                headline = "গোপন ফাইল নং-৪০২",
                bodyText = "প্রাথমিক তদন্তে দেখা গেছে এই লেনদেনের পেছনে বড় সিন্ডিকেট জড়িত ছিল"
            )
            InvestigativeToolType.NEWSPAPER_LOUPE -> InvestigativeDocConfig(
                toolType = toolType,
                headline = "দৈনিক সংবাদ আর্কাইভ · ১৯৭৪",
                bodyText = "ঐতিহাসিক প্রতিবেদন: অজানা সত্য উন্মোচিত হলো অনুসন্ধানী জরিপে"
            )
            InvestigativeToolType.REDACTION_CENSOR -> InvestigativeDocConfig(
                toolType = toolType,
                headline = "CLASSIFIED · TOP SECRET",
                bodyText = "সংশ্লিষ্ট ব্যক্তির পরিচয় গোপন রাখা হয়েছে"
            )
            InvestigativeToolType.MAP_RADAR_ROUTE -> InvestigativeDocConfig(
                toolType = toolType,
                headline = "অপারেশন রুট ও রাডার ট্র্যাক",
                pointALabel = "ঢাকা",
                pointBLabel = "চট্টগ্রাম"
            )
            InvestigativeToolType.STAT_YEAR_COUNTER -> InvestigativeDocConfig(
                toolType = toolType,
                headline = "সময়কাল পরিবর্তন",
                bodyText = "ঐতিহাসিক সময়ের ব্যবধান",
                counterStart = 1952,
                counterEnd = 2026,
                counterPrefix = "",
                counterSuffix = " সাল"
            )
            InvestigativeToolType.NONE -> InvestigativeDocConfig(toolType = InvestigativeToolType.EVIDENCE_HIGHLIGHTER)
        }
        val startAt = _playheadMs.value
        val dur = 5_500L
        val lane = findFreeLaneIndex(proj.clips, TrackType.GRAPHICS, startAt, startAt + dur)
        val clip = TimelineClip(
            trackType = TrackType.GRAPHICS,
            laneIndex = lane,
            title = toolType.labelBn.take(20),
            startMs = startAt,
            durationMs = dur,
            inAnimationId = "in_cinema_reveal",
            outAnimationId = "out_fade",
            lockedSfxId = "sfx_type_1",
            investigativeConfig = defaultConfig
        )
        commitProject(proj.copy(clips = proj.clips + clip))
        _selectedClipId.value = clip.id
    }

    fun applyTransitionToAllClips(
        transitionIn: TransitionType,
        inDurationMs: Long,
        transitionOut: TransitionType,
        outDurationMs: Long
    ) {
        val proj = _project.value ?: return
        val safeInDur = inDurationMs.coerceIn(200L, 2500L)
        val safeOutDur = outDurationMs.coerceIn(200L, 2500L)
        val updated = proj.clips.map { clip ->
            if (clip.trackType == TrackType.VIDEO && !isClipLocked(proj, clip)) {
                clip.copy(
                    transitionIn = transitionIn,
                    transitionOut = transitionOut,
                    transitionInDurationMs = safeInDur,
                    transitionOutDurationMs = safeOutDur
                )
            } else {
                clip
            }
        }
        commitProject(proj.copy(clips = updated))
    }

    fun ensureChartClipForStudio(): TimelineClip? {
        val proj = _project.value ?: return null
        val selected = proj.clips.firstOrNull { it.id == _selectedClipId.value }
        if (selected?.chartConfig != null) return selected
        addComparisonChartClip(0)
        val latestProj = _project.value ?: return null
        return latestProj.clips.firstOrNull { it.id == _selectedClipId.value }
    }

    fun ensureMapClipForStudio(): TimelineClip? {
        val proj = _project.value ?: return null
        val selected = proj.clips.firstOrNull { it.id == _selectedClipId.value }
        if (selected?.investigativeConfig?.toolType == InvestigativeToolType.MAP_RADAR_ROUTE) return selected
        addInvestigativeClip(InvestigativeToolType.MAP_RADAR_ROUTE)
        val latestProj = _project.value ?: return null
        return latestProj.clips.firstOrNull { it.id == _selectedClipId.value }
    }

    fun generateAutoDocumentaryIntoCurrent(topic: String, scriptNote: String) {
        val proj = _project.value ?: return
        val generated = PresetCatalog.buildAutoInvestigativeDocumentary(topic, scriptNote)
        val merged = proj.copy(
            name = generated.name,
            aspectRatio = generated.aspectRatio,
            totalDurationMs = generated.totalDurationMs,
            clips = generated.clips
        )
        commitProject(merged)
        _selectedClipId.value = merged.clips.firstOrNull()?.id
        _playheadMs.value = 0L
    }

    fun addAudioClip(uri: Uri?, label: String, isVoiceover: Boolean, recordedPath: String? = null, durationMs: Long = 6_000L) {
        viewModelScope.launch {
            val resolvedDuration = if (uri != null && recordedPath == null) {
                withContext(Dispatchers.IO) { probeMediaDurationMs(uri, durationMs) }
            } else {
                durationMs
            }
            val proj = _project.value ?: return@launch
            val safeDur = resolvedDuration.coerceIn(1_000L, 600_000L)
            val track = if (isVoiceover) TrackType.VOICEOVER else TrackType.MUSIC
            val startAt = _playheadMs.value
            val lane = findFreeLaneIndex(proj.clips, track, startAt, startAt + safeDur)
            val clip = TimelineClip(
                trackType = track,
                laneIndex = lane,
                title = label,
                startMs = startAt,
                durationMs = safeDur,
                audioConfig = AudioConfig(
                    volume = if (isVoiceover) 1.15f else 0.78f,
                    autoDuckingEnabled = !isVoiceover,
                    noiseReductionEnabled = isVoiceover,
                    audioPath = recordedPath ?: uri?.toString(),
                    waveformSeed = (System.currentTimeMillis() % 97).toInt()
                )
            )
            commitProject(proj.copy(clips = proj.clips + clip))
            _selectedClipId.value = clip.id
        }
    }

    fun splitSelectedClipAtPlayhead() {
        val proj = _project.value ?: return
        val id = _selectedClipId.value ?: return
        val clip = proj.clips.firstOrNull { it.id == id } ?: return
        if (isClipLocked(proj, clip)) return
        val splitAt = _playheadMs.value
        if (splitAt <= clip.startMs + 350L || splitAt >= clip.endMs - 350L) return

        val firstDur = splitAt - clip.startMs
        val secondDur = clip.durationMs - firstDur
        val firstHalf = clip.copy(durationMs = firstDur)
        val secondHalf = clip.copy(
            id = UUID.randomUUID().toString(),
            startMs = splitAt,
            durationMs = secondDur,
            keyframes = clip.keyframes
                .filter { it.timeOffsetMs >= firstDur }
                .map { it.copy(timeOffsetMs = it.timeOffsetMs - firstDur) }
        )
        val newClips = proj.clips.flatMap { if (it.id == id) listOf(firstHalf, secondHalf) else listOf(it) }
        commitProject(proj.copy(clips = newClips))
        _selectedClipId.value = secondHalf.id
    }

    fun duplicateClipSequentially(clipId: String) {
        val proj = _project.value ?: return
        val clip = proj.clips.firstOrNull { it.id == clipId } ?: return
        val startAt = clip.endMs + 100L
        val lane = findFreeLaneIndex(proj.clips, clip.trackType, startAt, startAt + clip.durationMs, preferredLane = clip.laneIndex)
        val clone = clip.copy(
            id = UUID.randomUUID().toString(),
            laneIndex = lane,
            startMs = startAt,
            title = "${clip.title} *",
            isLocked = false
        )
        commitProject(proj.copy(clips = proj.clips + clone))
        _selectedClipId.value = clone.id
    }

    fun duplicateClipToSubLayer(clipId: String) {
        val proj = _project.value ?: return
        val clip = proj.clips.firstOrNull { it.id == clipId } ?: return
        val subLane = findFreeLaneIndex(
            proj.clips,
            clip.trackType,
            clip.startMs,
            clip.endMs,
            preferredLane = (clip.laneIndex + 1).coerceAtMost(8)
        )
        val clone = clip.copy(
            id = UUID.randomUUID().toString(),
            laneIndex = subLane,
            startMs = clip.startMs,
            title = "${clip.title} (L${subLane + 1})",
            isLocked = false
        )
        commitProject(proj.copy(clips = proj.clips + clone))
        _selectedClipId.value = clone.id
    }

    fun duplicateSelectedClip() {
        val id = _selectedClipId.value ?: return
        duplicateClipSequentially(id)
    }

    fun deleteSelectedClip() {
        val proj = _project.value ?: return
        val id = _selectedClipId.value ?: return
        val clip = proj.clips.firstOrNull { it.id == id } ?: return
        if (isClipLocked(proj, clip)) return
        val remaining = proj.clips.filterNot { it.id == id }
        commitProject(proj.copy(clips = remaining))
        _selectedClipId.value = remaining.firstOrNull()?.id
    }

    fun startExport(resolution: PreviewResolution, fps: ExportFps, fastMode: Boolean = true) {
        val proj = _project.value ?: return
        replayStopJob?.cancel()
        _isPlaying.value = false
        playbackJob?.cancel()
        liveAudioEngine.stop()
        exportJob?.cancel()
        exportJob = viewModelScope.launch {
            _exportState.value = ExportUiState.Exporting(0.02f, "স্টুডিও রেন্ডার শুরু হচ্ছে...")
            try {
                val result = VideoExportEngine.exportProjectToMp4(
                    context = getApplication(),
                    project = proj,
                    resolution = resolution,
                    fpsOption = fps,
                    fastMode = fastMode,
                    onProgress = { progress, status ->
                        _exportState.value = ExportUiState.Exporting(progress, status)
                    }
                )
                _exportState.value = ExportUiState.Completed(result)
            } catch (_: CancellationException) {
                _exportState.value = ExportUiState.Idle
            }
        }
    }

    fun cancelExport() {
        exportJob?.cancel()
        exportJob = null
        _exportState.value = ExportUiState.Idle
    }

    fun dismissExportDialog() {
        exportJob?.cancel()
        exportJob = null
        _exportState.value = ExportUiState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        replayStopJob?.cancel()
        playbackJob?.cancel()
        exportJob?.cancel()
        liveAudioEngine.stop()
        liveAudioEngine.clearCache()
        voiceoverRecorder.stopRecording()
    }

    companion object {
        fun computeTotalDurationMs(clips: List<TimelineClip>): Long {
            val maxEnd = clips.maxOfOrNull { it.endMs } ?: return 8_000L
            return maxEnd.coerceIn(1_500L, 600_000L)
        }
    }
}
