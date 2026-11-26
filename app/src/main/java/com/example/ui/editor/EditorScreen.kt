package com.example.ui.editor

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.AspectRatioPreset
import com.example.ui.theme.AmberDocumentary
import com.example.ui.theme.CyanPlayhead
import com.example.ui.theme.EmeraldAudio
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun EditorScreen(
    projectId: String,
    viewModel: EditorViewModel,
    onNavigateBack: () -> Unit
) {
    LaunchedEffect(projectId) {
        viewModel.loadProject(projectId)
    }

    BackHandler { onNavigateBack() }

    val project by viewModel.project.collectAsStateWithLifecycle()
    val selectedClipId by viewModel.selectedClipId.collectAsStateWithLifecycle()
    val playheadMs by viewModel.playheadMs.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val autoSaveLabel by viewModel.autoSaveLabel.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()
    val isBangla by viewModel.isBanglaUi.collectAsStateWithLifecycle()
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()

    var showExportDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Zero-Permission Android Photo Picker for Video/Photo clips
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val mime = context.contentResolver.getType(uri) ?: ""
            val isVideo = mime.startsWith("video/")
            viewModel.addMediaClip(uri, isVideo, if (isVideo) "Imported Video" else "Imported Photo")
        }
    }

    val overlayImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.addGraphicOverlayUri(uri, "Graphic Overlay")
        }
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.addAudioClip(uri, "Music Track", isVoiceover = false)
        }
    }

    val currentProject = project
    if (currentProject == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(StudioObsidian),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = CyanPlayhead)
        }
        return
    }

    val selectedClip = remember(currentProject.clips, selectedClipId) {
        currentProject.clips.firstOrNull { it.id == selectedClipId }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioObsidian)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // 1. Top Studio Bar (Back, Project Name + AutoSave, Undo/Redo, 720p/1080p/4K toggle, Export)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(StudioSurface)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.testTag("editor_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Hub",
                        tint = TextPrimary
                    )
                }
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = currentProject.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = null,
                            tint = if (autoSaveLabel == "Saved") EmeraldAudio else AmberDocumentary,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = autoSaveLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { viewModel.undo() },
                    enabled = canUndo,
                    modifier = Modifier.size(38.dp).testTag("undo_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo",
                        tint = if (canUndo) TextPrimary else TextMuted.copy(alpha = 0.4f),
                        modifier = Modifier.size(19.dp)
                    )
                }
                IconButton(
                    onClick = { viewModel.redo() },
                    enabled = canRedo,
                    modifier = Modifier.size(38.dp).testTag("redo_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Redo,
                        contentDescription = "Redo",
                        tint = if (canRedo) TextPrimary else TextMuted.copy(alpha = 0.4f),
                        modifier = Modifier.size(19.dp)
                    )
                }

                // 720p / 1080p / 4K preview toggle
                Surface(
                    onClick = { viewModel.cyclePreviewResolution() },
                    color = StudioSurfaceElevated,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder),
                    modifier = Modifier.padding(horizontal = 4.dp).testTag("resolution_toggle_pill")
                ) {
                    Text(
                        text = currentProject.previewResolution.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanPlayhead,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }

                Button(
                    onClick = { showExportDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanPlayhead,
                        contentColor = StudioObsidian
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(36.dp).testTag("export_button")
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isBangla) "এক্সপোর্ট" else "Export",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 2. Interactive WYSIWYG Preview Canvas
        PreviewCanvas(
            project = currentProject,
            playheadMs = playheadMs,
            selectedClipId = selectedClipId,
            onSelectClip = { viewModel.selectClip(it) },
            onDirectGesture = { id, dx, dy, scale, rot ->
                viewModel.updateDirectCanvasGesture(id, dx, dy, scale, rot)
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.40f)
        )

        // 2b. Precision Transport & Timecode Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(StudioSurface)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${formatTimecode(playheadMs)} / ${formatTimecode(currentProject.totalDurationMs)}",
                style = MaterialTheme.typography.labelMedium,
                color = CyanPlayhead
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = { viewModel.stepFrame(-500L) },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(Icons.Default.FastRewind, contentDescription = "Rewind", tint = TextSecondary, modifier = Modifier.size(20.dp))
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(CyanPlayhead)
                        .clickable { viewModel.togglePlayback() }
                        .testTag("play_pause_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = StudioObsidian,
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(
                    onClick = { viewModel.stepFrame(500L) },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(Icons.Default.FastForward, contentDescription = "Forward", tint = TextSecondary, modifier = Modifier.size(20.dp))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (selectedClip != null) {
                    Surface(
                        onClick = { viewModel.addKeyframeAtPlayhead(selectedClip.id) },
                        color = StudioSurfaceElevated,
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyanPlayhead.copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Diamond, contentDescription = null, tint = CyanPlayhead, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("+KF", style = MaterialTheme.typography.labelSmall, color = CyanPlayhead)
                        }
                    }
                }
                Surface(
                    onClick = {
                        val entries = AspectRatioPreset.entries
                        val next = entries[(entries.indexOf(currentProject.aspectRatio) + 1) % entries.size]
                        viewModel.setAspectRatio(next)
                    },
                    color = StudioSurfaceElevated,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AspectRatio, contentDescription = "Aspect Ratio", tint = AmberDocumentary, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(currentProject.aspectRatio.label, style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                    }
                }
            }
        }

        // 3. Smart Dynamic Property Panel
        SmartPropertyPanel(
            selectedClip = selectedClip,
            playheadMs = playheadMs,
            isBangla = isBangla,
            voiceoverRecorder = viewModel.voiceoverRecorder,
            onPickMedia = {
                mediaPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
            },
            onPickOverlayImage = {
                overlayImagePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onPickAudioFile = { audioPickerLauncher.launch(arrayOf("audio/*")) },
            onAddText = { tpl -> viewModel.addTextClip(tpl) },
            onAddAdjustment = { viewModel.addAdjustmentLayer() },
            onVoiceoverRecorded = { path, dur ->
                viewModel.addAudioClip(null, "Voiceover Rec", isVoiceover = true, recordedPath = path, durationMs = dur)
            },
            onUpdateClip = { id, fn -> viewModel.updateClip(id, transform = fn) },
            onAddKeyframe = { id -> viewModel.addKeyframeAtPlayhead(id) },
            onSeek = { viewModel.seekTo(it) },
            onSplit = { viewModel.splitSelectedClipAtPlayhead() },
            onDuplicate = { viewModel.duplicateSelectedClip() },
            onDelete = { viewModel.deleteSelectedClip() },
            onCloseSelection = { viewModel.selectClip(null) },
            modifier = Modifier.fillMaxWidth()
        )

        // 4. Multi-Track Timeline (Bottom Pinned Playhead + 6 Stacked Layers)
        MultiTrackTimeline(
            project = currentProject,
            playheadMs = playheadMs,
            selectedClipId = selectedClipId,
            isBangla = isBangla,
            onSeek = { viewModel.seekTo(it) },
            onSelectClip = { viewModel.selectClip(it) },
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.32f)
        )
    }

    if (showExportDialog || exportState !is ExportUiState.Idle) {
        ExportDialog(
            initialResolution = currentProject.previewResolution,
            exportState = exportState,
            isBangla = isBangla,
            onStartExport = { res, fps -> viewModel.startExport(res, fps) },
            onDismiss = {
                showExportDialog = false
                viewModel.dismissExportDialog()
            }
        )
    }
}

private fun formatTimecode(ms: Long): String {
    val clamped = ms.coerceAtLeast(0L)
    val secs = clamped / 1000L
    val mins = secs / 60L
    val remSec = secs % 60L
    val centis = (clamped % 1000L) / 10L
    return "%02d:%02d.%02d".format(mins, remSec, centis)
}
