package com.example.ui.editor

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.engine.AudioSynthEngine
import com.example.domain.engine.VoiceoverRecorder
import com.example.domain.model.AnimationCategory
import com.example.domain.model.BlendModeOption
import com.example.domain.model.KeyframeEasing
import com.example.domain.model.LowerThirdTemplate
import com.example.domain.model.MaskShape
import com.example.domain.model.PresetCatalog
import com.example.domain.model.SfxCategory
import com.example.domain.model.StudioFontOption
import com.example.domain.model.TextLayerConfig
import com.example.domain.model.TimelineClip
import com.example.domain.model.TrackType
import com.example.ui.theme.AmberDocumentary
import com.example.ui.theme.CoralAction
import com.example.ui.theme.CyanPlayhead
import com.example.ui.theme.EmeraldAudio
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletAdjustment

@Composable
fun SmartPropertyPanel(
    selectedClip: TimelineClip?,
    playheadMs: Long,
    isBangla: Boolean,
    voiceoverRecorder: VoiceoverRecorder,
    onPickMedia: () -> Unit,
    onPickOverlayImage: () -> Unit,
    onPickAudioFile: () -> Unit,
    onAddText: (LowerThirdTemplate) -> Unit,
    onAddAdjustment: () -> Unit,
    onVoiceoverRecorded: (String, Long) -> Unit,
    onUpdateClip: (String, (TimelineClip) -> TimelineClip) -> Unit,
    onAddKeyframe: (String) -> Unit,
    onSeek: (Long) -> Unit,
    onSplit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onCloseSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(StudioSurface)
            .border(1.dp, StudioBorder)
    ) {
        if (selectedClip == null) {
            QuickAddStudioDock(
                isBangla = isBangla,
                voiceoverRecorder = voiceoverRecorder,
                onPickMedia = onPickMedia,
                onPickOverlayImage = onPickOverlayImage,
                onPickAudioFile = onPickAudioFile,
                onAddText = onAddText,
                onAddAdjustment = onAddAdjustment,
                onVoiceoverRecorded = onVoiceoverRecorded
            )
        } else {
            SelectedClipInspector(
                clip = selectedClip,
                playheadMs = playheadMs,
                isBangla = isBangla,
                voiceoverRecorder = voiceoverRecorder,
                onUpdateClip = { fn -> onUpdateClip(selectedClip.id, fn) },
                onAddKeyframe = { onAddKeyframe(selectedClip.id) },
                onSeek = onSeek,
                onSplit = onSplit,
                onDuplicate = onDuplicate,
                onDelete = onDelete,
                onClose = onCloseSelection,
                onVoiceoverRecorded = onVoiceoverRecorded
            )
        }
    }
}

@Composable
private fun QuickAddStudioDock(
    isBangla: Boolean,
    voiceoverRecorder: VoiceoverRecorder,
    onPickMedia: () -> Unit,
    onPickOverlayImage: () -> Unit,
    onPickAudioFile: () -> Unit,
    onAddText: (LowerThirdTemplate) -> Unit,
    onAddAdjustment: () -> Unit,
    onVoiceoverRecorded: (String, Long) -> Unit
) {
    val isRecording by voiceoverRecorder.isRecording.collectAsStateWithLifecycle()
    val liveAmp by voiceoverRecorder.liveAmplitude.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var micPermissionDenied by remember { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            micPermissionDenied = false
            voiceoverRecorder.startRecording(onVoiceoverRecorded)
        } else {
            micPermissionDenied = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isBangla) "স্মার্ট অ্যাড ও স্টুডিও টুলস" else "Smart Studio Add Dock",
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary
            )
            if (isRecording) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinearProgressIndicator(
                        progress = { liveAmp },
                        color = CoralAction,
                        trackColor = StudioObsidian,
                        modifier = Modifier.width(64.dp).height(6.dp)
                    )
                    Text("REC", style = MaterialTheme.typography.labelSmall, color = CoralAction)
                }
            } else if (micPermissionDenied) {
                Text(
                    text = if (isBangla) "মাইক অনুমতি প্রয়োজন" else "Mic permission needed",
                    style = MaterialTheme.typography.labelSmall,
                    color = CoralAction
                )
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 2.dp)
        ) {
            item {
                DockActionChip(
                    icon = Icons.Default.Movie,
                    label = if (isBangla) "+ ভিডিও/ছবি" else "+ Media",
                    accent = CoralAction,
                    tag = "add_media_chip",
                    onClick = onPickMedia
                )
            }
            item {
                DockActionChip(
                    icon = Icons.Default.TextFields,
                    label = if (isBangla) "+ বাংলা টেক্সট" else "+ Text",
                    accent = CyanPlayhead,
                    tag = "add_text_chip",
                    onClick = { onAddText(LowerThirdTemplate.NONE) }
                )
            }
            item {
                DockActionChip(
                    icon = Icons.Default.Subtitles,
                    label = if (isBangla) "+ সাল/লোয়ার থার্ড" else "+ Doc Card",
                    accent = AmberDocumentary,
                    tag = "add_doc_card_chip",
                    onClick = { onAddText(LowerThirdTemplate.DOCUMENTARY_DATE_CARD) }
                )
            }
            item {
                DockActionChip(
                    icon = Icons.Default.Image,
                    label = if (isBangla) "+ ওভারলে ছবি" else "+ Overlay",
                    accent = AmberDocumentary,
                    tag = "add_overlay_chip",
                    onClick = onPickOverlayImage
                )
            }
            item {
                DockActionChip(
                    icon = Icons.Default.Tune,
                    label = if (isBangla) "+ অ্যাডজাস্টমেন্ট" else "+ Adjust Layer",
                    accent = VioletAdjustment,
                    tag = "add_adjustment_chip",
                    onClick = onAddAdjustment
                )
            }
            item {
                DockActionChip(
                    icon = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                    label = if (isRecording) {
                        if (isBangla) "থামান" else "Stop Rec"
                    } else {
                        if (isBangla) "+ ভয়েসওভার" else "+ Voiceover"
                    },
                    accent = if (isRecording) CoralAction else EmeraldAudio,
                    tag = "record_voiceover_chip",
                    onClick = {
                        if (isRecording) {
                            voiceoverRecorder.stopRecording()
                        } else {
                            val granted = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                voiceoverRecorder.startRecording(onVoiceoverRecorded)
                            } else {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    }
                )
            }
            item {
                DockActionChip(
                    icon = Icons.Default.MusicNote,
                    label = if (isBangla) "+ মিউজিক" else "+ Music",
                    accent = EmeraldAudio,
                    tag = "add_music_chip",
                    onClick = onPickAudioFile
                )
            }
        }
    }
}

@Composable
private fun DockActionChip(
    icon: ImageVector,
    label: String,
    accent: Color,
    tag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = StudioSurfaceElevated,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
        modifier = Modifier
            .height(54.dp)
            .testTag(tag)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = label, tint = accent, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextPrimary)
        }
    }
}

private enum class InspectorTab(val labelBn: String, val labelEn: String) {
    TEXT_FONT("টেক্সট ও ফন্ট", "Text & Font"),
    MOTION("মোশন (50+)", "Motion (50+)"),
    SFX("সাউন্ড (50+)", "SFX (50+)"),
    KEYFRAME("কি-ফ্রেম ◆", "Keyframes"),
    LUMETRI("কালার গ্রেডিং", "Lumetri"),
    CURVES("RGB কার্ভস", "RGB Curves"),
    SPEED("স্পিড র‍্যাম্প", "Speed Ramp"),
    MASK_BLEND("মাস্ক ও ব্লেন্ড", "Mask & Blend"),
    AUDIO_MIX("অডিও ও ডাকিং", "Mix & Ducking"),
    TRIM("ট্রিম ও সাইজ", "Trim & Transform")
}

@Composable
private fun SelectedClipInspector(
    clip: TimelineClip,
    playheadMs: Long,
    isBangla: Boolean,
    voiceoverRecorder: VoiceoverRecorder,
    onUpdateClip: ((TimelineClip) -> TimelineClip) -> Unit,
    onAddKeyframe: () -> Unit,
    onSeek: (Long) -> Unit,
    onSplit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    onVoiceoverRecorded: (String, Long) -> Unit
) {
    val availableTabs = remember(clip.trackType, clip.textConfig != null) {
        when (clip.trackType) {
            TrackType.TEXT -> listOf(
                InspectorTab.TEXT_FONT,
                InspectorTab.MOTION,
                InspectorTab.SFX,
                InspectorTab.KEYFRAME,
                InspectorTab.MASK_BLEND,
                InspectorTab.TRIM
            )
            TrackType.GRAPHICS -> buildList {
                if (clip.textConfig != null) add(InspectorTab.TEXT_FONT)
                add(InspectorTab.MOTION)
                add(InspectorTab.SFX)
                add(InspectorTab.KEYFRAME)
                add(InspectorTab.MASK_BLEND)
                add(InspectorTab.TRIM)
            }
            TrackType.VIDEO -> listOf(
                InspectorTab.SPEED,
                InspectorTab.LUMETRI,
                InspectorTab.CURVES,
                InspectorTab.MASK_BLEND,
                InspectorTab.MOTION,
                InspectorTab.KEYFRAME,
                InspectorTab.TRIM
            )
            TrackType.ADJUSTMENT -> listOf(
                InspectorTab.LUMETRI,
                InspectorTab.CURVES,
                InspectorTab.TRIM
            )
            TrackType.VOICEOVER, TrackType.MUSIC -> listOf(
                InspectorTab.AUDIO_MIX,
                InspectorTab.SFX,
                InspectorTab.TRIM
            )
        }
    }

    var activeTab by remember(clip.id) { mutableStateOf(availableTabs.first()) }
    if (activeTab !in availableTabs) activeTab = availableTabs.first()

    Column(modifier = Modifier.fillMaxWidth()) {
        // Header action strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(StudioSurfaceElevated)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = clip.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = CyanPlayhead,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isBangla) clip.trackType.labelBn else clip.trackType.labelEn,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onAddKeyframe, modifier = Modifier.size(36.dp).testTag("inspector_keyframe_btn")) {
                    Icon(Icons.Default.Diamond, contentDescription = "Add Keyframe", tint = CyanPlayhead, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onSplit, modifier = Modifier.size(36.dp).testTag("inspector_split_btn")) {
                    Icon(Icons.Default.ContentCut, contentDescription = "Split Clip", tint = AmberDocumentary, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDuplicate, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate", tint = TextSecondary, modifier = Modifier.size(17.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp).testTag("inspector_delete_btn")) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = CoralAction, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Deselect", tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
            }
        }

        // Inspector Tabs
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(availableTabs, key = { it.name }) { tab ->
                FilterChip(
                    selected = activeTab == tab,
                    onClick = { activeTab = tab },
                    label = {
                        Text(
                            text = if (isBangla) tab.labelBn else tab.labelEn,
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyanPlayhead,
                        selectedLabelColor = StudioObsidian,
                        labelColor = TextSecondary
                    ),
                    modifier = Modifier.height(32.dp)
                )
            }
        }

        // Tab Body
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (activeTab) {
                InspectorTab.TEXT_FONT -> TextFontInspector(clip, isBangla, onUpdateClip)
                InspectorTab.MOTION -> MotionAnimationInspector(clip, isBangla, onUpdateClip)
                InspectorTab.SFX -> SfxLibraryInspector(clip, isBangla, onUpdateClip)
                InspectorTab.KEYFRAME -> KeyframeInspector(clip, playheadMs, isBangla, onAddKeyframe, onSeek, onUpdateClip)
                InspectorTab.LUMETRI -> LumetriSliderInspector(clip, isBangla, onUpdateClip)
                InspectorTab.CURVES -> ColorCurvesEditor(
                    grade = clip.colorGrade,
                    onGradeChange = { next -> onUpdateClip { it.copy(colorGrade = next) } }
                )
                InspectorTab.SPEED -> SpeedRampGraphEditor(
                    profile = clip.speedRamp,
                    onProfileChange = { next -> onUpdateClip { it.copy(speedRamp = next) } }
                )
                InspectorTab.MASK_BLEND -> MaskAndBlendInspector(clip, isBangla, onUpdateClip)
                InspectorTab.AUDIO_MIX -> AudioMixInspector(clip, isBangla, voiceoverRecorder, onUpdateClip, onVoiceoverRecorded)
                InspectorTab.TRIM -> TrimAndTransformInspector(clip, isBangla, onUpdateClip)
            }
        }
    }
}

@Composable
private fun TextFontInspector(
    clip: TimelineClip,
    isBangla: Boolean,
    onUpdateClip: ((TimelineClip) -> TimelineClip) -> Unit
) {
    val tc = clip.textConfig ?: TextLayerConfig()
    OutlinedTextField(
        value = tc.text,
        onValueChange = { txt -> onUpdateClip { it.copy(title = txt.take(20), textConfig = tc.copy(text = txt)) } },
        label = { Text(if (isBangla) "মূল টেক্সট (বাংলা / English)" else "Headline Text") },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = CyanPlayhead,
            unfocusedBorderColor = StudioBorder,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary
        ),
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = tc.subtitle,
        onValueChange = { sub -> onUpdateClip { it.copy(textConfig = tc.copy(subtitle = sub)) } },
        label = { Text(if (isBangla) "সাবটাইটেল / সাল (ঐচ্ছিক)" else "Subtitle / Date") },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AmberDocumentary,
            unfocusedBorderColor = StudioBorder,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary
        ),
        modifier = Modifier.fillMaxWidth()
    )

    // Bangla & English Font Engine
    Text(
        text = if (isBangla) "বাংলা ও ইংরেজি ফন্ট ইঞ্জিন (যুক্তবর্ণ সুরক্ষিত)" else "Bangla & English Font Engine",
        style = MaterialTheme.typography.labelMedium,
        color = CyanPlayhead
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(StudioFontOption.entries, key = { it.id }) { font ->
            Surface(
                onClick = { onUpdateClip { it.copy(textConfig = tc.copy(fontId = font.id)) } },
                color = if (tc.fontId == font.id) CyanPlayhead.copy(alpha = 0.2f) else StudioSurfaceElevated,
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (tc.fontId == font.id) CyanPlayhead else StudioBorder
                )
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(font.previewSample, fontFamily = font.family, color = TextPrimary)
                    Text(font.displayName, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                }
            }
        }
    }

    // Karaoke + Lower Third Templates
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (isBangla) "লাইভ কারাওকে হাইলাইট" else "Karaoke Highlight", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
        Switch(
            checked = tc.karaokeHighlight,
            onCheckedChange = { v -> onUpdateClip { it.copy(textConfig = tc.copy(karaokeHighlight = v)) } },
            colors = SwitchDefaults.colors(checkedThumbColor = CyanPlayhead)
        )
    }

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(LowerThirdTemplate.entries, key = { it.name }) { tpl ->
            FilterChip(
                selected = tc.lowerThirdTemplate == tpl,
                onClick = { onUpdateClip { it.copy(textConfig = tc.copy(lowerThirdTemplate = tpl)) } },
                label = { Text(if (isBangla) tpl.labelBn else tpl.label, style = MaterialTheme.typography.labelSmall) }
            )
        }
    }

    LabeledSlider(
        label = if (isBangla) "ফন্ট সাইজ" else "Font Size",
        value = tc.fontSizeSp,
        range = 14f..48f,
        valueText = "${tc.fontSizeSp.toInt()}sp",
        onValueChange = { sz -> onUpdateClip { it.copy(textConfig = tc.copy(fontSizeSp = sz)) } }
    )
}

@Composable
private fun MotionAnimationInspector(
    clip: TimelineClip,
    isBangla: Boolean,
    onUpdateClip: ((TimelineClip) -> TimelineClip) -> Unit
) {
    var category by remember { mutableStateOf(AnimationCategory.IN) }
    val filtered = remember(category) {
        PresetCatalog.motionAnimations.filter { it.category == category }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AnimationCategory.entries.forEach { cat ->
            FilterChip(
                selected = category == cat,
                onClick = { category = cat },
                label = { Text(if (isBangla) cat.labelBn else cat.labelEn, style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AmberDocumentary,
                    selectedLabelColor = StudioObsidian,
                    labelColor = TextSecondary
                )
            )
        }
    }

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(filtered, key = { it.id }) { spec ->
            val selected = when (spec.category) {
                AnimationCategory.IN -> clip.inAnimationId == spec.id
                AnimationCategory.OUT -> clip.outAnimationId == spec.id
                AnimationCategory.LOOP -> clip.loopAnimationId == spec.id
            }
            Surface(
                onClick = {
                    onUpdateClip {
                        when (spec.category) {
                            AnimationCategory.IN -> it.copy(inAnimationId = spec.id)
                            AnimationCategory.OUT -> it.copy(outAnimationId = spec.id)
                            AnimationCategory.LOOP -> it.copy(loopAnimationId = spec.id)
                        }
                    }
                },
                color = if (selected) CyanPlayhead.copy(alpha = 0.22f) else StudioSurfaceElevated,
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) CyanPlayhead else StudioBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Animation, contentDescription = null, tint = if (selected) CyanPlayhead else TextMuted, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isBangla) spec.nameBn else spec.name, style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                }
            }
        }
    }

    LabeledSlider(
        label = if (isBangla) "অপাসিটি" else "Opacity",
        value = clip.opacity,
        range = 0f..1f,
        valueText = "${(clip.opacity * 100).toInt()}%",
        onValueChange = { op -> onUpdateClip { it.copy(opacity = op) } }
    )
}

@Composable
private fun SfxLibraryInspector(
    clip: TimelineClip,
    isBangla: Boolean,
    onUpdateClip: ((TimelineClip) -> TimelineClip) -> Unit
) {
    var cat by remember { mutableStateOf(SfxCategory.WHOOSH) }
    val filtered = remember(cat) { PresetCatalog.sfxPresets.filter { it.category == cat } }

    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(SfxCategory.entries, key = { it.name }) { c ->
            FilterChip(
                selected = cat == c,
                onClick = { cat = c },
                label = { Text(c.label, style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = EmeraldAudio,
                    selectedLabelColor = StudioObsidian,
                    labelColor = TextSecondary
                )
            )
        }
    }

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(filtered, key = { it.id }) { sfx ->
            val locked = clip.lockedSfxId == sfx.id
            Surface(
                onClick = {
                    AudioSynthEngine.previewSfx(sfx, clip.audioConfig.volume)
                    onUpdateClip { it.copy(lockedSfxId = if (locked) null else sfx.id) }
                },
                color = if (locked) EmeraldAudio.copy(alpha = 0.22f) else StudioSurfaceElevated,
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (locked) EmeraldAudio else StudioBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Audiotrack, contentDescription = null, tint = if (locked) EmeraldAudio else TextMuted, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(sfx.name, style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                }
            }
        }
    }

    LabeledSlider(
        label = if (isBangla) "SFX ভলিউম" else "SFX Volume",
        value = clip.audioConfig.volume,
        range = 0f..1.5f,
        valueText = "${(clip.audioConfig.volume * 100).toInt()}%",
        onValueChange = { vol -> onUpdateClip { it.copy(audioConfig = it.audioConfig.copy(volume = vol)) } }
    )
}

@Composable
private fun KeyframeInspector(
    clip: TimelineClip,
    playheadMs: Long,
    isBangla: Boolean,
    onAddKeyframe: () -> Unit,
    onSeek: (Long) -> Unit,
    onUpdateClip: ((TimelineClip) -> TimelineClip) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (isBangla) "কি-ফ্রেম পয়েন্ট (${clip.keyframes.size})" else "Keyframes (${clip.keyframes.size})",
            style = MaterialTheme.typography.titleSmall,
            color = TextPrimary
        )
        Button(
            onClick = onAddKeyframe,
            colors = ButtonDefaults.buttonColors(containerColor = CyanPlayhead, contentColor = StudioObsidian),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.Diamond, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(5.dp))
            Text(if (isBangla) "+ কি-ফ্রেম প্লেহেডে" else "+ Keyframe Here", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }

    if (clip.keyframes.isNotEmpty()) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(clip.keyframes, key = { it.id }) { kf ->
                Surface(
                    onClick = { onSeek(clip.startMs + kf.timeOffsetMs) },
                    color = StudioSurfaceElevated,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyanPlayhead.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "◆ %.2fs".format(kf.timeOffsetMs / 1000f),
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanPlayhead,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }

    LabeledSlider(
        label = if (isBangla) "স্কেল (Scale)" else "Scale",
        value = clip.scale,
        range = 0.25f..3.0f,
        valueText = "%.2fx".format(clip.scale),
        onValueChange = { s -> onUpdateClip { it.copy(scale = s) } }
    )
    LabeledSlider(
        label = if (isBangla) "রোটেশন (Rotate)" else "Rotation",
        value = clip.rotation,
        range = -180f..180f,
        valueText = "${clip.rotation.toInt()}°",
        onValueChange = { r -> onUpdateClip { it.copy(rotation = r) } }
    )
}

@Composable
private fun LumetriSliderInspector(
    clip: TimelineClip,
    isBangla: Boolean,
    onUpdateClip: ((TimelineClip) -> TimelineClip) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(PresetCatalog.lumetriPresets, key = { it.name }) { preset ->
            FilterChip(
                selected = false,
                onClick = { onUpdateClip { it.copy(colorGrade = preset.grade) } },
                label = { Text(if (isBangla) preset.nameBn else preset.name, style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(labelColor = AmberDocumentary)
            )
        }
    }
    val g = clip.colorGrade
    LabeledSlider(if (isBangla) "ব্রাইটনেস" else "Brightness", g.brightness, -0.5f..0.5f, "%.2f".format(g.brightness)) { v ->
        onUpdateClip { it.copy(colorGrade = g.copy(brightness = v)) }
    }
    LabeledSlider(if (isBangla) "কন্ট্রাস্ট" else "Contrast", g.contrast, 0.4f..2.0f, "%.2f".format(g.contrast)) { v ->
        onUpdateClip { it.copy(colorGrade = g.copy(contrast = v)) }
    }
    LabeledSlider(if (isBangla) "স্যাচুরেশন" else "Saturation", g.saturation, 0f..2.2f, "%.2f".format(g.saturation)) { v ->
        onUpdateClip { it.copy(colorGrade = g.copy(saturation = v)) }
    }
    LabeledSlider(if (isBangla) "টেম্পারেচার" else "Temperature", g.temperature, -0.5f..0.5f, "%.2f".format(g.temperature)) { v ->
        onUpdateClip { it.copy(colorGrade = g.copy(temperature = v)) }
    }
    LabeledSlider(if (isBangla) "সিনেমা গ্লো" else "Glow", g.glow, 0f..1f, "${(g.glow * 100).toInt()}%") { v ->
        onUpdateClip { it.copy(colorGrade = g.copy(glow = v)) }
    }
    LabeledSlider(if (isBangla) "ভিনিয়েট" else "Vignette", g.vignette, 0f..0.9f, "${(g.vignette * 100).toInt()}%") { v ->
        onUpdateClip { it.copy(colorGrade = g.copy(vignette = v)) }
    }
}

@Composable
private fun MaskAndBlendInspector(
    clip: TimelineClip,
    isBangla: Boolean,
    onUpdateClip: ((TimelineClip) -> TimelineClip) -> Unit
) {
    Text(if (isBangla) "মাস্কিং শেপ (Masking)" else "Mask Shape", style = MaterialTheme.typography.labelMedium, color = CyanPlayhead)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(MaskShape.entries, key = { it.name }) { shape ->
            FilterChip(
                selected = clip.maskConfig.shape == shape,
                onClick = { onUpdateClip { it.copy(maskConfig = it.maskConfig.copy(shape = shape)) } },
                label = { Text(if (isBangla) shape.labelBn else shape.label, style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
    LabeledSlider(
        label = if (isBangla) "মাস্ক সাইজ" else "Mask Size",
        value = clip.maskConfig.scale,
        range = 0.25f..1f,
        valueText = "${(clip.maskConfig.scale * 100).toInt()}%",
        onValueChange = { s -> onUpdateClip { it.copy(maskConfig = it.maskConfig.copy(scale = s)) } }
    )
    Text(if (isBangla) "ব্লেন্ডিং মোড (Blending)" else "Blending Mode", style = MaterialTheme.typography.labelMedium, color = AmberDocumentary)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(BlendModeOption.entries, key = { it.name }) { mode ->
            FilterChip(
                selected = clip.blendMode == mode,
                onClick = { onUpdateClip { it.copy(blendMode = mode) } },
                label = { Text(mode.label, style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}

@Composable
private fun AudioMixInspector(
    clip: TimelineClip,
    isBangla: Boolean,
    voiceoverRecorder: VoiceoverRecorder,
    onUpdateClip: ((TimelineClip) -> TimelineClip) -> Unit,
    onVoiceoverRecorded: (String, Long) -> Unit
) {
    val ac = clip.audioConfig
    LabeledSlider(if (isBangla) "ভলিউম" else "Volume", ac.volume, 0f..1.5f, "${(ac.volume * 100).toInt()}%") { v ->
        onUpdateClip { it.copy(audioConfig = ac.copy(volume = v)) }
    }
    if (clip.trackType == TrackType.MUSIC) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isBangla) "অটো-ডাকিং (ভয়েস চললে মিউজিক কমবে)" else "Auto-Ducking under Voiceover",
                style = MaterialTheme.typography.bodySmall,
                color = EmeraldAudio
            )
            Switch(
                checked = ac.autoDuckingEnabled,
                onCheckedChange = { en -> onUpdateClip { it.copy(audioConfig = ac.copy(autoDuckingEnabled = en)) } },
                colors = SwitchDefaults.colors(checkedThumbColor = EmeraldAudio)
            )
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isBangla) "নয়েজ রিডাকশন (Denoise)" else "One-Tap Noise Reduction",
                style = MaterialTheme.typography.bodySmall,
                color = CyanPlayhead
            )
            Switch(
                checked = ac.noiseReductionEnabled,
                onCheckedChange = { en -> onUpdateClip { it.copy(audioConfig = ac.copy(noiseReductionEnabled = en)) } },
                colors = SwitchDefaults.colors(checkedThumbColor = CyanPlayhead)
            )
        }
    }
    if (ac.audioPath != null && ac.audioPath.endsWith(".wav")) {
        Button(
            onClick = { AudioSynthEngine.playRecordedFile(ac.audioPath) },
            colors = ButtonDefaults.buttonColors(containerColor = EmeraldAudio, contentColor = StudioObsidian),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text(if (isBangla) "রেকর্ড করা ভয়েস শুনুন" else "Play Recorded Voiceover")
        }
    }
    LabeledSlider(if (isBangla) "ফেড ইন (ms)" else "Fade In", ac.fadeInMs.toFloat(), 0f..2000f, "${ac.fadeInMs}ms") { v ->
        onUpdateClip { it.copy(audioConfig = ac.copy(fadeInMs = v.toLong())) }
    }
    LabeledSlider(if (isBangla) "ফেড আউট (ms)" else "Fade Out", ac.fadeOutMs.toFloat(), 0f..2000f, "${ac.fadeOutMs}ms") { v ->
        onUpdateClip { it.copy(audioConfig = ac.copy(fadeOutMs = v.toLong())) }
    }
}

@Composable
private fun TrimAndTransformInspector(
    clip: TimelineClip,
    isBangla: Boolean,
    onUpdateClip: ((TimelineClip) -> TimelineClip) -> Unit
) {
    LabeledSlider(
        label = if (isBangla) "ক্লিপ ডিউরেশন" else "Clip Duration",
        value = clip.durationMs / 1000f,
        range = 1f..20f,
        valueText = "%.1fs".format(clip.durationMs / 1000f),
        onValueChange = { sec -> onUpdateClip { it.copy(durationMs = (sec * 1000f).toLong()) } }
    )
    LabeledSlider(
        label = if (isBangla) "শুরু (Start Offset)" else "Start Time",
        value = clip.startMs / 1000f,
        range = 0f..20f,
        valueText = "%.1fs".format(clip.startMs / 1000f),
        onValueChange = { sec -> onUpdateClip { it.copy(startMs = (sec * 1000f).toLong()) } }
    )
    LabeledSlider(
        label = if (isBangla) "সাইজ / স্কেল" else "Size / Scale",
        value = clip.scale,
        range = 0.25f..3f,
        valueText = "%.2fx".format(clip.scale),
        onValueChange = { s -> onUpdateClip { it.copy(scale = s) } }
    )
    LabeledSlider(
        label = if (isBangla) "অপাসিটি" else "Opacity",
        value = clip.opacity,
        range = 0f..1f,
        valueText = "${(clip.opacity * 100).toInt()}%",
        onValueChange = { op -> onUpdateClip { it.copy(opacity = op) } }
    )
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Text(text = valueText, style = MaterialTheme.typography.labelSmall, color = CyanPlayhead)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = CyanPlayhead,
                activeTrackColor = CyanPlayhead,
                inactiveTrackColor = StudioObsidian
            ),
            modifier = Modifier.height(30.dp)
        )
    }
}
