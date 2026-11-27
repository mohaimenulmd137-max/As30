package com.example.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.StudioProject
import com.example.domain.model.TimelineClip
import com.example.domain.model.TrackLayerState
import com.example.domain.model.TrackType
import com.example.ui.theme.AmberDocumentary
import com.example.ui.theme.CoralAction
import com.example.ui.theme.CyanPlayhead
import com.example.ui.theme.EmeraldAudio
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TrackAdjustmentColor
import com.example.ui.theme.TrackGraphicsColor
import com.example.ui.theme.TrackMusicColor
import com.example.ui.theme.TrackTextColor
import com.example.ui.theme.TrackVideoColor
import com.example.ui.theme.TrackVoiceColor
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

private data class TimelineLaneRow(
    val layer: TrackLayerState,
    val laneIndex: Int
)

@Composable
fun MultiTrackTimeline(
    project: StudioProject,
    playheadMs: Long,
    selectedClipId: String?,
    isBangla: Boolean,
    onSeek: (Long) -> Unit,
    onSelectClip: (String?) -> Unit,
    onMoveClip: (clipId: String, newStartMs: Long) -> Unit,
    onTrimClip: (clipId: String, newStartMs: Long, newDurationMs: Long) -> Unit,
    onMoveTrackOrder: (TrackType, Int) -> Unit,
    onToggleTrackVisibility: (TrackType) -> Unit,
    onToggleTrackLock: (TrackType) -> Unit,
    onQuickAddTrack: (TrackType) -> Unit,
    modifier: Modifier = Modifier,
    onMoveClipLane: (clipId: String, delta: Int) -> Unit = { _, _ -> },
    onDuplicateSequentially: (clipId: String) -> Unit = {},
    onDuplicateToSubLayer: (clipId: String) -> Unit = {}
) {
    var pxPerSecDp by remember { mutableFloatStateOf(46f) }
    val density = LocalDensity.current
    val pxPerMs = with(density) { (pxPerSecDp.dp.toPx()) / 1000f }
    val verticalScroll = rememberScrollState()
    val sortedLayers = remember(project.trackLayers) {
        project.trackLayers.sortedBy { it.order }
    }
    val laneRows = remember(sortedLayers, project.clips) {
        buildList {
            sortedLayers.forEach { layer ->
                val maxLane = project.clips
                    .filter { it.trackType == layer.trackType }
                    .maxOfOrNull { it.laneIndex } ?: 0
                for (lane in 0..maxLane.coerceIn(0, 8)) {
                    add(TimelineLaneRow(layer = layer, laneIndex = lane))
                }
            }
        }
    }
    val selectedClip = remember(project.clips, selectedClipId) {
        project.clips.firstOrNull { it.id == selectedClipId }
    }
    val currentPlayhead by rememberUpdatedState(playheadMs)
    val currentPxPerMs by rememberUpdatedState(pxPerMs)
    val currentTotalDur by rememberUpdatedState(project.totalDurationMs)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0A0D15))
            .border(1.dp, Color(0xFF1F2638))
    ) {
        if (selectedClip != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF111725))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${selectedClip.title} (L${selectedClip.laneIndex + 1})",
                    fontSize = 10.sp,
                    color = AmberDocumentary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                TimelineQuickActionChip(
                    label = if (isBangla) "▲ উপরে" else "▲ Up",
                    tint = CyanPlayhead,
                    tag = "clip_lane_up",
                    onClick = { onMoveClipLane(selectedClip.id, -1) }
                )
                TimelineQuickActionChip(
                    label = if (isBangla) "▼ নিচে" else "▼ Down",
                    tint = CyanPlayhead,
                    tag = "clip_lane_down",
                    onClick = { onMoveClipLane(selectedClip.id, 1) }
                )
                TimelineQuickActionChip(
                    label = if (isBangla) "পাশে কপি" else "Copy Next",
                    tint = EmeraldAudio,
                    tag = "clip_copy_next",
                    onClick = { onDuplicateSequentially(selectedClip.id) }
                )
                TimelineQuickActionChip(
                    label = if (isBangla) "নিচে লেয়ারে কপি" else "Copy Sub-Layer",
                    tint = AmberDocumentary,
                    tag = "clip_copy_sublayer",
                    onClick = { onDuplicateToSubLayer(selectedClip.id) }
                )
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val sidebarWidthDp = 152.dp
            val timelineViewportWidthDp = (maxWidth - sidebarWidthDp).coerceAtLeast(140.dp)
            val timelineViewportPx = with(density) { timelineViewportWidthDp.toPx() }
            val halfWidthPx = timelineViewportPx / 2f
            val scrollOffsetPx = halfWidthPx - (playheadMs * pxPerMs)

            Row(modifier = Modifier.fillMaxSize()) {
                // LEFT SIDEBAR: Layer Header + Zoom (- / ফিট / +) + Reorderable Layers
                Column(
                    modifier = Modifier
                        .width(sidebarWidthDp)
                        .fillMaxHeight()
                        .background(Color(0xFF0E131F))
                        .border(1.dp, Color(0xFF1D2436))
                ) {
                    // Header row matching screenshot: লেয়ার (7)  [-] [ফিট] [+]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                            .background(Color(0xFF121827))
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isBangla) "লেয়ার (${sortedLayers.size})" else "Layers (${sortedLayers.size})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                onClick = { pxPerSecDp = (pxPerSecDp * 0.78f).coerceIn(14f, 160f) },
                                color = StudioSurfaceElevated,
                                shape = CircleShape,
                                border = BorderStroke(1.dp, StudioBorder),
                                modifier = Modifier.size(22.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.ZoomOut, contentDescription = "Zoom out", tint = TextPrimary, modifier = Modifier.size(12.dp))
                                }
                            }
                            Surface(
                                onClick = {
                                    val totalSec = (project.totalDurationMs / 1000f).coerceAtLeast(4f)
                                    val fitDp = (timelineViewportWidthDp.value * 0.86f / totalSec).coerceIn(14f, 140f)
                                    pxPerSecDp = fitDp
                                },
                                color = Color(0xFF282213),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, AmberDocumentary.copy(alpha = 0.45f)),
                                modifier = Modifier.height(22.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 6.dp)
                                ) {
                                    Text(
                                        text = if (isBangla) "ফিট" else "Fit",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AmberDocumentary
                                    )
                                }
                            }
                            Surface(
                                onClick = { pxPerSecDp = (pxPerSecDp * 1.28f).coerceIn(14f, 160f) },
                                color = StudioSurfaceElevated,
                                shape = CircleShape,
                                border = BorderStroke(1.dp, StudioBorder),
                                modifier = Modifier.size(22.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.ZoomIn, contentDescription = "Zoom in", tint = TextPrimary, modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                    }

                    // Layer + Dynamic Sub-Layer control rows
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(verticalScroll)
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        laneRows.forEach { row ->
                            val layerIdx = sortedLayers.indexOfFirst { it.trackType == row.layer.trackType }
                            if (row.laneIndex == 0) {
                                LayerSidebarRow(
                                    layer = row.layer,
                                    isFirst = layerIdx <= 0,
                                    isLast = layerIdx >= sortedLayers.lastIndex,
                                    isBangla = isBangla,
                                    onMoveUp = { onMoveTrackOrder(row.layer.trackType, -1) },
                                    onMoveDown = { onMoveTrackOrder(row.layer.trackType, 1) },
                                    onToggleVisible = { onToggleTrackVisibility(row.layer.trackType) },
                                    onToggleLock = { onToggleTrackLock(row.layer.trackType) },
                                    onQuickAdd = { onQuickAddTrack(row.layer.trackType) }
                                )
                            } else {
                                SubLayerSidebarRow(
                                    layer = row.layer,
                                    laneIndex = row.laneIndex,
                                    isBangla = isBangla
                                )
                            }
                        }
                    }
                }

                // RIGHT SCROLLABLE TIMELINE: Ruler + Playhead + Draggable & Trimmable Clips
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clipToBounds()
                        .pointerInput(Unit) {
                            var dragStartPlayheadMs = 0L
                            var accumDelta = 0f
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    dragStartPlayheadMs = currentPlayhead
                                    accumDelta = 0f
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    accumDelta += dragAmount
                                    val deltaMs = (-accumDelta / currentPxPerMs).toLong()
                                    onSeek((dragStartPlayheadMs + deltaMs).coerceIn(0L, currentTotalDur))
                                }
                            )
                        }
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Top Timecode Ruler (adapts tick density for up to 10-minute timelines)
                        val tickStepSec = when {
                            pxPerSecDp < 18f -> 10
                            pxPerSecDp < 30f -> 5
                            else -> 2
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(34.dp)
                                .background(Color(0xFF0E131F))
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val totalSec = (project.totalDurationMs / 1000L).toInt() + 2
                                val minorStep = if (pxPerSecDp < 20f) 5 else 1
                                for (sec in 0..totalSec step minorStep) {
                                    val x = scrollOffsetPx + (sec * 1000f * pxPerMs)
                                    if (x in -20f..(size.width + 20f)) {
                                        drawLine(
                                            color = TextMuted.copy(alpha = 0.6f),
                                            start = Offset(x, size.height * 0.56f),
                                            end = Offset(x, size.height),
                                            strokeWidth = 2f
                                        )
                                    }
                                    if (minorStep == 1) {
                                        val halfX = x + 500f * pxPerMs
                                        if (halfX in -20f..(size.width + 20f)) {
                                            drawLine(
                                                color = TextMuted.copy(alpha = 0.25f),
                                                start = Offset(halfX, size.height * 0.78f),
                                                end = Offset(halfX, size.height),
                                                strokeWidth = 1.2f
                                            )
                                        }
                                    }
                                }
                            }

                            val totalSec = (project.totalDurationMs / 1000L).toInt() + 1
                            for (sec in 0..totalSec step tickStepSec) {
                                val xPx = scrollOffsetPx + (sec * 1000f * pxPerMs)
                                if (xPx in -40f..(timelineViewportPx + 40f)) {
                                    val label = if (sec >= 60) "%d:%02d".format(sec / 60, sec % 60) else ":%02d".format(sec)
                                    Text(
                                        text = label,
                                        fontSize = 10.sp,
                                        color = TextMuted,
                                        modifier = Modifier.offset { IntOffset((xPx + 4).roundToInt(), 5) }
                                    )
                                }
                            }
                        }

                        // Synced Stacked Track + Sub-Lane Rows
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(verticalScroll)
                                .padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            laneRows.forEach { row ->
                                val clipsInLane = project.clips.filter {
                                    it.trackType == row.layer.trackType && it.laneIndex == row.laneIndex
                                }
                                TimelineTrackClipsRow(
                                    layer = row.layer,
                                    clips = clipsInLane,
                                    allProjectClips = project.clips,
                                    selectedClipId = selectedClipId,
                                    scrollOffsetPx = scrollOffsetPx,
                                    pxPerMs = pxPerMs,
                                    onSelectClip = onSelectClip,
                                    onMoveClip = onMoveClip,
                                    onTrimClip = onTrimClip
                                )
                            }
                        }
                    }

                    // Glowing Amber-Gold Vertical Playhead Line (Exact Screenshot Match)
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 24.dp)
                            .fillMaxHeight()
                            .width(3.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(AmberDocumentary, AmberDocumentary.copy(alpha = 0.7f))
                                )
                            )
                    )
                    // Playhead Amber-Gold Timecode Pill (00:00.97) + White Handle Dot
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            color = AmberDocumentary,
                            shape = RoundedCornerShape(12.dp),
                            shadowElevation = 6.dp
                        ) {
                            Text(
                                text = formatTimelinePlayhead(playheadMs),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF0A0C12),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .offset(y = (-3).dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .border(1.5.dp, AmberDocumentary, CircleShape)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LayerSidebarRow(
    layer: TrackLayerState,
    isFirst: Boolean,
    isLast: Boolean,
    isBangla: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggleVisible: () -> Unit,
    onToggleLock: () -> Unit,
    onQuickAdd: () -> Unit
) {
    val accent = trackColorFor(layer.trackType)
    val icon = trackIconFor(layer.trackType)
    val shortName = if (isBangla) layer.trackType.labelBn else layer.trackType.labelEn

    Surface(
        color = Color(0xFF131928),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (layer.isLocked) AmberDocumentary.copy(alpha = 0.45f) else Color(0xFF232B3E)),
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .alpha(if (layer.isVisible) 1f else 0.48f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(icon, contentDescription = shortName, tint = accent, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = shortName,
                    fontSize = 10.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SmallSidebarIconBtn(Icons.Default.ArrowUpward, "Move Up", enabled = !isFirst, tint = TextMuted, onClick = onMoveUp)
                SmallSidebarIconBtn(Icons.Default.ArrowDownward, "Move Down", enabled = !isLast, tint = TextMuted, onClick = onMoveDown)
                SmallSidebarIconBtn(
                    icon = if (layer.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    desc = "Visibility",
                    tint = if (layer.isVisible) TextPrimary else TextMuted,
                    onClick = onToggleVisible
                )
                SmallSidebarIconBtn(
                    icon = if (layer.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    desc = "Lock Track",
                    tint = if (layer.isLocked) AmberDocumentary else TextMuted,
                    onClick = onToggleLock
                )
                SmallSidebarIconBtn(Icons.Default.Add, "Add to track", tint = CyanPlayhead, onClick = onQuickAdd)
            }
        }
    }
}

@Composable
private fun SubLayerSidebarRow(
    layer: TrackLayerState,
    laneIndex: Int,
    isBangla: Boolean
) {
    val accent = trackColorFor(layer.trackType)
    Surface(
        color = Color(0xFF0E1422),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.25f)),
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .alpha(if (layer.isVisible) 0.92f else 0.45f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isBangla) "↳ সাব-লেয়ার ${laneIndex + 1}" else "↳ Lane ${laneIndex + 1}",
                fontSize = 10.sp,
                color = accent,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TimelineQuickActionChip(
    label: String,
    tint: Color,
    tag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color(0xFF192236),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.55f)),
        modifier = Modifier.testTag(tag)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = tint,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun SmallSidebarIconBtn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    tint: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = desc,
            tint = if (enabled) tint else tint.copy(alpha = 0.25f),
            modifier = Modifier.size(12.dp)
        )
    }
}

@Composable
private fun TimelineTrackClipsRow(
    layer: TrackLayerState,
    clips: List<TimelineClip>,
    allProjectClips: List<TimelineClip>,
    selectedClipId: String?,
    scrollOffsetPx: Float,
    pxPerMs: Float,
    onSelectClip: (String?) -> Unit,
    onMoveClip: (String, Long) -> Unit,
    onTrimClip: (String, Long, Long) -> Unit
) {
    val trackColor = trackColorFor(layer.trackType)
    val density = LocalDensity.current
    val currentPxPerMs by rememberUpdatedState(pxPerMs)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(Color(0xFF0E131F).copy(alpha = 0.65f))
            .alpha(if (layer.isVisible) 1f else 0.4f)
    ) {
        clips.forEach { clip ->
            val leftPx = scrollOffsetPx + (clip.startMs * pxPerMs)
            val widthDp = with(density) { (clip.durationMs * pxPerMs).coerceAtLeast(42f).toDp() }
            val isSelected = clip.id == selectedClipId
            val locked = clip.isLocked || layer.isLocked
            val latestClip by rememberUpdatedState(clip)

            Box(
                modifier = Modifier
                    .offset { IntOffset(leftPx.roundToInt(), 3) }
                    .width(widthDp)
                    .height(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(trackColor.copy(alpha = if (isSelected) 0.96f else 0.72f))
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = when {
                            isSelected -> AmberDocumentary
                            locked -> AmberDocumentary.copy(alpha = 0.4f)
                            else -> Color.White.copy(alpha = 0.22f)
                        },
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onSelectClip(clip.id) }
                    .pointerInput(clip.id, locked) {
                        if (locked) return@pointerInput
                        var dragStartMs = 0L
                        var dragAccumPx = 0f
                        detectHorizontalDragGestures(
                            onDragStart = {
                                onSelectClip(latestClip.id)
                                dragStartMs = latestClip.startMs
                                dragAccumPx = 0f
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                dragAccumPx += dragAmount
                                val deltaMs = (dragAccumPx / currentPxPerMs).toLong()
                                onMoveClip(latestClip.id, (dragStartMs + deltaMs).coerceAtLeast(0L))
                            }
                        )
                    }
                    .testTag("timeline_clip_${clip.id}")
            ) {
                // Waveform & Smooth Auto-Ducking curve for audio/video tracks
                if (layer.trackType == TrackType.VOICEOVER || layer.trackType == TrackType.MUSIC ||
                    (layer.trackType == TrackType.VIDEO && clip.mediaSource?.isVideo == true)
                ) {
                    val voClips = allProjectClips.filter { it.trackType == TrackType.VOICEOVER && !it.isHidden }
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val bars = (size.width / 5f).toInt().coerceIn(8, 120)
                        val step = size.width / bars
                        val duckPath = Path()
                        for (i in 0 until bars) {
                            val norm = i.toFloat() / bars
                            val tMs = clip.startMs + (norm * clip.durationMs).toLong()
                            val isDucked = layer.trackType == TrackType.MUSIC && clip.audioConfig.autoDuckingEnabled &&
                                voClips.any { tMs in (it.startMs - 200L)..(it.endMs + 250L) }
                            val duckMult = if (isDucked) clip.audioConfig.duckingFloor else 1f
                            val wave = (0.28f + 0.62f * abs(sin((i + clip.audioConfig.waveformSeed) * 0.75f))) * duckMult
                            val barH = size.height * wave * (if (clip.audioConfig.isMuted) 0.15f else 0.72f)
                            val x = i * step + step * 0.3f
                            drawLine(
                                color = Color.White.copy(alpha = if (clip.audioConfig.isMuted) 0.16f else if (isDucked) 0.30f else 0.55f),
                                start = Offset(x, (size.height - barH) / 2f),
                                end = Offset(x, (size.height + barH) / 2f),
                                strokeWidth = 2f
                            )
                            val envY = size.height * (1f - duckMult * 0.72f)
                            if (i == 0) duckPath.moveTo(x, envY) else duckPath.lineTo(x, envY)
                        }
                        if (layer.trackType == TrackType.MUSIC && clip.audioConfig.autoDuckingEnabled) {
                            drawPath(duckPath, color = AmberDocumentary, style = Stroke(width = 2.2f))
                        }
                    }
                }

                // Clip title + badges (including Mute & Solo)
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = if (isSelected) 18.dp else 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = clip.title,
                        fontSize = 10.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (clip.audioConfig.isMuted) {
                        Icon(Icons.AutoMirrored.Filled.VolumeOff, contentDescription = "Muted", tint = CoralAction, modifier = Modifier.size(10.dp))
                    } else if (clip.audioConfig.isSolo) {
                        Text("S", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = CyanPlayhead)
                    }
                    if (locked) {
                        Icon(Icons.Default.Lock, contentDescription = "Locked", tint = AmberDocumentary, modifier = Modifier.size(10.dp))
                    }
                    if (clip.keyframes.isNotEmpty()) {
                        Icon(Icons.Default.Diamond, contentDescription = "Keyframes", tint = CyanPlayhead, modifier = Modifier.size(10.dp))
                    }
                    if (clip.lockedSfxId != null) {
                        Icon(Icons.Default.Audiotrack, contentDescription = "SFX", tint = EmeraldAudio, modifier = Modifier.size(10.dp))
                    }
                    if (clip.speedRamp.presetName != "Normal (1.0x)") {
                        Icon(Icons.Default.Speed, contentDescription = "Speed", tint = AmberDocumentary, modifier = Modifier.size(10.dp))
                    }
                }

                // Left & Right 16.dp Trim Handles (200ms..600_000ms) when selected & unlocked
                if (isSelected && !locked) {
                    // Left trim bar (adjusts startMs + durationMs)
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .width(16.dp)
                            .fillMaxHeight()
                            .background(AmberDocumentary)
                            .pointerInput(clip.id) {
                                var initStart = 0L
                                var initEnd = 0L
                                var accum = 0f
                                detectHorizontalDragGestures(
                                    onDragStart = {
                                        initStart = latestClip.startMs
                                        initEnd = latestClip.endMs
                                        accum = 0f
                                    },
                                    onHorizontalDrag = { change, dragAmount ->
                                        change.consume()
                                        accum += dragAmount
                                        val deltaMs = (accum / currentPxPerMs).toLong()
                                        val nextStart = (initStart + deltaMs).coerceIn(0L, (initEnd - 200L).coerceAtLeast(0L))
                                        onTrimClip(latestClip.id, nextStart, (initEnd - nextStart).coerceIn(200L, 600_000L))
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(modifier = Modifier.width(2.5.dp).height(16.dp).background(StudioObsidian, RoundedCornerShape(1.dp)))
                    }

                    // Right trim bar (adjusts durationMs)
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .width(16.dp)
                            .fillMaxHeight()
                            .background(AmberDocumentary)
                            .pointerInput(clip.id) {
                                var initDur = 0L
                                var accum = 0f
                                detectHorizontalDragGestures(
                                    onDragStart = {
                                        initDur = latestClip.durationMs
                                        accum = 0f
                                    },
                                    onHorizontalDrag = { change, dragAmount ->
                                        change.consume()
                                        accum += dragAmount
                                        val deltaMs = (accum / currentPxPerMs).toLong()
                                        onTrimClip(latestClip.id, latestClip.startMs, (initDur + deltaMs).coerceIn(200L, 600_000L))
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(modifier = Modifier.width(2.5.dp).height(16.dp).background(StudioObsidian, RoundedCornerShape(1.dp)))
                    }
                }
            }
        }
    }
}

private fun trackColorFor(trackType: TrackType): Color = when (trackType) {
    TrackType.TEXT -> TrackTextColor
    TrackType.GRAPHICS -> TrackGraphicsColor
    TrackType.VFX -> Color(0xFF0E7490)
    TrackType.ADJUSTMENT -> TrackAdjustmentColor
    TrackType.VIDEO -> TrackVideoColor
    TrackType.VOICEOVER -> TrackVoiceColor
    TrackType.MUSIC -> TrackMusicColor
}

private fun trackIconFor(trackType: TrackType) = when (trackType) {
    TrackType.TEXT -> Icons.Default.TextFields
    TrackType.GRAPHICS -> Icons.Default.BarChart
    TrackType.VFX -> Icons.Default.AutoFixHigh
    TrackType.ADJUSTMENT -> Icons.Default.AutoAwesome
    TrackType.VIDEO -> Icons.Default.Movie
    TrackType.VOICEOVER -> Icons.Default.GraphicEq
    TrackType.MUSIC -> Icons.Default.MusicNote
}

private fun formatTimelinePlayhead(ms: Long): String {
    val clamped = ms.coerceAtLeast(0L)
    val secs = clamped / 1000L
    val mins = secs / 60L
    val remSec = secs % 60L
    val centis = (clamped % 1000L) / 10L
    return "%02d:%02d.%02d".format(mins, remSec, centis)
}
