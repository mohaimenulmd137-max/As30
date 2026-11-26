package com.example.ui.editor

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ProjectRepository
import com.example.data.local.StudioDatabase
import com.example.data.local.StudioPreferences
import com.example.domain.engine.AudioSynthEngine
import com.example.domain.engine.CustomFontManager
import com.example.domain.engine.ExportResult
import com.example.domain.engine.SpeedRampEngine
import com.example.domain.engine.VideoExportEngine
import com.example.domain.engine.VoiceoverRecorder
import com.example.domain.model.AspectRatioPreset
import com.example.domain.model.AudioConfig
import com.example.domain.model.ComparisonChartConfig
import com.example.domain.model.ExportFps
import com.example.domain.model.InvestigativeDocConfig
import com.example.domain.model.InvestigativeToolType
import com.example.domain.model.Keyframe
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
import com.example.domain.model.VfxConfig
import com.example.domain.model.VfxEffectType
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
        val recalculatedDuration = maxOf(
            8_000L,
            updated.clips.maxOfOrNull { it.endMs } ?: 12_000L
        )
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

                    val activeVideo = activeProj.clips.firstOrNull {
                        it.trackType == TrackType.VIDEO && !it.isHidden && nowMs in it.startMs..it.endMs
                    }
                    val speedMult = if (activeVideo != null && activeVideo.durationMs > 0L) {
                        val norm = (nowMs - activeVideo.startMs).toFloat() / activeVideo.durationMs.toFloat()
                        SpeedRampEngine.evaluateSpeedAt(activeVideo.speedRamp, norm)
                    } else 1f

                    activeProj.clips.forEach { clip ->
                        val sfxId = clip.lockedSfxId
                        if (sfxId != null && !clip.isHidden && abs(nowMs - clip.startMs) < 40L && lastTriggeredSfxClipId != clip.id) {
                            lastTriggeredSfxClipId = clip.id
                            PresetCatalog.sfxPresets.firstOrNull { it.id == sfxId }?.let { preset ->
                                AudioSynthEngine.previewSfx(preset, clip.audioConfig.volume * activeProj.masterVolume)
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
        val safeDur = newDurationMs.coerceIn(500L, 120_000L)
        val newClips = proj.clips.map {
            if (it.id == clipId) it.copy(startMs = safeStart, durationMs = safeDur) else it
        }
        commitProject(proj.copy(clips = newClips), pushUndo = pushUndo)
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

    fun importCustomFont(clipId: String, uri: Uri) {
        val imported = CustomFontManager.importFontFromUri(getApplication(), uri) ?: return
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

    fun addVfxClip(effectType: VfxEffectType = VfxEffectType.ANAMORPHIC_FLARE) {
        val proj = _project.value ?: return
        val clip = TimelineClip(
            trackType = TrackType.VFX,
            title = effectType.labelBn,
            startMs = _playheadMs.value,
            durationMs = 5_000L,
            vfxConfig = VfxConfig(effectType = effectType, intensity = 0.72f, speed = 1f)
        )
        commitProject(proj.copy(clips = proj.clips + clip))
        _selectedClipId.value = clip.id
    }

    fun addComparisonChartClip(barCount: Int = 10) {
        val proj = _project.value ?: return
        val safeCount = barCount.coerceIn(2, 60)
        val clip = TimelineClip(
            trackType = TrackType.GRAPHICS,
            title = "তুলনামূলক চার্ট ($safeCount)",
            startMs = _playheadMs.value,
            durationMs = 6_000L,
            inAnimationId = "in_slide_up",
            outAnimationId = "out_fade",
            lockedSfxId = "sfx_whoosh_1",
            chartConfig = ComparisonChartConfig(
                title = "তুলনামূলক ডেটা চার্ট ($safeCount বার)",
                unitSuffix = "%",
                barCount = safeCount,
                isHorizontal = true,
                sortDescending = true,
                animateGrowth = true,
                bars = PresetCatalog.generateDefaultBars(safeCount)
            )
        )
        commitProject(proj.copy(clips = proj.clips + clip))
        _selectedClipId.value = clip.id
    }

    fun addComparisonGridClip(slotCount: Int = 3) {
        val proj = _project.value ?: return
        val safeSlots = slotCount.coerceIn(2, 4)
        val clip = TimelineClip(
            trackType = TrackType.GRAPHICS,
            title = "$safeSlots-স্লট শেপ কম্পারিজন",
            startMs = _playheadMs.value,
            durationMs = 5_500L,
            inAnimationId = "in_zoom_pop",
            outAnimationId = "out_fade",
            comparisonGrid = PresetCatalog.buildDefaultComparisonGrid(safeSlots)
        )
        commitProject(proj.copy(clips = proj.clips + clip))
        _selectedClipId.value = clip.id
    }

    fun updateComparisonSlotMedia(clipId: String, slotId: String, uri: Uri, isVideo: Boolean, label: String) {
        updateClip(clipId) { clip ->
            val grid = clip.comparisonGrid ?: return@updateClip clip
            val updatedSlots = grid.slots.map { slot ->
                if (slot.id == slotId) {
                    slot.copy(
                        mediaSource = MediaSource(uriString = uri.toString(), isVideo = isVideo, label = label)
                    )
                } else slot
            }
            clip.copy(comparisonGrid = grid.copy(slots = updatedSlots))
        }
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
        val clip = TimelineClip(
            trackType = TrackType.GRAPHICS,
            title = toolType.labelBn.take(20),
            startMs = _playheadMs.value,
            durationMs = 5_500L,
            inAnimationId = "in_cinema_reveal",
            outAnimationId = "out_fade",
            lockedSfxId = "sfx_type_1",
            investigativeConfig = defaultConfig
        )
        commitProject(proj.copy(clips = proj.clips + clip))
        _selectedClipId.value = clip.id
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

    fun duplicateSelectedClip() {
        val proj = _project.value ?: return
        val id = _selectedClipId.value ?: return
        val clip = proj.clips.firstOrNull { it.id == id } ?: return
        val clone = clip.copy(
            id = UUID.randomUUID().toString(),
            startMs = clip.endMs + 150L,
            title = "${clip.title} *",
            isLocked = false
        )
        commitProject(proj.copy(clips = proj.clips + clone))
        _selectedClipId.value = clone.id
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
