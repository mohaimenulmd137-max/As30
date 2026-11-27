package com.example.ui.editor

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
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
import com.example.domain.model.ExportFps
import com.example.domain.model.PreviewResolution
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
    initialResolution: PreviewResolution,
    totalDurationMs: Long = 8_000L,
    exportState: ExportUiState,
    isBangla: Boolean,
    onStartExport: (PreviewResolution, ExportFps, Boolean) -> Unit,
    onCancelExport: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedRes by remember(initialResolution) { mutableStateOf(initialResolution) }
    var selectedFps by remember { mutableStateOf(ExportFps.FPS30) }
    var fastMode by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = { if (exportState !is ExportUiState.Exporting) onDismiss() },
        containerColor = StudioSurfaceElevated,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MovieCreation, contentDescription = null, tint = CyanPlayhead)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isBangla) "হাই-স্পিড এক্সপোর্ট (MP4)" else "Pro Hardware Export (MP4)",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
            }
        },
        text = {
            when (exportState) {
                is ExportUiState.Idle -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = if (isBangla) "রেন্ডার স্পিড মোড" else "Render Speed Mode",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = fastMode,
                                onClick = { fastMode = true },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Bolt,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = { Text(if (isBangla) "দ্রুত টার্বো (Fast)" else "Fast Turbo") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldAudio,
                                    selectedLabelColor = StudioObsidian,
                                    selectedLeadingIconColor = StudioObsidian,
                                    labelColor = TextSecondary
                                ),
                                modifier = Modifier.testTag("export_mode_turbo")
                            )
                            FilterChip(
                                selected = !fastMode,
                                onClick = { fastMode = false },
                                label = { Text(if (isBangla) "সিনেমা স্মুথ (HQ)" else "Cinema Smooth") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AmberDocumentary,
                                    selectedLabelColor = StudioObsidian,
                                    labelColor = TextSecondary
                                ),
                                modifier = Modifier.testTag("export_mode_hq")
                            )
                        }

                        Text(
                            text = if (isBangla) "রেজুলেশন সিলেক্ট করুন" else "Resolution",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PreviewResolution.entries.forEach { res ->
                                FilterChip(
                                    selected = selectedRes == res,
                                    onClick = { selectedRes = res },
                                    label = { Text(res.label) },
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
                            text = if (isBangla) "ফ্রেম রেট (FPS)" else "Frame Rate",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ExportFps.entries.forEach { fps ->
                                FilterChip(
                                    selected = selectedFps == fps,
                                    onClick = { selectedFps = fps },
                                    label = { Text(fps.label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AmberDocumentary,
                                        selectedLabelColor = StudioObsidian,
                                        labelColor = TextSecondary
                                    )
                                )
                            }
                        }

                        Surface(
                            color = StudioSurface,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                val sec = (totalDurationMs / 1000f)
                                val bitrateLabel = when (selectedRes) {
                                    PreviewResolution.P720 -> if (fastMode) "5 Mbps · Turbo 720p" else "6.5 Mbps · HD Studio"
                                    PreviewResolution.P1080 -> if (fastMode) "9.5 Mbps · Turbo Full HD" else "12.5 Mbps · Full HD Master"
                                    PreviewResolution.UHD4K -> if (fastMode) "18 Mbps · Fast Ultra HD" else "24 Mbps · Ultra HD Master"
                                }
                                Text(
                                    text = "$bitrateLabel · ${"%.1f".format(sec)}s",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = CyanPlayhead
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isBangla) {
                                        "এক্সপোর্ট শেষে স্বয়ংক্রিয়ভাবে ফোনের গ্যালারিতে (Movies/ChitraFlow Studio) সেভ হবে।"
                                    } else {
                                        "Renders via hardware MediaCodec and saves straight to Gallery (Movies/ChitraFlow Studio)."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }

                is ExportUiState.Exporting -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = exportState.status,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                        LinearProgressIndicator(
                            progress = { exportState.progress.coerceIn(0f, 1f) },
                            color = CyanPlayhead,
                            trackColor = StudioSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                        )
                        Text(
                            text = "${(exportState.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.titleMedium,
                            color = AmberDocumentary
                        )
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
                                if (isBangla) "ভিডিও সফলভাবে গ্যালারিতে সেভ হয়েছে!" else "Video Saved to Gallery!"
                            } else {
                                res.errorMessage ?: "Export failed"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        if (res.success) {
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
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (exportState) {
                is ExportUiState.Idle -> {
                    Button(
                        onClick = { onStartExport(selectedRes, selectedFps, fastMode) },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanPlayhead, contentColor = StudioObsidian),
                        modifier = Modifier.testTag("start_export_button")
                    ) {
                        Text(if (isBangla) "রেন্ডার শুরু করুন" else "Render Video", fontWeight = FontWeight.Bold)
                    }
                }
                is ExportUiState.Exporting -> {
                    TextButton(
                        onClick = onCancelExport,
                        modifier = Modifier.testTag("cancel_export_button")
                    ) {
                        Text(if (isBangla) "রেন্ডার বাতিল" else "Cancel Render", color = CoralAction)
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
            if (exportState is ExportUiState.Idle) {
                TextButton(onClick = onDismiss) {
                    Text(if (isBangla) "বাতিল" else "Cancel", color = TextSecondary)
                }
            }
        }
    )
}
