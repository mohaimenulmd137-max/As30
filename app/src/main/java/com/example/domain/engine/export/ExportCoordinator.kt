package com.example.domain.engine.export

import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.domain.engine.ExportResult
import com.example.domain.engine.VideoExportEngine
import com.example.domain.model.StudioProject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

sealed interface ExportCoordinatorState {
    data object Idle : ExportCoordinatorState
    data class Running(val snapshot: ExportProgressSnapshot) : ExportCoordinatorState
    data class Finished(val result: ExportResult) : ExportCoordinatorState
}

/**
 * Process-scoped export coordinator decoupling video rendering from Activity/ViewModel lifecycles
 * so exports continue reliably in the background via VideoExportForegroundService.
 */
object ExportCoordinator {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val isBusy = AtomicBoolean(false)
    private val pausedFlag = AtomicBoolean(false)
    private var activeJob: Job? = null

    private val _state = MutableStateFlow<ExportCoordinatorState>(ExportCoordinatorState.Idle)
    val state: StateFlow<ExportCoordinatorState> = _state.asStateFlow()

    private val _recoveryRecord = MutableStateFlow<ExportRecoveryRecord?>(null)
    val recoveryRecord: StateFlow<ExportRecoveryRecord?> = _recoveryRecord.asStateFlow()

    private var lastSubmittedProject: StudioProject? = null
    private var lastSubmittedConfig: ExportConfiguration? = null

    fun refreshRecoveryState(context: Context) {
        if (isBusy.get()) return
        val existing = ExportRecoveryStore.loadRecord(context)
        _recoveryRecord.value = existing
    }

    fun dismissRecoveryAndClean(context: Context) {
        ExportRecoveryStore.cleanupAbandonedArtifactsAndClear(context)
        _recoveryRecord.value = null
    }

    fun startExport(
        context: Context,
        project: StudioProject,
        config: ExportConfiguration
    ) {
        if (!isBusy.compareAndSet(false, true)) return
        val appCtx = context.applicationContext
        lastSubmittedProject = project
        lastSubmittedConfig = config
        pausedFlag.set(false)
        _recoveryRecord.value = null

        startForegroundExportService(appCtx, project.name, config)

        activeJob = scope.launch {
            try {
                val result = VideoExportEngine.exportProjectStreaming(
                    context = appCtx,
                    project = project,
                    requestedConfig = config,
                    pauseCheck = { pausedFlag.get() },
                    onSnapshot = { snap ->
                        _state.value = ExportCoordinatorState.Running(snap)
                        VideoExportForegroundService.updateNotificationProgress(appCtx, snap)
                    }
                )
                _state.value = ExportCoordinatorState.Finished(result)
                VideoExportForegroundService.notifyCompletedAndStop(appCtx, result)
                if (!result.success) {
                    _recoveryRecord.value = ExportRecoveryStore.loadRecord(appCtx)
                }
            } catch (_: CancellationException) {
                _state.value = ExportCoordinatorState.Idle
                VideoExportForegroundService.stopServiceSafely(appCtx)
            } finally {
                isBusy.set(false)
                pausedFlag.set(false)
            }
        }
    }

    fun togglePause() {
        if (!isBusy.get()) return
        val next = !pausedFlag.get()
        pausedFlag.set(next)
        val cur = _state.value
        if (cur is ExportCoordinatorState.Running) {
            _state.value = ExportCoordinatorState.Running(cur.snapshot.copy(isPaused = next))
        }
    }

    fun setPaused(paused: Boolean) {
        if (!isBusy.get()) return
        pausedFlag.set(paused)
    }

    fun cancelExport(context: Context) {
        pausedFlag.set(false)
        activeJob?.cancel()
        activeJob = null
        isBusy.set(false)
        _state.value = ExportCoordinatorState.Idle
        VideoExportForegroundService.stopServiceSafely(context.applicationContext)
    }

    fun retryLastExport(context: Context, fallbackProject: StudioProject?): Boolean {
        val proj = lastSubmittedProject ?: fallbackProject ?: return false
        val rec = ExportRecoveryStore.loadRecord(context)
        val cfg = lastSubmittedConfig ?: if (rec != null) {
            ExportConfiguration(
                resolution = com.example.domain.model.PreviewResolution.entries.firstOrNull { it.name == rec.resolutionName }
                    ?: com.example.domain.model.PreviewResolution.P1080,
                fps = com.example.domain.model.ExportFps.entries.firstOrNull { it.fps == rec.fps }
                    ?: com.example.domain.model.ExportFps.FPS30,
                codec = ExportCodec.entries.firstOrNull { it.name == rec.codecName } ?: ExportCodec.H264,
                quality = ExportQuality.entries.firstOrNull { it.name == rec.qualityName } ?: ExportQuality.HIGH,
                mode = ExportMode.entries.firstOrNull { it.name == rec.modeName } ?: ExportMode.SMART_AUTO
            )
        } else ExportConfiguration()

        ExportRecoveryStore.cleanupAbandonedArtifactsAndClear(context)
        startExport(context, proj, cfg)
        return true
    }

    fun resetToIdle() {
        if (!isBusy.get()) {
            _state.value = ExportCoordinatorState.Idle
        }
    }

    private fun startForegroundExportService(context: Context, title: String, cfg: ExportConfiguration) {
        val intent = Intent(context, VideoExportForegroundService::class.java).apply {
            action = VideoExportForegroundService.ACTION_START
            putExtra(VideoExportForegroundService.EXTRA_TITLE, title)
            putExtra(VideoExportForegroundService.EXTRA_SUBTITLE, "${cfg.resolution.label} · ${cfg.fps.fps}fps · ${cfg.codec.shortLabel}")
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
