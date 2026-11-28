package com.example.ui.editor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.domain.engine.MediaProbeHelper
import com.example.domain.engine.export.DeviceCapabilityAnalyzer
import com.example.domain.engine.export.EncoderCapabilityReport
import com.example.domain.engine.export.ExportCodec
import com.example.domain.engine.export.ExportConfiguration
import com.example.domain.engine.export.ExportMode
import com.example.domain.engine.export.ExportQuality
import com.example.domain.engine.export.ExportRecoveryRecord
import com.example.domain.engine.export.ExportStorageManager
import com.example.domain.engine.export.ExportThermalLevel
import com.example.domain.model.ExportFps
import com.example.domain.model.PreviewResolution
import com.example.domain.model.StudioProject
import com.example.ui.theme.AmberDocumentary
import com.example.ui.theme.CoralAction
import com.example.ui.theme.CyanPlayhead
import com.example.ui.theme.EmeraldAudio
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun ExportDialog(
    project: StudioProject,
    capabilityReport: EncoderCapabilityReport?,
    recoveryRecord: ExportRecoveryRecord?,
    exportState: ExportUiState,
    isBangla: Boolean,
    onRefreshCapabilities: () -> Unit,
    onStartExport: (ExportConfiguration) -> Unit,
    onTogglePause: () -> Unit,
    onCancelExport: () -> Unit,
    onRetryExport: () -> Unit,
    onDismissRecovery: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val report = capabilityReport ?: remember(project.aspectRatio) {
        DeviceCapabilityAnalyzer.inspectDevice(context, project.aspectRatio)
    }

    LaunchedEffect(Unit) { onRefreshCapabilities() }

    var selectedMode by remember { mutableStateOf(ExportMode.SMART_AUTO) }
    var selectedRes by remember(project.previewResolution, report) {
        mutableStateOf(DeviceCapabilityAnalyzer.clampResolutionToReport(project.previewResolution, report))
    }
    var selectedFps by remember(selectedRes, report) {
        val allowed = report.supportedFpsByResolution[selectedRes].orEmpty()
        mutableStateOf(if (ExportFps.FPS30 in allowed) ExportFps.FPS30 else (allowed.firstOrNull() ?: ExportFps.FPS30))
    }
    var selectedCodec by remember(report) {
        mutableStateOf(if (ExportCodec.H264 in report.supportedCodecs) ExportCodec.H264 else (report.supportedCodecs.firstOrNull() ?: ExportCodec.H264))
    }
    var selectedQuality by remember { mutableStateOf(ExportQuality.HIGH) }

    val effectiveConfig = remember(selectedMode, selectedRes, selectedFps, selectedCodec, selectedQuality, report, project) {
        val base = ExportConfiguration(selectedRes, selectedFps, selectedCodec, selectedQuality, selectedMode)
        if (selectedMode == ExportMode.SMART_AUTO) {
            DeviceCapabilityAnalyzer.resolveSmartAutoConfig(report, project, base)
        } else base
    }

    val estBitrate = remember(effectiveConfig) {
        DeviceCapabilityAnalyzer.computeTargetBitrate(
            effectiveConfig.resolution, effectiveConfig.fps, effectiveConfig.codec, effectiveConfig.quality, effectiveConfig.mode
        )
    }
    val storageCheck = remember(effectiveConfig, project.totalDurationMs) {
        ExportStorageManager.checkStorageForExport(context, project.totalDurationMs, estBitrate)
    }

    var pendingLaunchConfig by remember { mutableStateOf<ExportConfiguration?>(null) }
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        pendingLaunchConfig?.let { cfg ->
            pendingLaunchConfig = null
            onStartExport(cfg)
        }
    }

    fun launchWithNotificationPermission(cfg: ExportConfiguration) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) {
                pendingLaunchConfig = cfg
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        onStartExport(cfg)
    }

    AlertDialog(
        onDismissRequest = { onDismiss() },
        containerColor = StudioSurfaceElevated,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MovieCreation, contentDescription = null, tint = CyanPlayhead)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = if (isBangla) "অ্যাডাপ্টিভ হার্ডওয়্যার এক্সপোর্ট" else "Adaptive Hardware Export",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = "MP4 · ${report.glesVersion} · RAM ${report.availableRamMb}/${report.totalRamMb} MB",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }
        },
        text = {
            when (exportState) {
                is ExportUiState.Idle -> {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    ) {
                        if (recoveryRecord != null) {
                            Surface(
                                color = CoralAction.copy(alpha = 0.14f),
                                border = BorderStroke(1.dp, CoralAction.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = if (isBangla) {
                                            "পূর্বের অসম্পূর্ণ এক্সপোর্ট পাওয়া গেছে (${recoveryRecord.lastCompletedFrame}/${recoveryRecord.totalFrames} ফ্রেম)"
                                        } else {
                                            "Interrupted Export Found (${recoveryRecord.lastCompletedFrame}/${recoveryRecord.totalFrames} frames)"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = CoralAction,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    recoveryRecord.failureReason?.let { reason ->
                                        Text(reason, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = onRetryExport,
                                            border = BorderStroke(1.dp, EmeraldAudio),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.Refresh, null, tint = EmeraldAudio, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(if (isBangla) "পুনরায় চেষ্টা" else "Retry", color = EmeraldAudio)
                                        }
                                        TextButton(onClick = onDismissRecovery) {
                                            Text(if (isBangla) "পরিষ্কার করুন" else "Clean", color = TextSecondary)
                                        }
                                    }
                                }
                            }
                        }

                        Text(
                            text = if (isBangla) "এক্সপোর্ট মোড" else "Export Mode",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            ExportMode.entries.forEach { mode ->
                                val icon = when (mode) {
                                    ExportMode.SMART_AUTO -> Icons.Default.AutoAwesome
                                    ExportMode.FAST_EXPORT -> Icons.Default.Bolt
                                    ExportMode.HIGH_QUALITY -> Icons.Default.HighQuality
                                }
                                FilterChip(
                                    selected = selectedMode == mode,
                                    onClick = { selectedMode = mode },
                                    leadingIcon = { Icon(icon, null, modifier = Modifier.size(15.dp)) },
                                    label = { Text(if (isBangla) mode.labelBn else mode.labelEn) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = if (mode == ExportMode.SMART_AUTO) EmeraldAudio else AmberDocumentary,
                                        selectedLabelColor = StudioObsidian,
                                        selectedLeadingIconColor = StudioObsidian,
                                        labelColor = TextSecondary
                                    ),
                                    modifier = Modifier.testTag(
                                        when (mode) {
                                            ExportMode.SMART_AUTO -> "export_mode_smart_auto"
                                            ExportMode.FAST_EXPORT -> "export_mode_turbo"
                                            ExportMode.HIGH_QUALITY -> "export_mode_hq"
                                        }
                                    )
                                )
                            }
                        }

                        if (selectedMode == ExportMode.SMART_AUTO) {
                            Surface(
                                color = EmeraldAudio.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, EmeraldAudio.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(
                                        text = if (isBangla) {
                                            "স্মার্ট অটো নির্বাচিত: ${effectiveConfig.resolution.label} · ${effectiveConfig.fps.fps} FPS · ${effectiveConfig.codec.shortLabel} · ${effectiveConfig.quality.labelBn}"
                                        } else {
                                            "Smart Auto: ${effectiveConfig.resolution.label} · ${effectiveConfig.fps.fps} FPS · ${effectiveConfig.codec.shortLabel} · ${effectiveConfig.quality.labelEn}"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = EmeraldAudio,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = if (isBangla) {
                                            "ডিভাইসের হার্ডওয়্যার, RAM, থার্মাল ও স্টোরেজ অনুযায়ী স্বয়ংক্রিয়ভাবে সেরা সেটিংস ঠিক করা হয়েছে।"
                                        } else {
                                            "Automatically tuned to your device encoder, RAM headroom, thermal budget, and storage."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = if (isBangla) "রেজুলেশন (ডিভাইস সমর্থন অনুযায়ী)" else "Resolution (Capability Verified)",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            ) {
                                PreviewResolution.entries.forEach { res ->
                                    val supported = res in report.supportedResolutions
                                    FilterChip(
                                        enabled = supported,
                                        selected = selectedRes == res,
                                        onClick = {
                                            selectedRes = res
                                            val allowed = report.supportedFpsByResolution[res].orEmpty()
                                            if (selectedFps !in allowed) {
                                                selectedFps = allowed.firstOrNull() ?: ExportFps.FPS30
                                            }
                                        },
                                        label = { Text(if (supported) res.label else "${res.label} ✕") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = CyanPlayhead,
                                            selectedLabelColor = StudioObsidian,
                                            labelColor = TextSecondary
                                        ),
                                        modifier = Modifier.testTag("export_res_${res.label}")
                                    )
                                }
                            }

                            Text(
                                text = if (isBangla) "ফ্রেম রেট (FPS)" else "Frame Rate (FPS)",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            ) {
                                val allowedFps = report.supportedFpsByResolution[selectedRes].orEmpty()
                                ExportFps.entries.forEach { fps ->
                                    val supported = fps in allowedFps
                                    FilterChip(
                                        enabled = supported,
                                        selected = selectedFps == fps,
                                        onClick = { selectedFps = fps },
                                        label = { Text("${fps.fps}") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = AmberDocumentary,
                                            selectedLabelColor = StudioObsidian,
                                            labelColor = TextSecondary
                                        ),
                                        modifier = Modifier.testTag("export_fps_${fps.fps}")
                                    )
                                }
                            }

                            Text(
                                text = if (isBangla) "কোডেক ও কোয়ালিটি" else "Codec & Quality",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            ) {
                                ExportCodec.entries.forEach { codec ->
                                    val supported = codec in report.supportedCodecs
                                    val isHw = if (codec == ExportCodec.H265) report.hasHardwareH265 else report.hasHardwareH264
                                    val hwTag = if (!supported) "✕" else if (isHw) "HW" else "SW"
                                    FilterChip(
                                        enabled = supported,
                                        selected = selectedCodec == codec,
                                        onClick = { selectedCodec = codec },
                                        label = { Text("${codec.shortLabel} ($hwTag)") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = CyanPlayhead,
                                            selectedLabelColor = StudioObsidian,
                                            labelColor = TextSecondary
                                        ),
                                        modifier = Modifier.testTag("export_codec_${codec.shortLabel}")
                                    )
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            ) {
                                ExportQuality.entries.forEach { q ->
                                    FilterChip(
                                        selected = selectedQuality == q,
                                        onClick = { selectedQuality = q },
                                        label = { Text(if (isBangla) q.labelBn.substringBefore(" ") else q.labelEn) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = AmberDocumentary,
                                            selectedLabelColor = StudioObsidian,
                                            labelColor = TextSecondary
                                        ),
                                        modifier = Modifier.testTag("export_quality_${q.name}")
                                    )
                                }
                            }
                        }

                        Surface(
                            color = StudioSurface,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, if (storageCheck.hasEnoughSpace) CyanPlayhead.copy(alpha = 0.25f) else CoralAction),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                val sec = project.totalDurationMs / 1000f
                                val mbps = estBitrate / 1_000_000f
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Storage, null, tint = if (storageCheck.hasEnoughSpace) CyanPlayhead else CoralAction, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${"%.1f".format(mbps)} Mbps · ${"%.1f".format(sec)}s · ~${ExportStorageManager.formatBytesReadable(storageCheck.estimatedOutputBytes)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (storageCheck.hasEnoughSpace) CyanPlayhead else CoralAction
                                    )
                                }
                                Text(
                                    text = storageCheck.messageBn,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (storageCheck.hasEnoughSpace) TextSecondary else CoralAction
                                )
                            }
                        }
                    }
                }

                is ExportUiState.Exporting -> {
                    val snap = exportState.snapshot
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = exportState.status,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                        LinearProgressIndicator(
                            progress = { exportState.progress.coerceIn(0f, 1f) },
                            color = if (snap?.isPaused == true) AmberDocumentary else CyanPlayhead,
                            trackColor = StudioSurface,
                            modifier = Modifier.fillMaxWidth().height(8.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${(exportState.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.titleLarge,
                                color = AmberDocumentary,
                                fontWeight = FontWeight.Bold
                            )
                            if (snap != null) {
                                Text(
                                    text = if (isBangla) {
                                        "সময়: ${MediaProbeHelper.formatDurationReadable(snap.elapsedMs)} · বাকি: ${MediaProbeHelper.formatDurationReadable(snap.estimatedRemainingMs)}"
                                    } else {
                                        "Elapsed: ${MediaProbeHelper.formatDurationReadable(snap.elapsedMs)} · ETA: ${MediaProbeHelper.formatDurationReadable(snap.estimatedRemainingMs)}"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = CyanPlayhead
                                )
                            }
                        }

                        if (snap != null) {
                            Surface(
                                color = StudioSurface,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "${snap.resolutionLabel} · ${snap.fps} FPS · ${snap.encoderInfo}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = if (isBangla) {
                                            "ফাইল সাইজ: ${ExportStorageManager.formatBytesReadable(snap.outputBytesWritten)} / ~${ExportStorageManager.formatBytesReadable(snap.estimatedTotalBytes)}"
                                        } else {
                                            "Output Size: ${ExportStorageManager.formatBytesReadable(snap.outputBytesWritten)} / ~${ExportStorageManager.formatBytesReadable(snap.estimatedTotalBytes)}"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = EmeraldAudio
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Memory, null, tint = CyanPlayhead, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = "${snap.memoryTier.labelBn} (${snap.availableRamMb}MB)",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TextSecondary
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            val tCol = if (snap.thermalLevel == ExportThermalLevel.NORMAL) EmeraldAudio else CoralAction
                                            Icon(Icons.Default.Thermostat, null, tint = tCol, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = if (isBangla) snap.thermalLevel.labelBn else snap.thermalLevel.labelEn,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = tCol
                                            )
                                        }
                                    }
                                    Text(
                                        text = if (isBangla) {
                                            "অ্যাপ মিনিমাইজ করলেও ব্যাকগ্রাউন্ড সার্ভিসে এক্সপোর্ট চলতে থাকবে।"
                                        } else {
                                            "Background foreground service keeps rendering even if minimized."
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onTogglePause,
                                    border = BorderStroke(1.dp, AmberDocumentary),
                                    modifier = Modifier.weight(1f).testTag("pause_resume_export_btn")
                                ) {
                                    Icon(
                                        if (snap.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                        null,
                                        tint = AmberDocumentary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (snap.isPaused) (if (isBangla) "চালু করুন" else "Resume") else (if (isBangla) "বিরতি" else "Pause"),
                                        color = AmberDocumentary
                                    )
                                }
                            }
                        }
                    }
                }

                is ExportUiState.Completed -> {
                    val res = exportState.result
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = if (res.success) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = if (res.success) EmeraldAudio else CoralAction,
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            text = if (res.success) {
                                if (isBangla) "ভিডিও সফলভাবে গ্যালারিতে সেভ হয়েছে!" else "Video Verified & Saved to Gallery!"
                            } else {
                                res.errorCategory?.let { if (isBangla) it.titleBn else it.titleEn }
                                    ?: (if (isBangla) "এক্সপোর্ট ব্যর্থ হয়েছে" else "Export Failed")
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        if (res.success) {
                            Text(
                                text = "${ExportStorageManager.formatBytesReadable(res.fileSizeBytes)} · ${MediaProbeHelper.formatDurationReadable(res.elapsedMs)} · ${res.encoderUsed}",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyanPlayhead
                            )
                            Text(
                                text = res.savedLocationLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = EmeraldAudio
                            )
                            val uri = res.outputUri
                            if (uri != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            runCatching {
                                                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                                    setDataAndType(uri, "video/mp4")
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(Intent.createChooser(viewIntent, if (isBangla) "ভিডিও প্লে করুন" else "Play Video"))
                                            }
                                        },
                                        modifier = Modifier.weight(1f).testTag("open_exported_video_btn")
                                    ) {
                                        Icon(Icons.Default.PlayArrow, null, tint = CyanPlayhead, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(if (isBangla) "দেখুন" else "Watch", color = CyanPlayhead)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            runCatching {
                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "video/mp4"
                                                    putExtra(Intent.EXTRA_STREAM, uri)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(Intent.createChooser(shareIntent, if (isBangla) "ভিডিও শেয়ার করুন" else "Share Video"))
                                            }
                                        },
                                        modifier = Modifier.weight(1f).testTag("share_exported_video_btn")
                                    ) {
                                        Icon(Icons.Default.Share, null, tint = AmberDocumentary, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(if (isBangla) "শেয়ার" else "Share", color = AmberDocumentary)
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = res.errorMessage ?: "Export failed",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            OutlinedButton(
                                onClick = onRetryExport,
                                border = BorderStroke(1.dp, AmberDocumentary),
                                modifier = Modifier.testTag("retry_export_button")
                            ) {
                                Icon(Icons.Default.Refresh, null, tint = AmberDocumentary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isBangla) "আবার চেষ্টা করুন" else "Safe Retry", color = AmberDocumentary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (exportState) {
                is ExportUiState.Idle -> {
                    Button(
                        enabled = storageCheck.hasEnoughSpace,
                        onClick = { launchWithNotificationPermission(effectiveConfig) },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanPlayhead, contentColor = StudioObsidian),
                        modifier = Modifier.testTag("start_export_button")
                    ) {
                        Text(if (isBangla) "রেন্ডার শুরু করুন" else "Start Export", fontWeight = FontWeight.Bold)
                    }
                }
                is ExportUiState.Exporting -> {
                    TextButton(
                        onClick = onCancelExport,
                        modifier = Modifier.testTag("cancel_export_button")
                    ) {
                        Text(if (isBangla) "রেন্ডার বাতিল" else "Cancel Export", color = CoralAction)
                    }
                }
                is ExportUiState.Completed -> {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldAudio, contentColor = StudioObsidian)
                    ) {
                        Text(if (isBangla) "সম্পন্ন" else "Done", fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        dismissButton = {
            when (exportState) {
                is ExportUiState.Idle -> {
                    TextButton(onClick = onDismiss) {
                        Text(if (isBangla) "বাতিল" else "Cancel", color = TextSecondary)
                    }
                }
                is ExportUiState.Exporting -> {
                    TextButton(onClick = onDismiss) {
                        Text(if (isBangla) "ব্যাকগ্রাউন্ডে রাখুন" else "Hide (Keep Running)", color = TextSecondary)
                    }
                }
                is ExportUiState.Completed -> Unit
            }
        }
    )
}
