package com.example.ui.editor

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ProjectRepository
import com.example.data.local.StudioDatabase
import com.example.data.local.StudioPreferences
import com.example.domain.engine.AudioSynthEngine
import com.example.domain.engine.ExportResult
import com.example.domain.engine.SpeedRampEngine
import com.example.domain.engine.VideoExportEngine
import com.example.domain.engine.VoiceoverRecorder
import com.example.domain.model.AspectRatioPreset
import com.example.domain.model.AudioConfig
import com.example.domain.model.BlendModeOption
import com.example.domain.model.ExportFps
import com.example.domain.model.Keyframe
import com.example.domain.model.LowerThirdTemplate
import com.example.domain.model.LumetriColorGrade
import com.example.domain.model.MaskConfig
import com.example.domain.model.MediaSource
import com.example.domain.model.PresetCatalog
import com.example.domain.model.PreviewResolution
import com.example.domain.model.SpeedRampProfile
import com.example.domain.model.StudioFontOption
import com.example.domain.model.StudioProject
import com.example.domain.model.TextLayerConfig
import com.example.domain.model.TimelineClip
import com.example.domain.model.TrackType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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

    private val _project = MutableStateFlow<StudioProject?>(null)
    val project: StateFlow<StudioProject?> = _project.asStateFlow()

    private val _selectedClipId = MutableStateFlow<String?>(null)
    val selectedClipId: StateFlow<String?> = _selectedClipId.asStateFlow()

    private val _playheadMs = MutableStateFlow(0L)
    val playheadMs: StateFlow<Long> = _playheadMs.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

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
    private var playbackJob: Job? = null
    private var saveJob: Job? = null
    private var lastTriggeredSfxClipId: String? = null

    fun loadProject(projectId: String) {
        if (_project.value?.id == projectId) return
        viewModelScope.launch {
            val loaded = repository.getProject(projectId) ?: return@launch
            undoStack.clear()
            redoStack.clear()
            _canUndo.value = false
            _canRedo.value = false
            _project.value = loaded
            _selectedClipId.value = loaded.clips.firstOrNull()?.id
            _playheadMs.value = 0L
        }
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
        val recalculatedDuration = maxOf(
            8_000L,
            updated.clips.maxOfOrNull { it.endMs } ?: 12_000L
        )
        val normalized = updated.copy(
            totalDurationMs = recalculatedDuration,
            lastEditedAt = System.currentTimeMillis()
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
        _playheadMs.value = ms.coerceIn(0L, dur)
    }

    fun stepFrame(deltaMs: Long) {
        _isPlaying.value = false
        playbackJob?.cancel()
        seekTo(_playheadMs.value + deltaMs)
    }

    fun togglePlayback() {
        if (_isPlaying.value) {
            _isPlaying.value = false
            playbackJob?.cancel()
        } else {
            val proj = _project.value ?: return
            if (_playheadMs.value >= proj.totalDurationMs) {
                _playheadMs.value = 0L
            }
            _isPlaying.value = true
            lastTriggeredSfxClipId = null
            playbackJob = viewModelScope.launch {
                val frameStepMs = 33L
                while (isActive && _isPlaying.value) {
                    val activeProj = _project.value ?: break
                    val nowMs = _playheadMs.value

                    // Check active video speed ramp multiplier
                    val activeVideo = activeProj.clips.firstOrNull {
                        it.trackType == TrackType.VIDEO && nowMs in it.startMs..it.endMs
                    }
                    val speedMult = if (activeVideo != null && activeVideo.durationMs > 0L) {
                        val norm = (nowMs - activeVideo.startMs).toFloat() / activeVideo.durationMs.toFloat()
                        SpeedRampEngine.evaluateSpeedAt(activeVideo.speedRamp, norm)
                    } else 1f

                    // Trigger locked SFX when playhead enters clip start
                    activeProj.clips.forEach { clip ->
                        val sfxId = clip.lockedSfxId
                        if (sfxId != null && abs(nowMs - clip.startMs) < 40L && lastTriggeredSfxClipId != clip.id) {
                            lastTriggeredSfxClipId = clip.id
                            PresetCatalog.sfxPresets.firstOrNull { it.id == sfxId }?.let { preset ->
                                AudioSynthEngine.previewSfx(preset, clip.audioConfig.volume)
                            }
                        }
                    }

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
        val clip = proj.clips.firstOrNull { it.id == clipId } ?: return
        val localMs = (_playheadMs.value - clip.startMs).coerceIn(0L, clip.durationMs)

        // If clip has keyframes, update nearest keyframe within 350ms or update base transform
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
                opacity = clip.opacity
            )).sortedBy { it.timeOffsetMs }
        }
        updateClip(clipId) { it.copy(keyframes = updatedKeyframes) }
    }

    fun addMediaClip(uri: Uri, isVideo: Boolean, label: String) {
        val proj = _project.value ?: return
        val videoClips = proj.clips.filter { it.trackType == TrackType.VIDEO }
        val startAt = videoClips.maxOfOrNull { it.endMs } ?: 0L
        val clip = TimelineClip(
            trackType = TrackType.VIDEO,
            title = label.take(24),
            startMs = startAt,
            durationMs = 5_000L,
            mediaSource = MediaSource(uriString = uri.toString(), isVideo = isVideo, label = label)
        )
        commitProject(proj.copy(clips = proj.clips + clip))
        _selectedClipId.value = clip.id
        _playheadMs.value = clip.startMs + 200L
    }

    fun addGraphicOverlayUri(uri: Uri, label: String) {
        val proj = _project.value ?: return
        val clip = TimelineClip(
            trackType = TrackType.GRAPHICS,
            title = label.take(22),
            startMs = _playheadMs.value,
            durationMs = 4_500L,
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
        val clip = TimelineClip(
            trackType = if (isDoc) TrackType.GRAPHICS else TrackType.TEXT,
            title = if (isDoc) template.labelBn else "বাংলা টেক্সট",
            startMs = _playheadMs.value,
            durationMs = 4_500L,
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

    fun addAdjustmentLayer() {
        val proj = _project.value ?: return
        val clip = TimelineClip(
            trackType = TrackType.ADJUSTMENT,
            title = "Lumetri Adjustment",
            startMs = _playheadMs.value,
            durationMs = 5_000L,
            colorGrade = PresetCatalog.lumetriPresets[1].grade
        )
        commitProject(proj.copy(clips = proj.clips + clip))
        _selectedClipId.value = clip.id
    }

    fun addAudioClip(uri: Uri?, label: String, isVoiceover: Boolean, recordedPath: String? = null, durationMs: Long = 6_000L) {
        val proj = _project.value ?: return
        val clip = TimelineClip(
            trackType = if (isVoiceover) TrackType.VOICEOVER else TrackType.MUSIC,
            title = label,
            startMs = _playheadMs.value,
            durationMs = durationMs.coerceAtLeast(1_500L),
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

    fun splitSelectedClipAtPlayhead() {
        val proj = _project.value ?: return
        val id = _selectedClipId.value ?: return
        val clip = proj.clips.firstOrNull { it.id == id } ?: return
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

    fun duplicateSelectedClip() {
        val proj = _project.value ?: return
        val id = _selectedClipId.value ?: return
        val clip = proj.clips.firstOrNull { it.id == id } ?: return
        val clone = clip.copy(
            id = UUID.randomUUID().toString(),
            startMs = clip.endMs + 150L,
            title = "${clip.title} *"
        )
        commitProject(proj.copy(clips = proj.clips + clone))
        _selectedClipId.value = clone.id
    }

    fun deleteSelectedClip() {
        val proj = _project.value ?: return
        val id = _selectedClipId.value ?: return
        val remaining = proj.clips.filterNot { it.id == id }
        commitProject(proj.copy(clips = remaining))
        _selectedClipId.value = remaining.firstOrNull()?.id
    }

    fun startExport(resolution: PreviewResolution, fps: ExportFps) {
        val proj = _project.value ?: return
        _isPlaying.value = false
        playbackJob?.cancel()
        viewModelScope.launch {
            _exportState.value = ExportUiState.Exporting(0.02f, "Initializing Studio Render...")
            val result = VideoExportEngine.exportProjectToMp4(
                context = getApplication(),
                project = proj,
                resolution = resolution,
                fpsOption = fps,
                onProgress = { progress, status ->
                    _exportState.value = ExportUiState.Exporting(progress, status)
                }
            )
            _exportState.value = ExportUiState.Completed(result)
        }
    }

    fun dismissExportDialog() {
        _exportState.value = ExportUiState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        playbackJob?.cancel()
        voiceoverRecorder.stopRecording()
    }
}
