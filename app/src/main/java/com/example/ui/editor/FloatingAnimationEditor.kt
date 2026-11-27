package com.example.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.AnimationCategory
import com.example.domain.model.AnimationCategoryTab
import com.example.domain.model.AnimationDirection
import com.example.domain.model.AnimationEasingType
import com.example.domain.model.AnimationMediaScope
import com.example.domain.model.AnimationSlotConfig
import com.example.domain.model.ClipAnimationProfile
import com.example.domain.model.Keyframe
import com.example.domain.model.KeyframeEasing
import com.example.domain.model.MotionAnimationSpec
import com.example.domain.model.PresetCatalog
import com.example.domain.model.TimelineClip
import com.example.domain.model.UserAnimationPreset
import com.example.ui.theme.AmberDocumentary
import com.example.ui.theme.CoralAction
import com.example.ui.theme.CyanPlayhead
import com.example.ui.theme.EmeraldAudio
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.roundToInt

private enum class FloatingAnimTab(val labelBn: String, val labelEn: String) {
    LIBRARY("লাইব্রেরি", "Library"),
    SETTINGS("সেটিংস", "Settings"),
    ADVANCED("অ্যাডভান্সড", "Advanced"),
    CUSTOM("প্রিসেট", "Custom")
}

private enum class ActiveAnimSlot(val labelBn: String, val labelEn: String) {
    IN("ইন (In)", "In"),
    LOOP("লুপ (Loop)", "Loop"),
    OUT("আউট (Out)", "Out")
}

private data class NumericEditRequest(
    val title: String,
    val initialValue: Float,
    val range: ClosedFloatingPointRange<Float>,
    val onApply: (Float) -> Unit
)

@Composable
fun FloatingAnimationEditor(
    clip: TimelineClip,
    playheadMs: Long,
    isPlaying: Boolean,
    bypassAnimationPreview: Boolean,
    copiedKeyframe: Keyframe?,
    userPresets: List<UserAnimationPreset>,
    isBangla: Boolean,
    minimized: Boolean,
    onToggleMinimize: () -> Unit,
    onUpdateClip: ((TimelineClip) -> TimelineClip) -> Unit,
    onSeek: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    onReplayAnimation: (Long?) -> Unit,
    onToggleBeforeAfter: () -> Unit,
    onAddKeyframe: () -> Unit,
    onDeleteKeyframe: (String) -> Unit,
    onMoveKeyframe: (String, Long) -> Unit,
    onCopyKeyframe: (Keyframe) -> Unit,
    onPasteKeyframe: () -> Unit,
    onUpdateKeyframeEasing: (String, KeyframeEasing) -> Unit,
    onSaveUserPreset: (String, ClipAnimationProfile) -> Unit,
    onRenameUserPreset: (String, String) -> Unit,
    onDeleteUserPreset: (String) -> Unit,
    onApplyUserPreset: (UserAnimationPreset) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    var selectedTab by remember { mutableStateOf(FloatingAnimTab.LIBRARY) }
    var activeSlot by remember { mutableStateOf(ActiveAnimSlot.IN) }
    var categoryTab by remember { mutableStateOf(AnimationCategoryTab.ALL) }
    var previewDurationChoiceMs by remember { mutableStateOf<Long?>(3000L) }
    var numericEdit by remember { mutableStateOf<NumericEditRequest?>(null) }

    var offsetX by remember { mutableFloatStateOf(24f) }
    var offsetY by remember { mutableFloatStateOf(160f) }
    var panelWidthDp by remember { mutableFloatStateOf(348f) }
    var panelHeightDp by remember { mutableFloatStateOf(465f) }

    val profile = clip.animationProfile

    fun applyProfile(nextProfile: ClipAnimationProfile) {
        onUpdateClip { current ->
            current.copy(
                animationProfile = nextProfile,
                inAnimationId = if (nextProfile.inSlot.enabled) nextProfile.inSlot.animationId else "in_none",
                loopAnimationId = if (nextProfile.loopSlot.enabled) nextProfile.loopSlot.animationId else "loop_none",
                outAnimationId = if (nextProfile.outSlot.enabled) nextProfile.outSlot.animationId else "out_none"
            )
        }
    }

    fun currentSlotConfig(): AnimationSlotConfig = when (activeSlot) {
        ActiveAnimSlot.IN -> profile.inSlot
        ActiveAnimSlot.LOOP -> profile.loopSlot
        ActiveAnimSlot.OUT -> profile.outSlot
    }

    fun updateActiveSlot(transform: (AnimationSlotConfig) -> AnimationSlotConfig) {
        val next = when (activeSlot) {
            ActiveAnimSlot.IN -> profile.copy(inSlot = transform(profile.inSlot))
            ActiveAnimSlot.LOOP -> profile.copy(loopSlot = transform(profile.loopSlot))
            ActiveAnimSlot.OUT -> profile.copy(outSlot = transform(profile.outSlot))
        }
        applyProfile(next)
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val maxWpx = with(density) { maxWidth.toPx() }
        val maxHpx = with(density) { maxHeight.toPx() }

        if (minimized) {
            val pillWpx = with(density) { 215.dp.toPx() }
            val pillHpx = with(density) { 48.dp.toPx() }
            Surface(
                color = Color(0xFF15120A),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.5.dp, AmberDocumentary),
                shadowElevation = 10.dp,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            offsetX.coerceIn(8f, (maxWpx - pillWpx - 8f).coerceAtLeast(8f)).roundToInt(),
                            offsetY.coerceIn(16f, (maxHpx - pillHpx - 16f).coerceAtLeast(16f)).roundToInt()
                        )
                    }
                    .pointerInput(maxWpx, maxHpx) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            offsetX = (offsetX + drag.x).coerceIn(8f, (maxWpx - pillWpx - 8f).coerceAtLeast(8f))
                            offsetY = (offsetY + drag.y).coerceIn(16f, (maxHpx - pillHpx - 16f).coerceAtLeast(16f))
                        }
                    }
                    .testTag("floating_anim_minimized_pill")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = "Animation Studio", tint = AmberDocumentary, modifier = Modifier.size(16.dp))
                    Text(
                        text = clip.title.ifBlank { if (isBangla) "অ্যানিমেশন" else "Animation" },
                        color = AmberDocumentary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(100.dp)
                    )
                    IconButton(onClick = onToggleMinimize, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.OpenInFull, contentDescription = "Expand Animation Editor", tint = TextPrimary, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close Animation Editor", tint = TextSecondary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        } else {
            val clampedWidthDp = panelWidthDp.coerceIn(290f, minOf(440f, maxWidth.value - 12f))
            val clampedHeightDp = panelHeightDp.coerceIn(340f, minOf(620f, maxHeight.value - 24f))
            val panelWpx = with(density) { clampedWidthDp.dp.toPx() }
            val panelHpx = with(density) { clampedHeightDp.dp.toPx() }
            val safeX = offsetX.coerceIn(6f, (maxWpx - panelWpx - 6f).coerceAtLeast(6f))
            val safeY = offsetY.coerceIn(12f, (maxHpx - panelHpx - 12f).coerceAtLeast(12f))

            Surface(
                color = Color(0xF50D111C),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.5.dp, AmberDocumentary.copy(alpha = 0.85f)),
                shadowElevation = 16.dp,
                modifier = Modifier
                    .offset { IntOffset(safeX.roundToInt(), safeY.roundToInt()) }
                    .size(width = clampedWidthDp.dp, height = clampedHeightDp.dp)
                    .testTag("floating_anim_editor_panel")
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // 1. DRAGGABLE HEADER BAR
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF151B2B))
                                .pointerInput(maxWpx, maxHpx, panelWpx, panelHpx) {
                                    detectDragGestures { change, drag ->
                                        change.consume()
                                        offsetX = (offsetX + drag.x).coerceIn(6f, (maxWpx - panelWpx - 6f).coerceAtLeast(6f))
                                        offsetY = (offsetY + drag.y).coerceIn(12f, (maxHpx - panelHpx - 12f).coerceAtLeast(12f))
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.DragIndicator, contentDescription = "Drag Panel", tint = AmberDocumentary, modifier = Modifier.size(18.dp))
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AmberDocumentary, modifier = Modifier.size(16.dp))
                                Column {
                                    Text(
                                        text = if (isBangla) "ফ্লোটিং অ্যানিমেশন স্টুডিও" else "Floating Animation Studio",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = AmberDocumentary,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "${clip.title} • %.1fs".format(playheadMs / 1000f),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = CyanPlayhead,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = onToggleMinimize,
                                    modifier = Modifier.size(34.dp).testTag("floating_anim_minimize")
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "Minimize", tint = AmberDocumentary, modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.size(34.dp).testTag("floating_anim_close")
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextPrimary, modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        // 2. LIVE PREVIEW CONTROL STRIP
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0A0E18))
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                onClick = onTogglePlayPause,
                                color = if (isPlaying) CoralAction.copy(alpha = 0.2f) else AmberDocumentary,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (isPlaying) CoralAction else AmberDocumentary),
                                modifier = Modifier.testTag("anim_preview_play_stop")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                        contentDescription = if (isPlaying) "Stop" else "Play",
                                        tint = if (isPlaying) CoralAction else StudioObsidian,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = if (isPlaying) (if (isBangla) "থামান" else "Stop") else (if (isBangla) "প্লে" else "Play"),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isPlaying) CoralAction else StudioObsidian
                                    )
                                }
                            }

                            Surface(
                                onClick = { onReplayAnimation(previewDurationChoiceMs) },
                                color = Color(0xFF152036),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, CyanPlayhead.copy(alpha = 0.7f)),
                                modifier = Modifier.testTag("anim_preview_replay")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Replay, contentDescription = "Replay", tint = CyanPlayhead, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(if (isBangla) "রিপ্লে" else "Replay", fontSize = 11.sp, color = CyanPlayhead, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            Surface(
                                onClick = onToggleBeforeAfter,
                                color = if (bypassAnimationPreview) CoralAction.copy(alpha = 0.22f) else Color(0xFF141B29),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (bypassAnimationPreview) CoralAction else StudioBorder),
                                modifier = Modifier.testTag("anim_before_after_toggle")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Compare, contentDescription = "Before/After", tint = if (bypassAnimationPreview) CoralAction else TextSecondary, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = if (bypassAnimationPreview) (if (isBangla) "আগে (অফ)" else "Before") else (if (isBangla) "পরে (অন)" else "After"),
                                        fontSize = 11.sp,
                                        color = if (bypassAnimationPreview) CoralAction else TextPrimary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            listOf(
                                1000L to "1s",
                                2000L to "2s",
                                3000L to "3s",
                                5000L to "5s",
                                null to (if (isBangla) "ফুল" else "Full")
                            ).forEach { (durMs, lbl) ->
                                val activeDur = previewDurationChoiceMs == durMs
                                Surface(
                                    onClick = { previewDurationChoiceMs = durMs },
                                    color = if (activeDur) AmberDocumentary.copy(alpha = 0.22f) else StudioSurfaceElevated,
                                    shape = RoundedCornerShape(7.dp),
                                    border = BorderStroke(1.dp, if (activeDur) AmberDocumentary else StudioBorder)
                                ) {
                                    Text(
                                        text = lbl,
                                        fontSize = 10.sp,
                                        color = if (activeDur) AmberDocumentary else TextSecondary,
                                        fontWeight = if (activeDur) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        // 3. SIMULTANEOUS IN + LOOP + OUT SLOT STRIP
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ActiveAnimSlot.entries.forEach { slot ->
                                val slotCfg = when (slot) {
                                    ActiveAnimSlot.IN -> profile.inSlot
                                    ActiveAnimSlot.LOOP -> profile.loopSlot
                                    ActiveAnimSlot.OUT -> profile.outSlot
                                }
                                val isSel = activeSlot == slot
                                Surface(
                                    onClick = { activeSlot = slot },
                                    color = if (isSel) Color(0xFF2B220E) else Color(0xFF131824),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, if (isSel) AmberDocumentary else StudioBorder),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = if (isBangla) slot.labelBn else slot.labelEn,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSel) AmberDocumentary else TextSecondary,
                                            maxLines = 1
                                        )
                                        Switch(
                                            checked = slotCfg.enabled,
                                            onCheckedChange = { enabled ->
                                                val next = when (slot) {
                                                    ActiveAnimSlot.IN -> profile.copy(inSlot = profile.inSlot.copy(enabled = enabled))
                                                    ActiveAnimSlot.LOOP -> profile.copy(loopSlot = profile.loopSlot.copy(enabled = enabled))
                                                    ActiveAnimSlot.OUT -> profile.copy(outSlot = profile.outSlot.copy(enabled = enabled))
                                                }
                                                applyProfile(next)
                                            },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = StudioObsidian,
                                                checkedTrackColor = AmberDocumentary,
                                                uncheckedThumbColor = TextMuted,
                                                uncheckedTrackColor = StudioObsidian
                                            ),
                                            modifier = Modifier.height(22.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // 4. 4 MAIN EDITOR TABS
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            FloatingAnimTab.entries.forEach { tab ->
                                val isActive = selectedTab == tab
                                Surface(
                                    onClick = { selectedTab = tab },
                                    color = if (isActive) AmberDocumentary else StudioSurfaceElevated,
                                    shape = RoundedCornerShape(9.dp),
                                    border = BorderStroke(1.dp, if (isActive) AmberDocumentary else StudioBorder),
                                    modifier = Modifier.weight(1f).testTag("anim_tab_${tab.name.lowercase()}")
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (isBangla) tab.labelBn else tab.labelEn,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isActive) StudioObsidian else TextPrimary,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }

                        // 5. SCROLLABLE CONTENT BODY
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            when (selectedTab) {
                                FloatingAnimTab.LIBRARY -> {
                                    AnimationLibraryContent(
                                        profile = profile,
                                        categoryTab = categoryTab,
                                        userPresets = userPresets,
                                        isBangla = isBangla,
                                        onSelectCategory = { categoryTab = it },
                                        onSelectSpec = { spec ->
                                            val isNone = spec.id == "in_none" || spec.id == "out_none" || spec.id == "loop_none"
                                            val next = when (spec.category) {
                                                AnimationCategory.IN -> {
                                                    activeSlot = ActiveAnimSlot.IN
                                                    profile.copy(inSlot = profile.inSlot.copy(animationId = spec.id, enabled = !isNone))
                                                }
                                                AnimationCategory.OUT -> {
                                                    activeSlot = ActiveAnimSlot.OUT
                                                    profile.copy(outSlot = profile.outSlot.copy(animationId = spec.id, enabled = !isNone))
                                                }
                                                AnimationCategory.LOOP -> {
                                                    activeSlot = ActiveAnimSlot.LOOP
                                                    profile.copy(loopSlot = profile.loopSlot.copy(animationId = spec.id, enabled = !isNone))
                                                }
                                            }
                                            applyProfile(next)
                                        },
                                        onApplyUserPreset = onApplyUserPreset,
                                        onOpenCustomTab = { selectedTab = FloatingAnimTab.CUSTOM }
                                    )
                                }

                                FloatingAnimTab.SETTINGS -> {
                                    SlotTimingSettingsContent(
                                        activeSlot = activeSlot,
                                        slotConfig = currentSlotConfig(),
                                        isBangla = isBangla,
                                        onUpdateSlot = { fn -> updateActiveSlot(fn) },
                                        onRequestNumericEdit = { numericEdit = it }
                                    )
                                }

                                FloatingAnimTab.ADVANCED -> {
                                    AdvancedControlsAndKeyframesContent(
                                        clip = clip,
                                        profile = profile,
                                        copiedKeyframe = copiedKeyframe,
                                        isBangla = isBangla,
                                        onUpdateClip = onUpdateClip,
                                        onUpdateProfile = { applyProfile(it) },
                                        onSeek = onSeek,
                                        onAddKeyframe = onAddKeyframe,
                                        onDeleteKeyframe = onDeleteKeyframe,
                                        onMoveKeyframe = onMoveKeyframe,
                                        onCopyKeyframe = onCopyKeyframe,
                                        onPasteKeyframe = onPasteKeyframe,
                                        onUpdateKeyframeEasing = onUpdateKeyframeEasing,
                                        onRequestNumericEdit = { numericEdit = it }
                                    )
                                }

                                FloatingAnimTab.CUSTOM -> {
                                    CustomPresetsTabContent(
                                        profile = profile,
                                        userPresets = userPresets,
                                        isBangla = isBangla,
                                        onSavePreset = onSaveUserPreset,
                                        onRenamePreset = onRenameUserPreset,
                                        onDeletePreset = onDeleteUserPreset,
                                        onApplyPreset = onApplyUserPreset
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                        }
                    }

                    // 6. BOTTOM-RIGHT RESIZE HANDLE
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(28.dp)
                            .clip(RoundedCornerShape(topStart = 10.dp))
                            .background(AmberDocumentary.copy(alpha = 0.24f))
                            .pointerInput(Unit) {
                                detectDragGestures { change, drag ->
                                    change.consume()
                                    val dxDp = with(density) { drag.x.toDp().value }
                                    val dyDp = with(density) { drag.y.toDp().value }
                                    panelWidthDp = (panelWidthDp + dxDp).coerceIn(290f, 440f)
                                    panelHeightDp = (panelHeightDp + dyDp).coerceIn(340f, 620f)
                                }
                            }
                            .testTag("floating_anim_resize_handle"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("◢", fontSize = 11.sp, color = AmberDocumentary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    numericEdit?.let { req ->
        var txt by remember(req) { mutableStateOf("%.2f".format(req.initialValue)) }
        AlertDialog(
            onDismissRequest = { numericEdit = null },
            containerColor = StudioSurfaceElevated,
            title = { Text(req.title, color = AmberDocumentary, style = MaterialTheme.typography.titleSmall) },
            text = {
                OutlinedTextField(
                    value = txt,
                    onValueChange = { txt = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    label = { Text("${req.range.start} .. ${req.range.endInclusive}") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AmberDocumentary,
                        unfocusedBorderColor = StudioBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        txt.toFloatOrNull()?.let { parsed ->
                            req.onApply(parsed.coerceIn(req.range.start, req.range.endInclusive))
                        }
                        numericEdit = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberDocumentary, contentColor = StudioObsidian)
                ) { Text(if (isBangla) "প্রয়োগ" else "Apply", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { numericEdit = null }) {
                    Text(if (isBangla) "বাতিল" else "Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun AnimationLibraryContent(
    profile: ClipAnimationProfile,
    categoryTab: AnimationCategoryTab,
    userPresets: List<UserAnimationPreset>,
    isBangla: Boolean,
    onSelectCategory: (AnimationCategoryTab) -> Unit,
    onSelectSpec: (MotionAnimationSpec) -> Unit,
    onApplyUserPreset: (UserAnimationPreset) -> Unit,
    onOpenCustomTab: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        AnimationCategoryTab.entries.forEach { cat ->
            val selected = categoryTab == cat
            FilterChip(
                selected = selected,
                onClick = { onSelectCategory(cat) },
                label = { Text(if (isBangla) cat.labelBn else cat.labelEn, style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AmberDocumentary,
                    selectedLabelColor = StudioObsidian,
                    labelColor = TextSecondary
                )
            )
        }
    }

    if (categoryTab == AnimationCategoryTab.CUSTOM) {
        if (userPresets.isEmpty()) {
            Surface(
                color = StudioSurfaceElevated,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, StudioBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isBangla) "এখনো কোনো কাস্টম অ্যানিমেশন প্রিসেট সেভ করা হয়নি" else "No custom animation presets saved yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Button(
                        onClick = onOpenCustomTab,
                        colors = ButtonDefaults.buttonColors(containerColor = AmberDocumentary, contentColor = StudioObsidian)
                    ) {
                        Text(if (isBangla) "+ নতুন প্রিসেট তৈরি করুন" else "+ Create Custom Preset", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            userPresets.forEach { preset ->
                Surface(
                    onClick = { onApplyUserPreset(preset) },
                    color = Color(0xFF1B1910),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AmberDocumentary.copy(alpha = 0.65f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AmberDocumentary, modifier = Modifier.size(16.dp))
                            Text(preset.name, color = TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(if (isBangla) "প্রয়োগ করুন" else "Apply", color = AmberDocumentary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        return
    }

    val filtered = remember(categoryTab) {
        PresetCatalog.motionAnimations.filter { spec ->
            when (categoryTab) {
                AnimationCategoryTab.ALL -> true
                AnimationCategoryTab.IN -> spec.category == AnimationCategory.IN
                AnimationCategoryTab.OUT -> spec.category == AnimationCategory.OUT
                AnimationCategoryTab.LOOP -> spec.category == AnimationCategory.LOOP
                AnimationCategoryTab.TEXT -> spec.scope == AnimationMediaScope.TEXT || spec.scope == AnimationMediaScope.ALL
                AnimationCategoryTab.IMAGE -> spec.scope == AnimationMediaScope.IMAGE || spec.scope == AnimationMediaScope.ALL
                AnimationCategoryTab.VIDEO -> spec.scope == AnimationMediaScope.VIDEO || spec.scope == AnimationMediaScope.ALL
                AnimationCategoryTab.CUSTOM -> false
            }
        }
    }

    Text(
        text = if (isBangla) "${filtered.size}টি রিয়েল-টাইম অ্যানিমেশন" else "${filtered.size} Real-Time Animations",
        style = MaterialTheme.typography.labelSmall,
        color = CyanPlayhead
    )

    val rows = remember(filtered) { filtered.chunked(2) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { pair ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { spec ->
                    val isSelected = when (spec.category) {
                        AnimationCategory.IN -> profile.inSlot.enabled && profile.inSlot.animationId == spec.id
                        AnimationCategory.OUT -> profile.outSlot.enabled && profile.outSlot.animationId == spec.id
                        AnimationCategory.LOOP -> profile.loopSlot.enabled && profile.loopSlot.animationId == spec.id
                    }
                    val badgeColor = when (spec.category) {
                        AnimationCategory.IN -> AmberDocumentary
                        AnimationCategory.LOOP -> CyanPlayhead
                        AnimationCategory.OUT -> CoralAction
                    }
                    Surface(
                        onClick = { onSelectSpec(spec) },
                        color = if (isSelected) Color(0xFF28200D) else StudioSurfaceElevated,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) AmberDocumentary else StudioBorder),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("anim_card_${spec.id}")
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Animation,
                                    contentDescription = null,
                                    tint = if (isSelected) AmberDocumentary else badgeColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = spec.category.name,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = badgeColor
                                )
                            }
                            Text(
                                text = if (isBangla) spec.nameBn else spec.name,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) AmberDocumentary else TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = spec.name,
                                fontSize = 10.sp,
                                color = TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (pair.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SlotTimingSettingsContent(
    activeSlot: ActiveAnimSlot,
    slotConfig: AnimationSlotConfig,
    isBangla: Boolean,
    onUpdateSlot: ((AnimationSlotConfig) -> AnimationSlotConfig) -> Unit,
    onRequestNumericEdit: (NumericEditRequest) -> Unit
) {
    Text(
        text = if (isBangla) "${activeSlot.labelBn} টাইমিং ও কার্ভ" else "${activeSlot.labelEn} Timing & Easing",
        style = MaterialTheme.typography.labelMedium,
        color = AmberDocumentary,
        fontWeight = FontWeight.Bold
    )

    NumericInspectorSlider(
        label = if (isBangla) "সময়কাল (Duration)" else "Duration (sec)",
        value = slotConfig.durationMs / 1000f,
        range = 0.2f..15.0f,
        valueText = "%.2fs".format(slotConfig.durationMs / 1000f),
        onValueChange = { sec -> onUpdateSlot { it.copy(durationMs = (sec * 1000f).toLong().coerceIn(200L, 15_000L)) } },
        onTapNumber = {
            onRequestNumericEdit(
                NumericEditRequest(
                    title = if (isBangla) "সময়কাল (0.2 - 15.0 সেকেন্ড)" else "Duration (s)",
                    initialValue = slotConfig.durationMs / 1000f,
                    range = 0.2f..15.0f
                ) { sec -> onUpdateSlot { it.copy(durationMs = (sec * 1000f).toLong().coerceIn(200L, 15_000L)) } }
            )
        }
    )

    NumericInspectorSlider(
        label = if (isBangla) "বিলম্ব (Delay)" else "Delay (sec)",
        value = slotConfig.delayMs / 1000f,
        range = 0f..5.0f,
        valueText = "%.2fs".format(slotConfig.delayMs / 1000f),
        onValueChange = { sec -> onUpdateSlot { it.copy(delayMs = (sec * 1000f).toLong().coerceIn(0L, 5_000L)) } },
        onTapNumber = {
            onRequestNumericEdit(
                NumericEditRequest(
                    title = if (isBangla) "ডিলে (সেকেন্ড)" else "Delay (s)",
                    initialValue = slotConfig.delayMs / 1000f,
                    range = 0f..5.0f
                ) { sec -> onUpdateSlot { it.copy(delayMs = (sec * 1000f).toLong().coerceIn(0L, 5_000L)) } }
            )
        }
    )

    NumericInspectorSlider(
        label = if (isBangla) "গতি (Speed)" else "Speed",
        value = slotConfig.speed,
        range = 0.2f..3.0f,
        valueText = "%.2fx".format(slotConfig.speed),
        onValueChange = { sp -> onUpdateSlot { it.copy(speed = sp) } },
        onTapNumber = {
            onRequestNumericEdit(NumericEditRequest(if (isBangla) "স্পিড (0.2 - 3.0)" else "Speed", slotConfig.speed, 0.2f..3.0f) { sp ->
                onUpdateSlot { it.copy(speed = sp) }
            })
        }
    )

    NumericInspectorSlider(
        label = if (isBangla) "তীব্রতা (Intensity)" else "Intensity",
        value = slotConfig.intensity,
        range = 0.1f..2.0f,
        valueText = "${(slotConfig.intensity * 100).toInt()}%",
        onValueChange = { v -> onUpdateSlot { it.copy(intensity = v) } },
        onTapNumber = {
            onRequestNumericEdit(NumericEditRequest(if (isBangla) "তীব্রতা (0.1 - 2.0)" else "Intensity", slotConfig.intensity, 0.1f..2.0f) { v ->
                onUpdateSlot { it.copy(intensity = v) }
            })
        }
    )

    NumericInspectorSlider(
        label = if (isBangla) "শুরুর সময় অফসেট (Start)" else "Start Offset",
        value = slotConfig.startOffsetMs / 1000f,
        range = 0f..3.0f,
        valueText = "%.2fs".format(slotConfig.startOffsetMs / 1000f),
        onValueChange = { s -> onUpdateSlot { it.copy(startOffsetMs = (s * 1000f).toLong()) } },
        onTapNumber = {
            onRequestNumericEdit(NumericEditRequest(if (isBangla) "শুরু অফসেট (s)" else "Start Offset", slotConfig.startOffsetMs / 1000f, 0f..3.0f) { s ->
                onUpdateSlot { it.copy(startOffsetMs = (s * 1000f).toLong()) }
            })
        }
    )

    NumericInspectorSlider(
        label = if (isBangla) "শেষের সময় অফসেট (End)" else "End Offset",
        value = slotConfig.endOffsetMs / 1000f,
        range = 0f..3.0f,
        valueText = "%.2fs".format(slotConfig.endOffsetMs / 1000f),
        onValueChange = { s -> onUpdateSlot { it.copy(endOffsetMs = (s * 1000f).toLong()) } },
        onTapNumber = {
            onRequestNumericEdit(NumericEditRequest(if (isBangla) "শেষ অফসেট (s)" else "End Offset", slotConfig.endOffsetMs / 1000f, 0f..3.0f) { s ->
                onUpdateSlot { it.copy(endOffsetMs = (s * 1000f).toLong()) }
            })
        }
    )

    // Repeat count
    Text(if (isBangla) "রিপিট সংখ্যা (Repeat)" else "Repeat Count", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(1 to "1x", 2 to "2x", 3 to "3x", 5 to "5x", 0 to "∞").forEach { (rep, label) ->
            FilterChip(
                selected = slotConfig.repeatCount == rep,
                onClick = { onUpdateSlot { it.copy(repeatCount = rep) } },
                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AmberDocumentary, selectedLabelColor = StudioObsidian)
            )
        }
    }

    // 7 Directions
    Text(if (isBangla) "দিকনির্দেশনা (৭টি দিক)" else "Direction (7 Modes)", style = MaterialTheme.typography.labelSmall, color = CyanPlayhead)
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        AnimationDirection.entries.forEach { dir ->
            FilterChip(
                selected = slotConfig.direction == dir,
                onClick = { onUpdateSlot { it.copy(direction = dir) } },
                label = { Text(if (isBangla) dir.labelBn else dir.labelEn, style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CyanPlayhead, selectedLabelColor = StudioObsidian)
            )
        }
    }

    // 6 Easings + Cubic Bezier Preview
    Text(if (isBangla) "ইজিং ও কার্ভ (Easing)" else "Easing Curve", style = MaterialTheme.typography.labelSmall, color = AmberDocumentary)
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        AnimationEasingType.entries.forEach { ease ->
            FilterChip(
                selected = slotConfig.easing == ease,
                onClick = { onUpdateSlot { it.copy(easing = ease) } },
                label = { Text(ease.label, style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AmberDocumentary, selectedLabelColor = StudioObsidian)
            )
        }
    }

    if (slotConfig.easing == AnimationEasingType.CUBIC_BEZIER || slotConfig.easing == AnimationEasingType.CUSTOM_CURVE) {
        Surface(
            color = Color(0xFF090D16),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, AmberDocumentary.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Bezier (%.2f, %.2f, %.2f, %.2f)".format(slotConfig.bezierX1, slotConfig.bezierY1, slotConfig.bezierX2, slotConfig.bezierY2), fontSize = 11.sp, color = AmberDocumentary)
                Canvas(modifier = Modifier.fillMaxWidth().height(92.dp)) {
                    val pad = 12f
                    val w = size.width - pad * 2f
                    val h = size.height - pad * 2f
                    drawRect(color = Color(0xFF1B2336), topLeft = Offset(pad, pad), size = androidx.compose.ui.geometry.Size(w, h), style = Stroke(1.5f))
                    val p0 = Offset(pad, pad + h)
                    val p3 = Offset(pad + w, pad)
                    val c1 = Offset(pad + slotConfig.bezierX1 * w, pad + (1f - slotConfig.bezierY1.coerceIn(-0.2f, 1.2f)) * h)
                    val c2 = Offset(pad + slotConfig.bezierX2 * w, pad + (1f - slotConfig.bezierY2.coerceIn(-0.2f, 1.2f)) * h)
                    drawLine(CyanPlayhead.copy(alpha = 0.5f), p0, c1, strokeWidth = 2f)
                    drawLine(EmeraldAudio.copy(alpha = 0.5f), p3, c2, strokeWidth = 2f)
                    val path = Path().apply {
                        moveTo(p0.x, p0.y)
                        cubicTo(c1.x, c1.y, c2.x, c2.y, p3.x, p3.y)
                    }
                    drawPath(path, color = AmberDocumentary, style = Stroke(width = 4f, cap = StrokeCap.Round))
                    drawCircle(CyanPlayhead, radius = 7f, center = c1)
                    drawCircle(EmeraldAudio, radius = 7f, center = c2)
                }
                InspectorSlider("P1 X", slotConfig.bezierX1, 0f..1f, "%.2f".format(slotConfig.bezierX1)) { x1 -> onUpdateSlot { it.copy(bezierX1 = x1) } }
                InspectorSlider("P1 Y", slotConfig.bezierY1, 0f..1.4f, "%.2f".format(slotConfig.bezierY1)) { y1 -> onUpdateSlot { it.copy(bezierY1 = y1) } }
                InspectorSlider("P2 X", slotConfig.bezierX2, 0f..1f, "%.2f".format(slotConfig.bezierX2)) { x2 -> onUpdateSlot { it.copy(bezierX2 = x2) } }
                InspectorSlider("P2 Y", slotConfig.bezierY2, -0.3f..1.3f, "%.2f".format(slotConfig.bezierY2)) { y2 -> onUpdateSlot { it.copy(bezierY2 = y2) } }
            }
        }
    }
}

@Composable
private fun AdvancedControlsAndKeyframesContent(
    clip: TimelineClip,
    profile: ClipAnimationProfile,
    copiedKeyframe: Keyframe?,
    isBangla: Boolean,
    onUpdateClip: ((TimelineClip) -> TimelineClip) -> Unit,
    onUpdateProfile: (ClipAnimationProfile) -> Unit,
    onSeek: (Long) -> Unit,
    onAddKeyframe: () -> Unit,
    onDeleteKeyframe: (String) -> Unit,
    onMoveKeyframe: (String, Long) -> Unit,
    onCopyKeyframe: (Keyframe) -> Unit,
    onPasteKeyframe: () -> Unit,
    onUpdateKeyframeEasing: (String, KeyframeEasing) -> Unit,
    onRequestNumericEdit: (NumericEditRequest) -> Unit
) {
    Text(
        text = if (isBangla) "অ্যাডভান্সড ট্রান্সফর্ম (স্লাইডার বা সংখ্যায় ট্যাপ)" else "10 Advanced Controls (Slider or Tap Value)",
        style = MaterialTheme.typography.labelMedium,
        color = AmberDocumentary,
        fontWeight = FontWeight.Bold
    )

    NumericInspectorSlider("1. Pos X", clip.posX, -0.6f..0.6f, "%.2f".format(clip.posX), { v -> onUpdateClip { it.copy(posX = v) } }) {
        onRequestNumericEdit(NumericEditRequest("Pos X (-0.6 .. 0.6)", clip.posX, -0.6f..0.6f) { v -> onUpdateClip { it.copy(posX = v) } })
    }
    NumericInspectorSlider("2. Pos Y", clip.posY, -0.6f..0.6f, "%.2f".format(clip.posY), { v -> onUpdateClip { it.copy(posY = v) } }) {
        onRequestNumericEdit(NumericEditRequest("Pos Y (-0.6 .. 0.6)", clip.posY, -0.6f..0.6f) { v -> onUpdateClip { it.copy(posY = v) } })
    }
    NumericInspectorSlider("3. Scale", clip.scale, 0.2f..3.5f, "%.2fx".format(clip.scale), { v -> onUpdateClip { it.copy(scale = v) } }) {
        onRequestNumericEdit(NumericEditRequest("Scale (0.2 .. 3.5)", clip.scale, 0.2f..3.5f) { v -> onUpdateClip { it.copy(scale = v) } })
    }
    NumericInspectorSlider("4. Rotation", clip.rotation, -360f..360f, "${clip.rotation.toInt()}°", { v -> onUpdateClip { it.copy(rotation = v) } }) {
        onRequestNumericEdit(NumericEditRequest("Rotation (-360 .. 360)", clip.rotation, -360f..360f) { v -> onUpdateClip { it.copy(rotation = v) } })
    }
    NumericInspectorSlider("5. Opacity", clip.opacity, 0f..1f, "${(clip.opacity * 100).toInt()}%", { v -> onUpdateClip { it.copy(opacity = v) } }) {
        onRequestNumericEdit(NumericEditRequest("Opacity (0.0 .. 1.0)", clip.opacity, 0f..1f) { v -> onUpdateClip { it.copy(opacity = v) } })
    }
    NumericInspectorSlider("6. Blur", profile.blurRadius, 0f..32f, "%.1fpx".format(profile.blurRadius), { v -> onUpdateProfile(profile.copy(blurRadius = v)) }) {
        onRequestNumericEdit(NumericEditRequest("Blur (0 .. 32)", profile.blurRadius, 0f..32f) { v -> onUpdateProfile(profile.copy(blurRadius = v)) })
    }
    NumericInspectorSlider("7. Shadow", profile.shadowStrength, 0f..2f, "%.2f".format(profile.shadowStrength), { v -> onUpdateProfile(profile.copy(shadowStrength = v)) }) {
        onRequestNumericEdit(NumericEditRequest("Shadow (0 .. 2)", profile.shadowStrength, 0f..2f) { v -> onUpdateProfile(profile.copy(shadowStrength = v)) })
    }
    NumericInspectorSlider("8a. Skew X", profile.skewX, -1.2f..1.2f, "%.2f".format(profile.skewX), { v -> onUpdateProfile(profile.copy(skewX = v)) }) {
        onRequestNumericEdit(NumericEditRequest("Skew X (-1.2 .. 1.2)", profile.skewX, -1.2f..1.2f) { v -> onUpdateProfile(profile.copy(skewX = v)) })
    }
    NumericInspectorSlider("8b. Skew Y", profile.skewY, -1.2f..1.2f, "%.2f".format(profile.skewY), { v -> onUpdateProfile(profile.copy(skewY = v)) }) {
        onRequestNumericEdit(NumericEditRequest("Skew Y (-1.2 .. 1.2)", profile.skewY, -1.2f..1.2f) { v -> onUpdateProfile(profile.copy(skewY = v)) })
    }
    NumericInspectorSlider("9. Perspective", profile.perspective, -60f..60f, "${profile.perspective.toInt()}°", { v -> onUpdateProfile(profile.copy(perspective = v)) }) {
        onRequestNumericEdit(NumericEditRequest("3D Perspective (-60 .. 60)", profile.perspective, -60f..60f) { v -> onUpdateProfile(profile.copy(perspective = v)) })
    }
    NumericInspectorSlider("10a. Anchor X", profile.anchorX, 0f..1f, "%.2f".format(profile.anchorX), { v -> onUpdateProfile(profile.copy(anchorX = v)) }) {
        onRequestNumericEdit(NumericEditRequest("Anchor X (0.0 .. 1.0)", profile.anchorX, 0f..1f) { v -> onUpdateProfile(profile.copy(anchorX = v)) })
    }
    NumericInspectorSlider("10b. Anchor Y", profile.anchorY, 0f..1f, "%.2f".format(profile.anchorY), { v -> onUpdateProfile(profile.copy(anchorY = v)) }) {
        onRequestNumericEdit(NumericEditRequest("Anchor Y (0.0 .. 1.0)", profile.anchorY, 0f..1f) { v -> onUpdateProfile(profile.copy(anchorY = v)) })
    }

    // Keyframe Timeline System
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (isBangla) "কি-ফ্রেম ম্যানেজার (${clip.keyframes.size})" else "Keyframes (${clip.keyframes.size})",
            style = MaterialTheme.typography.labelMedium,
            color = CyanPlayhead,
            fontWeight = FontWeight.Bold
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(
                onClick = onAddKeyframe,
                colors = ButtonDefaults.buttonColors(containerColor = CyanPlayhead, contentColor = StudioObsidian),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(Icons.Default.Diamond, contentDescription = null, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text(if (isBangla) "+ যোগ" else "+ Add", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Surface(
                onClick = { if (copiedKeyframe != null) onPasteKeyframe() },
                color = if (copiedKeyframe != null) AmberDocumentary.copy(alpha = 0.22f) else StudioSurfaceElevated,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, if (copiedKeyframe != null) AmberDocumentary else StudioBorder)
            ) {
                Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ContentPaste, contentDescription = "Paste Keyframe", tint = if (copiedKeyframe != null) AmberDocumentary else TextMuted, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(if (isBangla) "পেস্ট" else "Paste", fontSize = 11.sp, color = if (copiedKeyframe != null) AmberDocumentary else TextMuted)
                }
            }
        }
    }

    clip.keyframes.sortedBy { it.timeOffsetMs }.forEach { kf ->
        Surface(
            color = StudioSurfaceElevated,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, CyanPlayhead.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "◆ %.2fs  (S:%.2fx R:%d° O:%d%%)".format(
                            kf.timeOffsetMs / 1000f,
                            kf.scale,
                            kf.rotation.toInt(),
                            (kf.opacity * 100).toInt()
                        ),
                        fontSize = 11.sp,
                        color = CyanPlayhead,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onSeek(clip.startMs + kf.timeOffsetMs) }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "-0.2s",
                            fontSize = 10.sp,
                            color = AmberDocumentary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(StudioObsidian)
                                .clickable { onMoveKeyframe(kf.id, -200L) }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                        Text(
                            "+0.2s",
                            fontSize = 10.sp,
                            color = AmberDocumentary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(StudioObsidian)
                                .clickable { onMoveKeyframe(kf.id, 200L) }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copy Keyframe",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp).clickable { onCopyKeyframe(kf) }
                        )
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Delete Keyframe",
                            tint = CoralAction,
                            modifier = Modifier.size(16.dp).clickable { onDeleteKeyframe(kf.id) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    KeyframeEasing.entries.forEach { ke ->
                        val activeKe = kf.easing == ke
                        Text(
                            text = ke.label,
                            fontSize = 9.sp,
                            color = if (activeKe) StudioObsidian else TextSecondary,
                            fontWeight = if (activeKe) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (activeKe) AmberDocumentary else StudioObsidian)
                                .clickable { onUpdateKeyframeEasing(kf.id, ke) }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomPresetsTabContent(
    profile: ClipAnimationProfile,
    userPresets: List<UserAnimationPreset>,
    isBangla: Boolean,
    onSavePreset: (String, ClipAnimationProfile) -> Unit,
    onRenamePreset: (String, String) -> Unit,
    onDeletePreset: (String) -> Unit,
    onApplyPreset: (UserAnimationPreset) -> Unit
) {
    var presetName by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<UserAnimationPreset?>(null) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = presetName,
            onValueChange = { presetName = it },
            label = { Text(if (isBangla) "প্রিসেটের নাম" else "Preset Name") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AmberDocumentary,
                unfocusedBorderColor = StudioBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            modifier = Modifier.weight(1f)
        )
        Button(
            onClick = {
                val clean = presetName.trim().ifBlank { "My Motion ${userPresets.size + 1}" }
                onSavePreset(clean, profile)
                presetName = ""
            },
            colors = ButtonDefaults.buttonColors(containerColor = AmberDocumentary, contentColor = StudioObsidian),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.testTag("save_custom_anim_preset_btn")
        ) {
            Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(if (isBangla) "সেভ" else "Save", fontWeight = FontWeight.Bold)
        }
    }

    Text(
        text = if (isBangla) "সেভ করা কাস্টম প্রিসেট (${userPresets.size})" else "Saved Custom Presets (${userPresets.size})",
        style = MaterialTheme.typography.labelMedium,
        color = AmberDocumentary
    )

    userPresets.forEach { preset ->
        Surface(
            color = StudioSurfaceElevated,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, StudioBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).clickable { onApplyPreset(preset) }) {
                    Text(preset.name, color = TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = "In: ${preset.profile.inSlot.animationId} | Loop: ${preset.profile.loopSlot.animationId}",
                        fontSize = 10.sp,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Button(
                        onClick = { onApplyPreset(preset) },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanPlayhead, contentColor = StudioObsidian),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(7.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text(if (isBangla) "প্রয়োগ" else "Apply", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = { renameTarget = preset }, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Rename Preset", tint = AmberDocumentary, modifier = Modifier.size(15.dp))
                    }
                    IconButton(onClick = { onDeletePreset(preset.id) }, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Preset", tint = CoralAction, modifier = Modifier.size(15.dp))
                    }
                }
            }
        }
    }

    renameTarget?.let { target ->
        var nextName by remember(target.id) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            containerColor = StudioSurfaceElevated,
            title = { Text(if (isBangla) "প্রিসেট রিনেম করুন" else "Rename Preset", color = AmberDocumentary) },
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
                        onRenamePreset(target.id, nextName)
                        renameTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberDocumentary, contentColor = StudioObsidian)
                ) { Text(if (isBangla) "আপডেট" else "Rename", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text(if (isBangla) "বাতিল" else "Cancel", color = TextSecondary) }
            }
        )
    }
}

@Composable
private fun NumericInspectorSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    onValueChange: (Float) -> Unit,
    onTapNumber: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Surface(
                onClick = onTapNumber,
                color = Color(0xFF1F1A0F),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, AmberDocumentary.copy(alpha = 0.6f))
            ) {
                Text(
                    text = "$valueText ✎",
                    fontSize = 11.sp,
                    color = AmberDocumentary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = AmberDocumentary,
                activeTrackColor = AmberDocumentary,
                inactiveTrackColor = StudioObsidian
            ),
            modifier = Modifier.height(26.dp)
        )
    }
}
