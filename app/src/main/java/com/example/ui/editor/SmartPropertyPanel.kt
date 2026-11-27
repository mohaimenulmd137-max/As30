package com.example.ui.editor

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TextFields
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import com.example.domain.engine.AudioSynthEngine
import com.example.domain.engine.CustomFontManager
import com.example.domain.engine.VoiceoverRecorder
import com.example.domain.model.AnimationCategory
import com.example.domain.model.BlendModeOption
import com.example.domain.model.BorderConfig
import com.example.domain.model.BorderShapeStyle
import com.example.domain.model.InvestigativeToolType
import com.example.domain.model.LowerThirdTemplate
import com.example.domain.model.MaskShape
import com.example.domain.model.PresetCatalog
import com.example.domain.model.SfxCategory
import com.example.domain.model.StudioFontOption
import com.example.domain.model.StudioTextAlign
import com.example.domain.model.TextLayerConfig
import com.example.domain.model.TimelineClip
import com.example.domain.model.TrackType
import com.example.domain.model.TransitionType
import com.example.domain.model.VfxEffectType
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

enum class ProToolSection(val labelBn: String, val labelEn: String) {
    SPEED("স্পিড", "Speed"),
    COLOR("কালার ও কার্ভস", "Color"),
    ANIMATION("অ্যানিমেশন ও কি-ফ্রেম", "Animation"),
    BACKGROUND("ব্যাকগ্রাউন্ড ও মাস্ক", "Background"),
    VOLUME("ভলিউম ও অটো-ডাকিং", "Audio & SFX"),
    TEXT_FONT("টেক্সট ও ফন্ট", "Text & Font"),
    TRANSITION("ট্রানজিশন স্টুডিও", "Transition Studio"),
    VFX("হলিউড VFX", "Hollywood VFX"),
    OVERLAY_PRO("চার্ট / ম্যাপ / ডক", "Charts & Map Doc")
}

@Composable
fun BottomActionToolGrid(
    selectedClip: TimelineClip?,
    activeSection: ProToolSection?,
    isBangla: Boolean,
    onSplit: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onToggleLock: () -> Unit,
    onSelectSection: (ProToolSection) -> Unit,
    onQuickAddText: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLocked = selectedClip?.isLocked == true

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0A0D16))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Row 1: কাট | ডিলিট | স্পিড | কালার | অ্যানিমেশন | ব্যাকগ্রাউন্ড
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ProGridTile(
                icon = Icons.Default.ContentCut,
                label = if (isBangla) "কাট" else "Cut",
                tint = TextSecondary,
                isActive = false,
                tag = "grid_cut",
                onClick = onSplit,
                modifier = Modifier.weight(1f)
            )
            ProGridTile(
                icon = Icons.Default.DeleteOutline,
                label = if (isBangla) "ডিলিট" else "Delete",
                tint = TextSecondary,
                isActive = false,
                tag = "grid_delete",
                onClick = onDelete,
                modifier = Modifier.weight(1f)
            )
            ProGridTile(
                icon = Icons.Default.Speed,
                label = if (isBangla) "স্পিড" else "Speed",
                tint = CyanPlayhead,
                isActive = activeSection == ProToolSection.SPEED,
                tag = "grid_speed",
                onClick = { onSelectSection(ProToolSection.SPEED) },
                modifier = Modifier.weight(1f)
            )
            ProGridTile(
                icon = Icons.Default.Palette,
                label = if (isBangla) "কালার" else "Color",
                tint = AmberDocumentary,
                isActive = activeSection == ProToolSection.COLOR,
                tag = "grid_color",
                onClick = { onSelectSection(ProToolSection.COLOR) },
                modifier = Modifier.weight(1f)
            )
            ProGridTile(
                icon = Icons.Default.AutoFixHigh,
                label = if (isBangla) "অ্যানিমেশন" else "Anim",
                tint = Color(0xFFF472B6),
                isActive = activeSection == ProToolSection.ANIMATION,
                tag = "grid_anim",
                onClick = { onSelectSection(ProToolSection.ANIMATION) },
                modifier = Modifier.weight(1f)
            )
            ProGridTile(
                icon = Icons.Default.PersonOutline,
                label = if (isBangla) "ব্যাকগ্রাউন্ড" else "BG/Mask",
                tint = EmeraldAudio,
                isActive = activeSection == ProToolSection.BACKGROUND,
                tag = "grid_bg",
                onClick = { onSelectSection(ProToolSection.BACKGROUND) },
                modifier = Modifier.weight(1f)
            )
        }

        // Row 2: কপি | লক | ভলিউম | + টেক্সট | ট্রানজিশন | ওভারলে
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ProGridTile(
                icon = Icons.Default.ContentCopy,
                label = if (isBangla) "কপি" else "Copy",
                tint = TextSecondary,
                isActive = false,
                tag = "grid_copy",
                onClick = onDuplicate,
                modifier = Modifier.weight(1f)
            )
            ProGridTile(
                icon = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                label = if (isBangla) "লক" else "Lock",
                tint = AmberDocumentary,
                isActive = isLocked,
                tag = "grid_lock",
                onClick = onToggleLock,
                modifier = Modifier.weight(1f)
            )
            ProGridTile(
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                label = if (isBangla) "ভলিউম" else "Volume",
                tint = EmeraldAudio,
                isActive = activeSection == ProToolSection.VOLUME,
                tag = "grid_volume",
                onClick = { onSelectSection(ProToolSection.VOLUME) },
                modifier = Modifier.weight(1f)
            )
            ProGridTile(
                icon = Icons.Default.TextFields,
                label = if (isBangla) "+ টেক্সট" else "+ Text",
                tint = TextPrimary,
                isActive = activeSection == ProToolSection.TEXT_FONT,
                tag = "grid_text",
                onClick = {
                    if (selectedClip?.textConfig == null) onQuickAddText()
                    onSelectSection(ProToolSection.TEXT_FONT)
                },
                modifier = Modifier.weight(1f)
            )
            ProGridTile(
                icon = Icons.Default.AutoAwesome,
                label = if (isBangla) "ট্রানজিশন" else "Transition",
                tint = Color(0xFF2DD4BF),
                isActive = activeSection == ProToolSection.TRANSITION || activeSection == ProToolSection.VFX,
                tag = "grid_transition",
                onClick = { onSelectSection(ProToolSection.TRANSITION) },
                modifier = Modifier.weight(1f)
            )
            ProGridTile(
                icon = Icons.Default.Layers,
                label = if (isBangla) "ওভারলে" else "Overlay",
                tint = TextPrimary,
                isActive = activeSection == ProToolSection.OVERLAY_PRO,
                tag = "grid_overlay",
                onClick = { onSelectSection(ProToolSection.OVERLAY_PRO) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ProGridTile(
    icon: ImageVector,
    label: String,
    tint: Color,
    isActive: Boolean,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = if (isActive) Color(0xFF1F2A3E) else Color(0xFF121826),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (isActive) AmberDocumentary else Color(0xFF232C40)
        ),
        modifier = modifier
            .height(50.dp)
            .testTag(tag)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                color = if (isActive) AmberDocumentary else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ExpandableInspectorSheet(
    section: ProToolSection,
    selectedClip: TimelineClip?,
    allClips: List<TimelineClip> = emptyList(),
    playheadMs: Long,
    isBangla: Boolean,
    voiceoverRecorder: VoiceoverRecorder,
    onUpdateClip: (String, (TimelineClip) -> TimelineClip) -> Unit,
    onSelectAudioTarget: (String) -> Unit = {},
    onOpenFloatingAnimEditor: () -> Unit = {},
    onAddKeyframe: (String) -> Unit,
    onSeek: (Long) -> Unit,
    onAddVfxClip: (VfxEffectType) -> Unit,
    onApplyTransitionToAll: (TransitionType, Long, TransitionType, Long) -> Unit = { _, _, _, _ -> },
    onSwitchSection: (ProToolSection) -> Unit = {},
    onAddChart: (Int) -> Unit,
    onAddInvestigativeTool: (InvestigativeToolType) -> Unit,
    onOpenChartStudio: () -> Unit = {},
    onOpenMapStudio: () -> Unit = {},
    onPickAudioFile: () -> Unit,
    onImportCustomFont: (clipId: String) -> Unit,
    onVoiceoverRecorded: (String, Long) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showRgbCurves by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(StudioSurface)
            .border(1.dp, AmberDocumentary.copy(alpha = 0.45f))
    ) {
        // Sheet Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(StudioSurfaceElevated)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isBangla) section.labelBn else section.labelEn,
                    style = MaterialTheme.typography.titleSmall,
                    color = AmberDocumentary,
                    fontWeight = FontWeight.Bold
                )
                if (selectedClip != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "• ${selectedClip.title}",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanPlayhead,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectedClip != null) {
                    IconButton(
                        onClick = { onAddKeyframe(selectedClip.id) },
                        modifier = Modifier.size(32.dp).testTag("inspector_keyframe_btn")
                    ) {
                        Icon(Icons.Default.Diamond, contentDescription = "Add Keyframe", tint = CyanPlayhead, modifier = Modifier.size(16.dp))
                    }
                }
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close Inspector", tint = TextPrimary, modifier = Modifier.size(18.dp))
                }
            }
        }

        // Scrollable Sheet Content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(215.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (section) {
                ProToolSection.SPEED -> {
                    if (selectedClip != null) {
                        SpeedRampGraphEditor(
                            profile = selectedClip.speedRamp,
                            onProfileChange = { next -> onUpdateClip(selectedClip.id) { it.copy(speedRamp = next) } }
                        )
                    } else {
                        EmptySelectionHint(isBangla)
                    }
                }

                ProToolSection.COLOR -> {
                    if (selectedClip != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(if (isBangla) "RGB কার্ভস মোড" else "RGB Curves Mode", style = MaterialTheme.typography.bodySmall, color = CyanPlayhead)
                            Switch(checked = showRgbCurves, onCheckedChange = { showRgbCurves = it }, colors = SwitchDefaults.colors(checkedThumbColor = CyanPlayhead))
                        }
                        if (showRgbCurves) {
                            ColorCurvesEditor(
                                grade = selectedClip.colorGrade,
                                onGradeChange = { next -> onUpdateClip(selectedClip.id) { it.copy(colorGrade = next) } }
                            )
                        } else {
                            LumetriSliderInspector(selectedClip, isBangla) { fn -> onUpdateClip(selectedClip.id, fn) }
                        }
                    } else {
                        EmptySelectionHint(isBangla)
                    }
                }

                ProToolSection.ANIMATION -> {
                    if (selectedClip != null) {
                        Button(
                            onClick = onOpenFloatingAnimEditor,
                            colors = ButtonDefaults.buttonColors(containerColor = AmberDocumentary, contentColor = StudioObsidian),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("open_floating_anim_btn")
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isBangla) "✨ ফ্লোটিং অ্যানিমেশন এডিটর খুলুন" else "✨ Open Floating Animation Editor",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        MotionAnimationInspector(selectedClip, isBangla) { fn -> onUpdateClip(selectedClip.id, fn) }
                        KeyframeInspector(selectedClip, playheadMs, isBangla, { onAddKeyframe(selectedClip.id) }, onSeek) { fn -> onUpdateClip(selectedClip.id, fn) }
                    } else {
                        EmptySelectionHint(isBangla)
                    }
                }

                ProToolSection.BACKGROUND -> {
                    if (selectedClip != null) {
                        MaskAndBlendInspector(selectedClip, isBangla) { fn -> onUpdateClip(selectedClip.id, fn) }
                    } else {
                        EmptySelectionHint(isBangla)
                    }
                }

                ProToolSection.VOLUME -> {
                    AudioMixInspector(
                        clip = selectedClip,
                        allClips = allClips,
                        isBangla = isBangla,
                        voiceoverRecorder = voiceoverRecorder,
                        onSelectAudioTarget = onSelectAudioTarget,
                        onUpdateClip = { fn -> selectedClip?.let { onUpdateClip(it.id, fn) } },
                        onPickAudioFile = onPickAudioFile,
                        onVoiceoverRecorded = onVoiceoverRecorded
                    )
                    if (selectedClip != null) {
                        SfxLibraryInspector(selectedClip, isBangla) { fn -> onUpdateClip(selectedClip.id, fn) }
                    }
                }

                ProToolSection.TEXT_FONT -> {
                    if (selectedClip != null) {
                        TextFontInspector(
                            clip = selectedClip,
                            isBangla = isBangla,
                            onImportCustomFont = { onImportCustomFont(selectedClip.id) },
                            onUpdateClip = { fn -> onUpdateClip(selectedClip.id, fn) }
                        )
                    } else {
                        EmptySelectionHint(isBangla)
                    }
                }

                ProToolSection.TRANSITION, ProToolSection.VFX -> {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = section == ProToolSection.TRANSITION,
                            onClick = { onSwitchSection(ProToolSection.TRANSITION) },
                            label = { Text(if (isBangla) "✨ ট্রানজিশন (Start/End)" else "Transition", style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AmberDocumentary,
                                selectedLabelColor = StudioObsidian,
                                labelColor = TextSecondary
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = section == ProToolSection.VFX,
                            onClick = { onSwitchSection(ProToolSection.VFX) },
                            label = { Text(if (isBangla) "🎬 হলিউড VFX" else "Hollywood VFX", style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyanPlayhead,
                                selectedLabelColor = StudioObsidian,
                                labelColor = TextSecondary
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (section == ProToolSection.TRANSITION) {
                        TransitionStudioInspector(
                            clip = selectedClip,
                            isBangla = isBangla,
                            onUpdateClip = onUpdateClip,
                            onApplyTransitionToAll = onApplyTransitionToAll
                        )
                    } else {
                        VfxStudioInspector(
                            clip = selectedClip,
                            isBangla = isBangla,
                            onAddVfxClip = onAddVfxClip,
                            onUpdateClip = onUpdateClip
                        )
                    }
                }

                ProToolSection.OVERLAY_PRO -> {
                    OverlayProInspector(
                        clip = selectedClip,
                        isBangla = isBangla,
                        onAddChart = onAddChart,
                        onAddInvestigativeTool = onAddInvestigativeTool,
                        onOpenChartStudio = onOpenChartStudio,
                        onOpenMapStudio = onOpenMapStudio,
                        onUpdateClip = onUpdateClip
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptySelectionHint(isBangla: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isBangla) "এডিট করতে টাইমলাইনে যেকোনো ক্লিপে ট্যাপ করুন" else "Tap any clip on the timeline to edit",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }
}

@Composable
private fun TextFontInspector(
    clip: TimelineClip,
    isBangla: Boolean,
    onImportCustomFont: () -> Unit,
    onUpdateClip: ((TimelineClip) -> TimelineClip) -> Unit
) {
    val tc = clip.textConfig ?: TextLayerConfig()
    OutlinedTextField(
        value = tc.text,
        onValueChange = { txt -> onUpdateClip { it.copy(title = txt.take(20), textConfig = tc.copy(text = txt)) } },
        label = { Text(if (isBangla) "মূল টেক্সট (বাংলা / English)" else "Headline Text") },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyanPlayhead, unfocusedBorderColor = StudioBorder, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = tc.subtitle,
        onValueChange = { sub -> onUpdateClip { it.copy(textConfig = tc.copy(subtitle = sub)) } },
        label = { Text(if (isBangla) "সাবটাইটেল / সাল" else "Subtitle / Date") },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AmberDocumentary, unfocusedBorderColor = StudioBorder, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
        modifier = Modifier.fillMaxWidth()
    )

    // 1. 16 Formatted Text Style Presets
    Text(
        text = if (isBangla) "ফর্মেটেড টেক্সট স্টাইল (${PresetCatalog.textStylePresets.size})" else "Formatted Text Styles (${PresetCatalog.textStylePresets.size})",
        style = MaterialTheme.typography.labelMedium,
        color = AmberDocumentary,
        fontWeight = FontWeight.Bold
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(PresetCatalog.textStylePresets, key = { it.id }) { preset ->
            val selected = tc.stylePresetId == preset.id
            val previewFamily = remember(preset.fontId) {
                CustomFontManager.resolveComposeFontFamily(preset.fontId, null)
            }
            Surface(
                onClick = {
                    onUpdateClip {
                        it.copy(textConfig = PresetCatalog.applyTextStylePreset(tc, preset))
                    }
                },
                color = if (preset.backgroundPill) Color(preset.pillColorHex) else if (selected) CyanPlayhead.copy(alpha = 0.16f) else StudioSurfaceElevated,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) AmberDocumentary else StudioBorder),
                modifier = Modifier.testTag("text_style_${preset.id}")
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isBangla) "অআ · Aa" else "Aa · স্টাইল",
                        fontFamily = previewFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(preset.textColorHex)
                    )
                    Text(
                        text = if (isBangla) preset.nameBn else preset.nameEn,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) AmberDocumentary else TextSecondary,
                        maxLines = 1
                    )
                }
            }
        }
    }

    // 2. Bundled Fonts + Custom .TTF/.OTF Font Importer
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (isBangla) "ফন্ট নির্বাচন ও নিজস্ব ফন্ট (.ttf/.otf)" else "Fonts & Custom Font Import",
            style = MaterialTheme.typography.labelMedium,
            color = CyanPlayhead
        )
        Surface(
            onClick = onImportCustomFont,
            color = Color(0xFF132638),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, CyanPlayhead.copy(alpha = 0.65f)),
            modifier = Modifier.testTag("import_custom_font_button")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.UploadFile, contentDescription = "Import Custom Font", tint = CyanPlayhead, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isBangla) "+ নিজস্ব ফন্ট" else "+ Font File",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyanPlayhead,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    if (!tc.customFontPath.isNullOrBlank()) {
        Surface(
            color = EmeraldAudio.copy(alpha = 0.14f),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, EmeraldAudio.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isBangla) "ইমপোর্টেড ফন্ট: ${tc.customFontName ?: "Custom"}" else "Imported: ${tc.customFontName ?: "Custom"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = EmeraldAudio,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { onUpdateClip { it.copy(textConfig = tc.copy(customFontPath = null, customFontName = null)) } },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Clear Custom Font", tint = TextPrimary, modifier = Modifier.size(14.dp))
                }
            }
        }
    }

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(StudioFontOption.entries, key = { it.id }) { font ->
            val selected = tc.customFontPath == null && tc.fontId == font.id
            Surface(
                onClick = { onUpdateClip { it.copy(textConfig = tc.copy(fontId = font.id, customFontPath = null, customFontName = null)) } },
                color = if (selected) CyanPlayhead.copy(alpha = 0.2f) else StudioSurfaceElevated,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, if (selected) CyanPlayhead else StudioBorder)
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(font.previewSample, fontFamily = font.family, color = TextPrimary)
                    Text(font.displayName, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                }
            }
        }
    }

    // 3. Color Mode (Solid vs Gradient) & Color Swatches
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (isBangla) "টেক্সট গ্রেডিয়েন্ট কালার" else "Gradient Text Fill",
            style = MaterialTheme.typography.bodySmall,
            color = AmberDocumentary
        )
        Switch(
            checked = tc.useGradient,
            onCheckedChange = { grad -> onUpdateClip { it.copy(textConfig = tc.copy(useGradient = grad)) } },
            colors = SwitchDefaults.colors(checkedThumbColor = AmberDocumentary)
        )
    }

    ColorSwatchRow(
        title = if (tc.useGradient) {
            if (isBangla) "গ্রেডিয়েন্ট শুরু কালার" else "Gradient Start"
        } else {
            if (isBangla) "টেক্সট কালার" else "Text Color"
        },
        selectedHex = if (tc.useGradient) tc.gradientStartHex else tc.textColorHex,
        onSelect = { hex ->
            onUpdateClip {
                if (tc.useGradient) {
                    it.copy(textConfig = tc.copy(gradientStartHex = hex, textColorHex = hex))
                } else {
                    it.copy(textConfig = tc.copy(textColorHex = hex))
                }
            }
        }
    )

    if (tc.useGradient) {
        ColorSwatchRow(
            title = if (isBangla) "গ্রেডিয়েন্ট শেষ কালার" else "Gradient End",
            selectedHex = tc.gradientEndHex,
            onSelect = { hex -> onUpdateClip { it.copy(textConfig = tc.copy(gradientEndHex = hex)) } }
        )
    }

    ColorSwatchRow(
        title = if (isBangla) "অ্যাক্সেন্ট / বর্ডার কালার" else "Accent Color",
        selectedHex = tc.accentColorHex,
        onSelect = { hex -> onUpdateClip { it.copy(textConfig = tc.copy(accentColorHex = hex)) } }
    )

    ColorSwatchRow(
        title = if (isBangla) "স্ট্রোক / আউটলাইন কালার" else "Stroke Outline Color",
        selectedHex = tc.strokeColorHex,
        onSelect = { hex -> onUpdateClip { it.copy(textConfig = tc.copy(strokeColorHex = hex)) } }
    )

    // 4. Alignment Chips
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (isBangla) "অ্যালাইনমেন্ট" else "Alignment", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StudioTextAlign.entries.forEach { align ->
                val active = tc.textAlign == align
                val alignIcon = when (align) {
                    StudioTextAlign.LEFT -> Icons.Default.FormatAlignLeft
                    StudioTextAlign.CENTER -> Icons.Default.FormatAlignCenter
                    StudioTextAlign.RIGHT -> Icons.Default.FormatAlignRight
                }
                Surface(
                    onClick = { onUpdateClip { it.copy(textConfig = tc.copy(textAlign = align)) } },
                    color = if (active) CyanPlayhead.copy(alpha = 0.22f) else StudioSurfaceElevated,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (active) CyanPlayhead else StudioBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(alignIcon, contentDescription = align.labelEn, tint = if (active) CyanPlayhead else TextSecondary, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(if (isBangla) align.labelBn else align.labelEn, style = MaterialTheme.typography.labelSmall, color = if (active) CyanPlayhead else TextSecondary)
                    }
                }
            }
        }
    }

    // 5. Formatting Sliders
    InspectorSlider(if (isBangla) "ফন্ট সাইজ" else "Font Size", tc.fontSizeSp, 14f..52f, "${tc.fontSizeSp.toInt()}sp") { sz ->
        onUpdateClip { it.copy(textConfig = tc.copy(fontSizeSp = sz)) }
    }
    InspectorSlider(if (isBangla) "স্ট্রোক / আউটলাইন" else "Stroke Width", tc.strokeWidthPx, 0f..12f, "%.1fpx".format(tc.strokeWidthPx)) { sw ->
        onUpdateClip { it.copy(textConfig = tc.copy(strokeWidthPx = sw)) }
    }
    InspectorSlider(if (isBangla) "শ্যাডো / গ্লো" else "Shadow / Glow", tc.shadowRadius, 0f..24f, "${tc.shadowRadius.toInt()}px") { sh ->
        onUpdateClip { it.copy(textConfig = tc.copy(shadowRadius = sh)) }
    }
    InspectorSlider(if (isBangla) "অক্ষরের দূরত্ব" else "Letter Spacing", tc.letterSpacingSp, -1f..8f, "%.1fsp".format(tc.letterSpacingSp)) { ls ->
        onUpdateClip { it.copy(textConfig = tc.copy(letterSpacingSp = ls)) }
    }
    InspectorSlider(if (isBangla) "লাইনের উচ্চতা" else "Line Height", tc.lineHeightMult, 0.9f..2.0f, "%.2fx".format(tc.lineHeightMult)) { lh ->
        onUpdateClip { it.copy(textConfig = tc.copy(lineHeightMult = lh)) }
    }

    // 6. Pill Background & Karaoke & Lower Third Template
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(if (isBangla) "ব্যাকগ্রাউন্ড বক্স (Pill)" else "Background Pill", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
        Switch(checked = tc.backgroundPill, onCheckedChange = { bg -> onUpdateClip { it.copy(textConfig = tc.copy(backgroundPill = bg)) } }, colors = SwitchDefaults.colors(checkedThumbColor = AmberDocumentary))
    }

    if (tc.backgroundPill) {
        ColorSwatchRow(
            title = if (isBangla) "বক্স ব্যাকগ্রাউন্ড কালার" else "Pill Color",
            selectedHex = tc.pillColorHex,
            onSelect = { hex ->
                val translucent = (0xDCL shl 24) or (hex and 0x00FFFFFFL)
                onUpdateClip { it.copy(textConfig = tc.copy(pillColorHex = translucent)) }
            }
        )
        InspectorSlider(if (isBangla) "বক্স কর্নার রাউন্ড" else "Pill Radius", tc.pillCornerDp, 0f..24f, "${tc.pillCornerDp.toInt()}dp") { rad ->
            onUpdateClip { it.copy(textConfig = tc.copy(pillCornerDp = rad)) }
        }
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(if (isBangla) "লাইভ কারাওকে হাইলাইট" else "Karaoke Highlight", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
        Switch(checked = tc.karaokeHighlight, onCheckedChange = { v -> onUpdateClip { it.copy(textConfig = tc.copy(karaokeHighlight = v)) } }, colors = SwitchDefaults.colors(checkedThumbColor = CyanPlayhead))
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
}

@Composable
private fun ColorSwatchRow(
    title: String,
    selectedHex: Long,
    onSelect: (Long) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(PresetCatalog.textColorSwatches, key = { it }) { hex ->
                val isSelected = (selectedHex and 0x00FFFFFFL) == (hex and 0x00FFFFFFL)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(Color(hex), Color(hex).copy(alpha = 0.85f))
                            )
                        )
                        .border(
                            width = if (isSelected) 2.5.dp else 1.dp,
                            color = if (isSelected) Color.White else StudioBorder,
                            shape = CircleShape
                        )
                        .clickable { onSelect(hex) }
                )
            }
        }
    }
}

@Composable
private fun MotionAnimationInspector(
    clip: TimelineClip,
    isBangla: Boolean,
    onUpdateClip: ((TimelineClip) -> TimelineClip) -> Unit
) {
    var category by remember { mutableStateOf(AnimationCategory.IN) }
    val filtered = remember(category) { PresetCatalog.motionAnimations.filter { it.category == category } }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AnimationCategory.entries.forEach { cat ->
            FilterChip(
                selected = category == cat,
                onClick = { category = cat },
                label = { Text(if (isBangla) cat.labelBn else cat.labelEn, style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AmberDocumentary, selectedLabelColor = StudioObsidian, labelColor = TextSecondary)
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
                    val isNone = spec.id == "in_none" || spec.id == "out_none" || spec.id == "loop_none"
                    onUpdateClip {
                        val prof = it.animationProfile
                        when (spec.category) {
                            AnimationCategory.IN -> it.copy(
                                inAnimationId = spec.id,
                                animationProfile = prof.copy(inSlot = prof.inSlot.copy(animationId = spec.id, enabled = !isNone))
                            )
                            AnimationCategory.OUT -> it.copy(
                                outAnimationId = spec.id,
                                animationProfile = prof.copy(outSlot = prof.outSlot.copy(animationId = spec.id, enabled = !isNone))
                            )
                            AnimationCategory.LOOP -> it.copy(
                                loopAnimationId = spec.id,
                                animationProfile = prof.copy(loopSlot = prof.loopSlot.copy(animationId = spec.id, enabled = !isNone))
                            )
                        }
                    }
                },
                color = if (selected) CyanPlayhead.copy(alpha = 0.22f) else StudioSurfaceElevated,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, if (selected) CyanPlayhead else StudioBorder)
            ) {
                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Animation, contentDescription = null, tint = if (selected) CyanPlayhead else TextMuted, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isBangla) spec.nameBn else spec.name, style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                }
            }
        }
    }

    InspectorSlider(if (isBangla) "অপাসিটি" else "Opacity", clip.opacity, 0f..1f, "${(clip.opacity * 100).toInt()}%") { op ->
        onUpdateClip { it.copy(opacity = op) }
    }
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
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(if (isBangla) "কি-ফ্রেম পয়েন্ট (${clip.keyframes.size})" else "Keyframes (${clip.keyframes.size})", style = MaterialTheme.typography.labelMedium, color = CyanPlayhead)
        Button(onClick = onAddKeyframe, colors = ButtonDefaults.buttonColors(containerColor = CyanPlayhead, contentColor = StudioObsidian), shape = RoundedCornerShape(10.dp)) {
            Icon(Icons.Default.Diamond, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(if (isBangla) "+ কি-ফ্রেম" else "+ Keyframe", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
    if (clip.keyframes.isNotEmpty()) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(clip.keyframes, key = { it.id }) { kf ->
                Surface(onClick = { onSeek(clip.startMs + kf.timeOffsetMs) }, color = StudioSurfaceElevated, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, CyanPlayhead.copy(alpha = 0.5f))) {
                    Text("◆ %.2fs".format(kf.timeOffsetMs / 1000f), style = MaterialTheme.typography.labelSmall, color = CyanPlayhead, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }
        }
    }
    InspectorSlider(if (isBangla) "স্কেল (Scale)" else "Scale", clip.scale, 0.25f..3.0f, "%.2fx".format(clip.scale)) { s -> onUpdateClip { it.copy(scale = s) } }
    InspectorSlider(if (isBangla) "রোটেশন (Rotate)" else "Rotation", clip.rotation, -180f..180f, "${clip.rotation.toInt()}°") { r -> onUpdateClip { it.copy(rotation = r) } }
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
    InspectorSlider(if (isBangla) "ব্রাইটনেস" else "Brightness", g.brightness, -0.5f..0.5f, "%.2f".format(g.brightness)) { v -> onUpdateClip { it.copy(colorGrade = g.copy(brightness = v)) } }
    InspectorSlider(if (isBangla) "কন্ট্রাস্ট" else "Contrast", g.contrast, 0.4f..2.0f, "%.2f".format(g.contrast)) { v -> onUpdateClip { it.copy(colorGrade = g.copy(contrast = v)) } }
    InspectorSlider(if (isBangla) "স্যাচুরেশন" else "Saturation", g.saturation, 0f..2.2f, "%.2f".format(g.saturation)) { v -> onUpdateClip { it.copy(colorGrade = g.copy(saturation = v)) } }
    InspectorSlider(if (isBangla) "টেম্পারেচার" else "Temperature", g.temperature, -0.5f..0.5f, "%.2f".format(g.temperature)) { v -> onUpdateClip { it.copy(colorGrade = g.copy(temperature = v)) } }
    InspectorSlider(if (isBangla) "সিনেমা গ্লো" else "Glow", g.glow, 0f..1f, "${(g.glow * 100).toInt()}%") { v -> onUpdateClip { it.copy(colorGrade = g.copy(glow = v)) } }
    InspectorSlider(if (isBangla) "ভিনিয়েট" else "Vignette", g.vignette, 0f..0.9f, "${(g.vignette * 100).toInt()}%") { v -> onUpdateClip { it.copy(colorGrade = g.copy(vignette = v)) } }
}

@Composable
private fun MaskAndBlendInspector(
    clip: TimelineClip,
    isBangla: Boolean,
    onUpdateClip: ((TimelineClip) -> TimelineClip) -> Unit
) {
    // 1. Geometric Borders & Frame Shapes (10 Styles)
    val border = clip.borderConfig
    Text(
        text = if (isBangla) "জ্যামিতিক বর্ডার ও ফ্রেম স্টাইল (১০টি)" else "Geometric Border & Frame Style",
        style = MaterialTheme.typography.labelMedium,
        color = AmberDocumentary,
        fontWeight = FontWeight.Bold
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(BorderShapeStyle.entries, key = { it.name }) { style ->
            FilterChip(
                selected = border.style == style,
                onClick = { onUpdateClip { it.copy(borderConfig = border.copy(style = style)) } },
                label = { Text(if (isBangla) style.labelBn else style.labelEn, style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AmberDocumentary,
                    selectedLabelColor = StudioObsidian,
                    labelColor = TextPrimary
                )
            )
        }
    }

    if (border.style != BorderShapeStyle.NONE) {
        ColorSwatchRow(
            title = if (isBangla) "বর্ডার কালার" else "Border Color",
            selectedHex = border.colorHex,
            onSelect = { hex -> onUpdateClip { it.copy(borderConfig = border.copy(colorHex = hex)) } }
        )
        if (border.style == BorderShapeStyle.DOUBLE_FRAME || border.style == BorderShapeStyle.NEON_DASHED) {
            ColorSwatchRow(
                title = if (isBangla) "সেকেন্ডারি বর্ডার কালার" else "Secondary Border Color",
                selectedHex = border.secondaryColorHex,
                onSelect = { hex -> onUpdateClip { it.copy(borderConfig = border.copy(secondaryColorHex = hex)) } }
            )
        }
        InspectorSlider(if (isBangla) "বর্ডার পুরুত্ব" else "Border Thickness", border.thicknessDp, 1f..20f, "${border.thicknessDp.toInt()}dp") { t ->
            onUpdateClip { it.copy(borderConfig = border.copy(thicknessDp = t)) }
        }
        InspectorSlider(if (isBangla) "নিয়ন গ্লো" else "Border Glow", border.glowRadius, 0f..24f, "${border.glowRadius.toInt()}px") { g ->
            onUpdateClip { it.copy(borderConfig = border.copy(glowRadius = g)) }
        }
        InspectorSlider(if (isBangla) "কর্নার রাউন্ড" else "Corner Radius", border.cornerRadiusDp, 0f..36f, "${border.cornerRadiusDp.toInt()}dp") { r ->
            onUpdateClip { it.copy(borderConfig = border.copy(cornerRadiusDp = r)) }
        }
        InspectorSlider(if (isBangla) "ফ্রেম ইনসেট (ফাঁকা)" else "Frame Inset", border.insetPercent, 0f..0.20f, "${(border.insetPercent * 100).toInt()}%") { ins ->
            onUpdateClip { it.copy(borderConfig = border.copy(insetPercent = ins)) }
        }
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(if (isBangla) "ক্রোমা কী / গ্রিন-স্ক্রিন রিমুভ" else "Green Screen Chroma Key", style = MaterialTheme.typography.bodySmall, color = EmeraldAudio)
        Switch(
            checked = clip.colorGrade.chromaKeyEnabled,
            onCheckedChange = { ck -> onUpdateClip { it.copy(colorGrade = it.colorGrade.copy(chromaKeyEnabled = ck)) } },
            colors = SwitchDefaults.colors(checkedThumbColor = EmeraldAudio)
        )
    }
    if (clip.colorGrade.chromaKeyEnabled) {
        InspectorSlider(
            label = if (isBangla) "ক্রোমা কী তীব্রতা" else "Chroma Key Strength",
            value = clip.colorGrade.chromaKeyStrength,
            range = 0.15f..0.95f,
            valueText = "${(clip.colorGrade.chromaKeyStrength * 100).toInt()}%"
        ) { str ->
            onUpdateClip { it.copy(colorGrade = it.colorGrade.copy(chromaKeyStrength = str)) }
        }
    }
    InspectorSlider(if (isBangla) "ব্যাকগ্রাউন্ড ব্লার" else "BG Blur", clip.colorGrade.blurRadius, 0f..20f, "${clip.colorGrade.blurRadius.toInt()}px") { b ->
        onUpdateClip { it.copy(colorGrade = it.colorGrade.copy(blurRadius = b)) }
    }
    Text(if (isBangla) "মাস্কিং শেপ (Masking)" else "Mask Shape", style = MaterialTheme.typography.labelMedium, color = CyanPlayhead)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(MaskShape.entries, key = { it.name }) { shape ->
            FilterChip(selected = clip.maskConfig.shape == shape, onClick = { onUpdateClip { it.copy(maskConfig = it.maskConfig.copy(shape = shape)) } }, label = { Text(if (isBangla) shape.labelBn else shape.label, style = MaterialTheme.typography.labelSmall) })
        }
    }
    InspectorSlider(if (isBangla) "মাস্ক সাইজ" else "Mask Size", clip.maskConfig.scale, 0.25f..1f, "${(clip.maskConfig.scale * 100).toInt()}%") { s ->
        onUpdateClip { it.copy(maskConfig = it.maskConfig.copy(scale = s)) }
    }
    Text(if (isBangla) "ব্লেন্ডিং মোড" else "Blending Mode", style = MaterialTheme.typography.labelMedium, color = AmberDocumentary)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(BlendModeOption.entries, key = { it.name }) { mode ->
            FilterChip(selected = clip.blendMode == mode, onClick = { onUpdateClip { it.copy(blendMode = mode) } }, label = { Text(mode.label, style = MaterialTheme.typography.labelSmall) })
        }
    }
}

@Composable
private fun AudioMixInspector(
    clip: TimelineClip?,
    allClips: List<TimelineClip>,
    isBangla: Boolean,
    voiceoverRecorder: VoiceoverRecorder,
    onSelectAudioTarget: (String) -> Unit,
    onUpdateClip: ((TimelineClip) -> TimelineClip) -> Unit,
    onPickAudioFile: () -> Unit,
    onVoiceoverRecorded: (String, Long) -> Unit
) {
    val isRecording by voiceoverRecorder.isRecording.collectAsStateWithLifecycle()
    val liveAmp by voiceoverRecorder.liveAmplitude.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) voiceoverRecorder.startRecording(onVoiceoverRecorded)
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = {
                if (isRecording) {
                    voiceoverRecorder.stopRecording()
                } else {
                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                    if (granted) voiceoverRecorder.startRecording(onVoiceoverRecorded)
                    else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = if (isRecording) CoralAction else EmeraldAudio, contentColor = StudioObsidian),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(if (isRecording) Icons.Default.Stop else Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(5.dp))
            Text(if (isRecording) "থামান" else if (isBangla) "ভয়েস রেকর্ড" else "Record VO", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
        Button(
            onClick = onPickAudioFile,
            colors = ButtonDefaults.buttonColors(containerColor = AmberDocumentary, contentColor = StudioObsidian),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            Text(if (isBangla) "+ মিউজিক BGM" else "+ Music File", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }

    if (isRecording) {
        LinearProgressIndicator(progress = { liveAmp }, color = CoralAction, trackColor = StudioObsidian, modifier = Modifier.fillMaxWidth().height(6.dp))
    }

    // All Audio Clips Target Selector (Native Video Sound + Voiceover + Music)
    val audioTargets = remember(allClips) {
        allClips.filter {
            it.trackType == TrackType.VOICEOVER ||
                it.trackType == TrackType.MUSIC ||
                (it.trackType == TrackType.VIDEO && it.mediaSource?.isVideo == true)
        }
    }
    if (audioTargets.isNotEmpty()) {
        Text(
            text = if (isBangla) "সব অডিও ক্লিপ (ভিডিও / ভয়েস / মিউজিক)" else "All Audio Clips",
            style = MaterialTheme.typography.labelSmall,
            color = CyanPlayhead
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(audioTargets, key = { it.id }) { target ->
                val isCurrent = clip?.id == target.id
                val prefix = when (target.trackType) {
                    TrackType.VIDEO -> "🎬"
                    TrackType.VOICEOVER -> "🎙"
                    else -> "🎵"
                }
                val badge = buildString {
                    if (target.audioConfig.isMuted) append(" [M]")
                    if (target.audioConfig.isSolo) append(" [S]")
                }
                FilterChip(
                    selected = isCurrent,
                    onClick = { onSelectAudioTarget(target.id) },
                    label = {
                        Text(
                            "$prefix ${target.title.take(14)}$badge",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyanPlayhead,
                        selectedLabelColor = StudioObsidian,
                        labelColor = TextPrimary
                    )
                )
            }
        }
    }

    if (clip != null) {
        val ac = clip.audioConfig
        // Per-Clip Mute + Solo Switches
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                onClick = { onUpdateClip { it.copy(audioConfig = ac.copy(isMuted = !ac.isMuted)) } },
                color = if (ac.isMuted) CoralAction.copy(alpha = 0.22f) else StudioSurfaceElevated,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, if (ac.isMuted) CoralAction else StudioBorder),
                modifier = Modifier.weight(1f).testTag("audio_mute_toggle")
            ) {
                Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (ac.isMuted) (if (isBangla) "🔇 মিউট অন" else "Muted") else (if (isBangla) "🔊 মিউট" else "Mute"),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (ac.isMuted) CoralAction else TextPrimary
                    )
                }
            }
            Surface(
                onClick = { onUpdateClip { it.copy(audioConfig = ac.copy(isSolo = !ac.isSolo)) } },
                color = if (ac.isSolo) AmberDocumentary.copy(alpha = 0.24f) else StudioSurfaceElevated,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, if (ac.isSolo) AmberDocumentary else StudioBorder),
                modifier = Modifier.weight(1f).testTag("audio_solo_toggle")
            ) {
                Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (ac.isSolo) (if (isBangla) "★ সোলো অন" else "Solo ON") else (if (isBangla) "☆ সোলো" else "Solo"),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (ac.isSolo) AmberDocumentary else TextPrimary
                    )
                }
            }
        }

        InspectorSlider(if (isBangla) "ক্লিপ ভলিউম (0%–200%)" else "Clip Volume", ac.volume, 0f..2.0f, "${(ac.volume * 100).toInt()}%") { v ->
            onUpdateClip { it.copy(audioConfig = ac.copy(volume = v)) }
        }
        InspectorSlider(if (isBangla) "ফেড ইন (Fade In)" else "Fade In", ac.fadeInMs / 1000f, 0f..3.0f, "%.1fs".format(ac.fadeInMs / 1000f)) { s ->
            onUpdateClip { it.copy(audioConfig = ac.copy(fadeInMs = (s * 1000f).toLong())) }
        }
        InspectorSlider(if (isBangla) "ফেড আউট (Fade Out)" else "Fade Out", ac.fadeOutMs / 1000f, 0f..3.0f, "%.1fs".format(ac.fadeOutMs / 1000f)) { s ->
            onUpdateClip { it.copy(audioConfig = ac.copy(fadeOutMs = (s * 1000f).toLong())) }
        }

        if (clip.trackType == TrackType.MUSIC) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (isBangla) "প্রো অটো-ডাকিং (ভয়েসে BGM কমবে)" else "Auto-Ducking under Voice", style = MaterialTheme.typography.bodySmall, color = EmeraldAudio)
                Switch(checked = ac.autoDuckingEnabled, onCheckedChange = { en -> onUpdateClip { it.copy(audioConfig = ac.copy(autoDuckingEnabled = en)) } }, colors = SwitchDefaults.colors(checkedThumbColor = EmeraldAudio))
            }
            if (ac.autoDuckingEnabled) {
                InspectorSlider(if (isBangla) "ডাকিং লেভেল (কতটা কমবে)" else "Ducking Floor", ac.duckingFloor, 0.05f..0.75f, "${(ac.duckingFloor * 100).toInt()}%") { f ->
                    onUpdateClip { it.copy(audioConfig = ac.copy(duckingFloor = f)) }
                }
            }
        } else if (clip.trackType == TrackType.VOICEOVER) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (isBangla) "ভয়েস নয়েজ রিডাকশন (Denoise)" else "Voice Noise Reduction", style = MaterialTheme.typography.bodySmall, color = CyanPlayhead)
                Switch(checked = ac.noiseReductionEnabled, onCheckedChange = { en -> onUpdateClip { it.copy(audioConfig = ac.copy(noiseReductionEnabled = en)) } }, colors = SwitchDefaults.colors(checkedThumbColor = CyanPlayhead))
            }
            if (ac.audioPath != null && ac.audioPath.endsWith(".wav")) {
                Button(onClick = { AudioSynthEngine.playRecordedFile(ac.audioPath) }, colors = ButtonDefaults.buttonColors(containerColor = EmeraldAudio, contentColor = StudioObsidian), shape = RoundedCornerShape(10.dp)) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isBangla) "রেকর্ড করা ভয়েস শুনুন" else "Play Voiceover")
                }
            }
        }
    }
}

@Composable
private fun SfxLibraryInspector(
    clip: TimelineClip,
    isBangla: Boolean,
    onUpdateClip: ((TimelineClip) -> TimelineClip) -> Unit
) {
    var cat by remember { mutableStateOf(SfxCategory.WHOOSH) }
    val filtered = remember(cat) { PresetCatalog.sfxPresets.filter { it.category == cat } }

    Text(if (isBangla) "সাউন্ড ইফেক্ট (52+ SFX)" else "Sound Effects (52+ SFX)", style = MaterialTheme.typography.labelMedium, color = EmeraldAudio)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(SfxCategory.entries, key = { it.name }) { c ->
            FilterChip(selected = cat == c, onClick = { cat = c }, label = { Text(c.label, style = MaterialTheme.typography.labelSmall) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = EmeraldAudio, selectedLabelColor = StudioObsidian, labelColor = TextSecondary))
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
                border = BorderStroke(1.dp, if (locked) EmeraldAudio else StudioBorder)
            ) {
                Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Audiotrack, contentDescription = null, tint = if (locked) EmeraldAudio else TextMuted, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(sfx.name, style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                }
            }
        }
    }
}
