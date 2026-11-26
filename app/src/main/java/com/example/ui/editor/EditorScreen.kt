package com.example.ui.editor

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.AspectRatioPreset
import com.example.domain.model.InvestigativeToolType
import com.example.domain.model.LowerThirdTemplate
import com.example.domain.model.TrackType
import com.example.domain.model.VfxEffectType
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
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()
    val isBangla by viewModel.isBanglaUi.collectAsStateWithLifecycle()
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()

    var showExportDialog by remember { mutableStateOf(false) }
    var showMediaMenu by remember { mutableStateOf(false) }
    var showAudioMenu by remember { mutableStateOf(false) }
    var showAutoDocDialog by remember { mutableStateOf(false) }
    var showFullscreenPreview by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var activeToolSection by remember { mutableStateOf<ProToolSection?>(null) }
    var pendingSlotTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var pendingFontClipId by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val mime = context.contentResolver.getType(uri) ?: ""
            val isVideo = mime.startsWith("video/")
            viewModel.addMediaClip(uri, isVideo, if (isVideo) "ভিডিও ক্লিপ" else "ছবি ক্লিপ")
        }
    }

    val overlayImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.addGraphicOverlayUri(uri, "গ্রাফিক ওভারলে")
        }
    }

    val comparisonSlotPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        val target = pendingSlotTarget
        pendingSlotTarget = null
        if (uri != null && target != null) {
            val mime = context.contentResolver.getType(uri) ?: ""
            val isVideo = mime.startsWith("video/")
            viewModel.updateComparisonSlotMedia(target.first, target.second, uri, isVideo, "স্লট মিডিয়া")
        }
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.addAudioClip(uri, "ব্যাকগ্রাউন্ড মিউজিক", isVoiceover = false)
        }
    }

    val fontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val targetClipId = pendingFontClipId
        pendingFontClipId = null
        if (uri != null && targetClipId != null) {
            viewModel.importCustomFont(targetClipId, uri)
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.voiceoverRecorder.startRecording { path, dur ->
                viewModel.addAudioClip(null, "ভয়েসওভার রেকর্ড", isVoiceover = true, recordedPath = path, durationMs = dur)
            }
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
            CircularProgressIndicator(color = AmberDocumentary)
        }
        return
    }

    val selectedClip = remember(currentProject.clips, selectedClipId) {
        currentProject.clips.firstOrNull { it.id == selectedClipId }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07090F))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // 1. TOP STUDIO BAR: ← | Title | Undo | Redo | Speed | 1080p v | Tune | এক্সপোর্ট
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.size(36.dp).testTag("editor_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary, modifier = Modifier.size(20.dp))
                }
                Text(
                    text = currentProject.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clickable { showRenameDialog = true }
                        .padding(end = 4.dp)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                IconButton(
                    onClick = { viewModel.undo() },
                    enabled = canUndo,
                    modifier = Modifier.size(32.dp).testTag("undo_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo",
                        tint = if (canUndo) TextPrimary else TextMuted.copy(alpha = 0.35f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = { viewModel.redo() },
                    enabled = canRedo,
                    modifier = Modifier.size(32.dp).testTag("redo_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Redo,
                        contentDescription = "Redo",
                        tint = if (canRedo) TextPrimary else TextMuted.copy(alpha = 0.35f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Amber Speed pill
                Surface(
                    onClick = {
                        activeToolSection = if (activeToolSection == ProToolSection.SPEED) null else ProToolSection.SPEED
                    },
                    color = Color(0xFF1E1A12),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, AmberDocumentary.copy(alpha = 0.55f)),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Speed, contentDescription = "Speed Ramp", tint = AmberDocumentary, modifier = Modifier.size(17.dp))
                    }
                }

                // 1080p v Resolution pill
                Surface(
                    onClick = { viewModel.cyclePreviewResolution() },
                    color = Color(0xFF131926),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, StudioBorder),
                    modifier = Modifier.height(34.dp).testTag("resolution_toggle_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(currentProject.previewResolution.label, fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                    }
                }

                // Tune / Adjust / Settings icon
                Surface(
                    onClick = {
                        activeToolSection = if (activeToolSection == ProToolSection.COLOR) null else ProToolSection.COLOR
                    },
                    color = Color(0xFF131926),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, StudioBorder),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Tune, contentDescription = "Color & Grade", tint = TextPrimary, modifier = Modifier.size(17.dp))
                    }
                }

                // Bright Amber Export Button
                Button(
                    onClick = { showExportDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberDocumentary, contentColor = Color(0xFF0A0C12)),
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(34.dp).testTag("export_button")
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(if (isBangla) "এক্সপোর্ট" else "Export", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }

        // 2. QUICK ADD PILL ROW: মিডিয়া v | অডিও v | T + টেক্সট | সাবটাইটেল
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                QuickCategoryPill(
                    icon = Icons.Default.AddPhotoAlternate,
                    label = if (isBangla) "মিডিয়া" else "Media",
                    hasArrow = true,
                    containerColor = Color(0xFF0C2224),
                    borderColor = Color(0xFF1F7A70),
                    contentColor = Color(0xFF4FD1C5),
                    tag = "pill_media",
                    onClick = { showMediaMenu = true }
                )
                DropdownMenu(
                    expanded = showMediaMenu,
                    onDismissRequest = { showMediaMenu = false },
                    containerColor = StudioSurfaceElevated
                ) {
                    DropdownMenuItem(
                        text = { Text(if (isBangla) "ভিডিও / ছবি যোগ করুন" else "Add Video / Photo", color = TextPrimary) },
                        leadingIcon = { Icon(Icons.Default.AddPhotoAlternate, null, tint = CyanPlayhead) },
                        onClick = {
                            showMediaMenu = false
                            mediaPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (isBangla) "ওভারলে ছবি যোগ করুন" else "Add Overlay Image", color = TextPrimary) },
                        leadingIcon = { Icon(Icons.Default.Layers, null, tint = AmberDocumentary) },
                        onClick = {
                            showMediaMenu = false
                            overlayImagePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (isBangla) "৬০-বার তুলনামূলক চার্ট" else "60-Bar Comparison Chart", color = CyanPlayhead) },
                        leadingIcon = { Icon(Icons.Default.BarChart, null, tint = CyanPlayhead) },
                        onClick = {
                            showMediaMenu = false
                            viewModel.addComparisonChartClip(12)
                            activeToolSection = ProToolSection.OVERLAY_PRO
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (isBangla) "৩/৪ ছবি জ্যামিতিক কম্পারিজন" else "Geometric Media Comparison", color = AmberDocumentary) },
                        leadingIcon = { Icon(Icons.Default.GridView, null, tint = AmberDocumentary) },
                        onClick = {
                            showMediaMenu = false
                            viewModel.addComparisonGridClip(3)
                            activeToolSection = ProToolSection.OVERLAY_PRO
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (isBangla) "অনুসন্ধানী ডকুমেন্টারি টুলস" else "Investigative Doc Tools", color = EmeraldAudio) },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = EmeraldAudio) },
                        onClick = {
                            showMediaMenu = false
                            viewModel.addInvestigativeClip(InvestigativeToolType.NEWSPAPER_LOUPE)
                            activeToolSection = ProToolSection.OVERLAY_PRO
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (isBangla) "অটো ডকুমেন্টারি বিল্ডার ✨" else "Auto Documentary Builder ✨", color = AmberDocumentary, fontWeight = FontWeight.Bold) },
                        leadingIcon = { Icon(Icons.Default.AutoAwesome, null, tint = AmberDocumentary) },
                        onClick = {
                            showMediaMenu = false
                            showAutoDocDialog = true
                        }
                    )
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                QuickCategoryPill(
                    icon = Icons.Default.MusicNote,
                    label = if (isBangla) "অডিও" else "Audio",
                    hasArrow = true,
                    containerColor = Color(0xFF281A0E),
                    borderColor = Color(0xFF9E621C),
                    contentColor = Color(0xFFF6AD55),
                    tag = "pill_audio",
                    onClick = { showAudioMenu = true }
                )
                DropdownMenu(
                    expanded = showAudioMenu,
                    onDismissRequest = { showAudioMenu = false },
                    containerColor = StudioSurfaceElevated
                ) {
                    DropdownMenuItem(
                        text = { Text(if (isBangla) "ব্যাকগ্রাউন্ড মিউজিক (অটো-ডাকিং)" else "Background Music (Auto-Duck)", color = TextPrimary) },
                        leadingIcon = { Icon(Icons.Default.MusicNote, null, tint = AmberDocumentary) },
                        onClick = {
                            showAudioMenu = false
                            audioPickerLauncher.launch(arrayOf("audio/*"))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (isBangla) "ভয়েসওভার রেকর্ড (Mic)" else "Record Voiceover", color = EmeraldAudio) },
                        leadingIcon = { Icon(Icons.Default.Mic, null, tint = EmeraldAudio) },
                        onClick = {
                            showAudioMenu = false
                            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                viewModel.voiceoverRecorder.startRecording { path, dur ->
                                    viewModel.addAudioClip(null, "ভয়েসওভার রেকর্ড", isVoiceover = true, recordedPath = path, durationMs = dur)
                                }
                                activeToolSection = ProToolSection.VOLUME
                            } else {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (isBangla) "৫২+ SFX ও মিক্সার খুলুন" else "Open 52+ SFX & Mixer", color = CyanPlayhead) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = CyanPlayhead) },
                        onClick = {
                            showAudioMenu = false
                            activeToolSection = ProToolSection.VOLUME
                        }
                    )
                }
            }

            Box(modifier = Modifier.weight(1.08f)) {
                QuickCategoryPill(
                    icon = null,
                    label = if (isBangla) "T + টেক্সট" else "T + Text",
                    hasArrow = false,
                    containerColor = Color(0xFF15203E),
                    borderColor = Color(0xFF34569E),
                    contentColor = Color(0xFF8AB4F8),
                    tag = "pill_text",
                    onClick = {
                        viewModel.addTextClip(LowerThirdTemplate.NONE)
                        activeToolSection = ProToolSection.TEXT_FONT
                    }
                )
            }

            Box(modifier = Modifier.weight(1.05f)) {
                QuickCategoryPill(
                    icon = Icons.Default.Subtitles,
                    label = if (isBangla) "সাবটাইটেল" else "Subtitles",
                    hasArrow = false,
                    containerColor = Color(0xFF0E2534),
                    borderColor = Color(0xFF1F6B8C),
                    contentColor = Color(0xFF56CCF2),
                    tag = "pill_subtitle",
                    onClick = {
                        viewModel.addTextClip(LowerThirdTemplate.DOCUMENTARY_DATE_CARD)
                        activeToolSection = ProToolSection.TEXT_FONT
                    }
                )
            }
        }

        // 3. LIVE STATUS & MASTER AUDIO STRIP: ভিডিও: অন | এডিট: অন | ▶ এ পর্যন্ত (0.0s) | Speaker + Amber Slider
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusTogglePill(
                icon = if (currentProject.videoTrackPreviewOn) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                label = if (currentProject.videoTrackPreviewOn) {
                    if (isBangla) "ভিডিও: অন" else "Vid: ON"
                } else {
                    if (isBangla) "ভিডিও: অফ" else "Vid: OFF"
                },
                active = currentProject.videoTrackPreviewOn,
                activeContainer = Color(0xFF0C261F),
                activeBorder = EmeraldAudio.copy(alpha = 0.55f),
                activeContent = EmeraldAudio,
                onClick = { viewModel.toggleVideoPreview() }
            )

            StatusTogglePill(
                icon = Icons.Default.Layers,
                label = if (currentProject.editModeOn) {
                    if (isBangla) "এডিট: অন" else "Edit: ON"
                } else {
                    if (isBangla) "এডিট: অফ" else "Edit: OFF"
                },
                active = currentProject.editModeOn,
                activeContainer = Color(0xFF281F10),
                activeBorder = AmberDocumentary.copy(alpha = 0.55f),
                activeContent = AmberDocumentary,
                onClick = { viewModel.toggleEditMode() }
            )

            Surface(
                onClick = { viewModel.togglePlayback() },
                color = Color(0xFF112036),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFF284874)),
                modifier = Modifier.height(28.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF6BB5FF), modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = if (isBangla) "এ পর্যন্ত (%.1fs)".format(playheadMs / 1000f) else "At %.1fs".format(playheadMs / 1000f),
                        fontSize = 10.sp,
                        color = Color(0xFF9ECBFF),
                        maxLines = 1
                    )
                }
            }

            // Master Volume & Auto-Ducking Pill Slider
            Surface(
                color = Color(0xFF131926),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, StudioBorder),
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Master Audio", tint = CyanPlayhead, modifier = Modifier.size(13.dp))
                    Slider(
                        value = currentProject.masterVolume,
                        onValueChange = { viewModel.setMasterVolume(it) },
                        valueRange = 0f..1.5f,
                        colors = SliderDefaults.colors(
                            thumbColor = AmberDocumentary,
                            activeTrackColor = AmberDocumentary,
                            inactiveTrackColor = Color(0xFF273044)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 4. PREVIEW CANVAS (with Corner Brackets & Safe Zone Grid)
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
                .weight(0.38f)
        )

        // 5. TRANSPORT & RATIO BAR: [⤢] [▦]   [⏮] [▶/❚❚] [⏭]   [9:16 | 16:9 | 1:1]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    onClick = { showFullscreenPreview = true },
                    color = Color(0xFF151C2B),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, StudioBorder),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Fullscreen, contentDescription = "Fullscreen Preview", tint = TextPrimary, modifier = Modifier.size(19.dp))
                    }
                }
                Surface(
                    onClick = { viewModel.toggleSafeZoneGuides() },
                    color = if (currentProject.showSafeZoneGuides) Color(0xFF1F273B) else Color(0xFF151C2B),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, if (currentProject.showSafeZoneGuides) AmberDocumentary else StudioBorder),
                    modifier = Modifier.size(36.dp).testTag("safe_zone_toggle")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.GridOn,
                            contentDescription = "Safe Zone Grid",
                            tint = if (currentProject.showSafeZoneGuides) AmberDocumentary else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconButton(onClick = { viewModel.stepFrame(-500L) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Back 0.5s", tint = TextPrimary, modifier = Modifier.size(22.dp))
                }
                Surface(
                    onClick = { viewModel.togglePlayback() },
                    color = Color(0xFFF3F6FC),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(width = 48.dp, height = 40.dp).testTag("play_pause_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color(0xFF0A0C12),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                IconButton(onClick = { viewModel.stepFrame(500L) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Forward 0.5s", tint = TextPrimary, modifier = Modifier.size(22.dp))
                }
            }

            // Segmented Aspect Ratio Pill: 9:16 | 16:9 | 1:1
            Surface(
                color = Color(0xFF121826),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, StudioBorder)
            ) {
                Row(
                    modifier = Modifier.padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AspectRatioPreset.entries.forEach { ratio ->
                        val active = currentProject.aspectRatio == ratio
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (active) Color(0xFF423415) else Color.Transparent)
                                .border(
                                    width = if (active) 1.dp else 0.dp,
                                    color = if (active) AmberDocumentary else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { viewModel.setAspectRatio(ratio) }
                                .padding(horizontal = 7.dp, vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = ratio.label,
                                fontSize = 11.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                color = if (active) AmberDocumentary else TextSecondary
                            )
                        }
                    }
                }
            }
        }

        // Optional Expandable Inspector Sheet when any bottom tool is opened
        AnimatedVisibility(visible = activeToolSection != null) {
            activeToolSection?.let { section ->
                ExpandableInspectorSheet(
                    section = section,
                    selectedClip = selectedClip,
                    playheadMs = playheadMs,
                    isBangla = isBangla,
                    voiceoverRecorder = viewModel.voiceoverRecorder,
                    onUpdateClip = { id, fn -> viewModel.updateClip(id, transform = fn) },
                    onAddKeyframe = { id -> viewModel.addKeyframeAtPlayhead(id) },
                    onSeek = { viewModel.seekTo(it) },
                    onAddVfxClip = { eff -> viewModel.addVfxClip(eff) },
                    onAddChart = { cnt -> viewModel.addComparisonChartClip(cnt) },
                    onAddComparisonGrid = { slots -> viewModel.addComparisonGridClip(slots) },
                    onAddInvestigativeTool = { t -> viewModel.addInvestigativeClip(t) },
                    onPickSlotMedia = { cId, sId ->
                        pendingSlotTarget = cId to sId
                        comparisonSlotPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    },
                    onPickAudioFile = { audioPickerLauncher.launch(arrayOf("audio/*")) },
                    onImportCustomFont = { clipId ->
                        pendingFontClipId = clipId
                        fontPickerLauncher.launch(arrayOf("font/*", "application/x-font-ttf", "application/x-font-otf", "application/octet-stream", "*/*"))
                    },
                    onVoiceoverRecorded = { path, dur ->
                        viewModel.addAudioClip(null, "ভয়েসওভার রেকর্ড", isVoiceover = true, recordedPath = path, durationMs = dur)
                    },
                    onClose = { activeToolSection = null }
                )
            }
        }

        // 6. SPLIT MULTI-TRACK TIMELINE (Left Reorderable Layers + Zoom/Fit + Right Draggable/Trimmable Timeline)
        MultiTrackTimeline(
            project = currentProject,
            playheadMs = playheadMs,
            selectedClipId = selectedClipId,
            isBangla = isBangla,
            onSeek = { viewModel.seekTo(it) },
            onSelectClip = { viewModel.selectClip(it) },
            onMoveClip = { id, startMs -> viewModel.moveClipTime(id, startMs) },
            onTrimClip = { id, startMs, durMs -> viewModel.trimClip(id, startMs, durMs) },
            onMoveTrackOrder = { type, delta -> viewModel.moveTrackOrder(type, delta) },
            onToggleTrackVisibility = { type -> viewModel.toggleTrackVisibility(type) },
            onToggleTrackLock = { type -> viewModel.toggleTrackLock(type) },
            onQuickAddTrack = { trackType ->
                when (trackType) {
                    TrackType.TEXT -> {
                        viewModel.addTextClip(LowerThirdTemplate.NONE)
                        activeToolSection = ProToolSection.TEXT_FONT
                    }
                    TrackType.GRAPHICS -> {
                        viewModel.addComparisonChartClip(12)
                        activeToolSection = ProToolSection.OVERLAY_PRO
                    }
                    TrackType.VFX -> {
                        viewModel.addVfxClip(VfxEffectType.ANAMORPHIC_FLARE)
                        activeToolSection = ProToolSection.TRANSITION_VFX
                    }
                    TrackType.ADJUSTMENT -> {
                        viewModel.addAdjustmentLayer()
                        activeToolSection = ProToolSection.COLOR
                    }
                    TrackType.VIDEO -> mediaPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    TrackType.VOICEOVER -> {
                        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            viewModel.voiceoverRecorder.startRecording { path, dur ->
                                viewModel.addAudioClip(null, "ভয়েসওভার রেকর্ড", isVoiceover = true, recordedPath = path, durationMs = dur)
                            }
                            activeToolSection = ProToolSection.VOLUME
                        } else {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                    TrackType.MUSIC -> audioPickerLauncher.launch(arrayOf("audio/*"))
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(if (activeToolSection == null) 0.34f else 0.22f)
        )

        // 7. BOTTOM 2x6 PRO ACTION GRID (Exact Match to Screenshot)
        BottomActionToolGrid(
            selectedClip = selectedClip,
            activeSection = activeToolSection,
            isBangla = isBangla,
            onSplit = { viewModel.splitSelectedClipAtPlayhead() },
            onDelete = { viewModel.deleteSelectedClip() },
            onDuplicate = { viewModel.duplicateSelectedClip() },
            onToggleLock = { selectedClip?.let { viewModel.toggleClipLock(it.id) } },
            onSelectSection = { sec ->
                activeToolSection = if (activeToolSection == sec) null else sec
            },
            onQuickAddText = { viewModel.addTextClip(LowerThirdTemplate.NONE) }
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

    if (showAutoDocDialog) {
        AutoDocumentaryBuilderDialog(
            isBangla = isBangla,
            onDismiss = { showAutoDocDialog = false },
            onGenerate = { topic, note ->
                showAutoDocDialog = false
                viewModel.generateAutoDocumentaryIntoCurrent(topic, note)
            }
        )
    }

    if (showRenameDialog) {
        var nextName by remember(currentProject.name) { mutableStateOf(currentProject.name) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            containerColor = StudioSurfaceElevated,
            title = { Text(if (isBangla) "প্রজেক্টের নাম" else "Project Title", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = nextName,
                    onValueChange = { nextName = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AmberDocumentary, unfocusedBorderColor = StudioBorder, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.renameProject(nextName)
                        showRenameDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberDocumentary, contentColor = StudioObsidian)
                ) { Text(if (isBangla) "সেভ" else "Save", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text(if (isBangla) "বাতিল" else "Cancel", color = TextSecondary) }
            }
        )
    }

    if (showFullscreenPreview) {
        Dialog(
            onDismissRequest = { showFullscreenPreview = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .padding(12.dp)
            ) {
                PreviewCanvas(
                    project = currentProject,
                    playheadMs = playheadMs,
                    selectedClipId = null,
                    onSelectClip = {},
                    onDirectGesture = { _, _, _, _, _ -> },
                    modifier = Modifier.fillMaxSize().padding(bottom = 54.dp)
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { viewModel.togglePlayback() },
                        colors = ButtonDefaults.buttonColors(containerColor = AmberDocumentary, contentColor = StudioObsidian)
                    ) {
                        Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isPlaying) "বিরতি" else "প্লে করুন", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = { showFullscreenPreview = false },
                        colors = ButtonDefaults.buttonColors(containerColor = StudioSurfaceElevated, contentColor = TextPrimary)
                    ) {
                        Icon(Icons.Default.Close, null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("বন্ধ করুন")
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickCategoryPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    label: String,
    hasArrow: Boolean,
    containerColor: Color,
    borderColor: Color,
    contentColor: Color,
    tag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = containerColor,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (hasArrow) {
                Spacer(modifier = Modifier.width(2.dp))
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = contentColor, modifier = Modifier.size(13.dp))
            }
        }
    }
}

@Composable
private fun StatusTogglePill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    activeContainer: Color,
    activeBorder: Color,
    activeContent: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (active) activeContainer else Color(0xFF151923),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (active) activeBorder else StudioBorder),
        modifier = Modifier.height(28.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = if (active) activeContent else TextMuted, modifier = Modifier.size(13.dp))
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                color = if (active) activeContent else TextMuted,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@Composable
fun AutoDocumentaryBuilderDialog(
    isBangla: Boolean,
    onDismiss: () -> Unit,
    onGenerate: (topic: String, scriptNote: String) -> Unit
) {
    var topic by remember { mutableStateOf("বাংলার অজানা ঐতিহাসিক রহস্য") }
    var scriptNote by remember { mutableStateOf("গোপন নথি ও পরিসংখ্যান বিশ্লেষণ অনুযায়ী অনুসন্ধানী প্রতিবেদন") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StudioSurfaceElevated,
        title = {
            Text(
                text = if (isBangla) "অটো ডকুমেন্টারি স্টোরি বিল্ডার ✨" else "Auto Investigative Doc Builder",
                color = AmberDocumentary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (isBangla) "টপিক ও স্ক্রিপ্ট লিখলেই ম্যাপ রাডার, নথি হাইলাইট, ৬০-বার চার্ট, ৩-স্লট কম্পারিজন ও অটো-ডাকিং মিউজিকসহ সম্পূর্ণ ডকুমেন্টারি টাইমলাইন তৈরি হয়ে যাবে।" else "Enter topic & notes to auto-assemble a full multi-track documentary.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    label = { Text(if (isBangla) "ডকুমেন্টারির শিরোনাম / টপিক" else "Topic") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AmberDocumentary, unfocusedBorderColor = StudioBorder, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = scriptNote,
                    onValueChange = { scriptNote = it },
                    label = { Text(if (isBangla) "মূল অনুসন্ধানী নোট / স্ক্রিপ্ট" else "Investigation Summary") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyanPlayhead, unfocusedBorderColor = StudioBorder, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onGenerate(topic, scriptNote) },
                colors = ButtonDefaults.buttonColors(containerColor = AmberDocumentary, contentColor = StudioObsidian),
                modifier = Modifier.testTag("confirm_auto_doc_btn")
            ) {
                Text(if (isBangla) "ডকুমেন্টারি তৈরি করুন" else "Build Documentary", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isBangla) "বাতিল" else "Cancel", color = TextSecondary)
            }
        }
    )
}
