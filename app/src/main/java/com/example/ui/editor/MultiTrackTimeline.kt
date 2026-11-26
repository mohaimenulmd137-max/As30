package com.example.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
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
import com.example.domain.model.StudioProject
import com.example.domain.model.TimelineClip
import com.example.domain.model.TrackType
import com.example.ui.theme.AmberDocumentary
import com.example.ui.theme.CyanPlayhead
import com.example.ui.theme.EmeraldAudio
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSurface
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

@Composable
fun MultiTrackTimeline(
    project: StudioProject,
    playheadMs: Long,
    selectedClipId: String?,
    isBangla: Boolean,
    onSeek: (Long) -> Unit,
    onSelectClip: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val pxPerSecDp by remember { mutableFloatStateOf(52f) }
    val density = LocalDensity.current
    val pxPerMs = with(density) { (pxPerSecDp.dp.toPx()) / 1000f }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(StudioObsidian)
            .border(1.dp, StudioBorder)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .pointerInput(project.totalDurationMs, pxPerMs) {
                    var currentDragMs = playheadMs
                    detectHorizontalDragGestures(
                        onDragStart = { currentDragMs = playheadMs },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val deltaMs = (-dragAmount / pxPerMs).toLong()
                            currentDragMs = (currentDragMs + deltaMs).coerceIn(0L, project.totalDurationMs)
                            onSeek(currentDragMs)
                        }
                    )
                }
        ) {
            val maxWidthPx = constraints.maxWidth
            val halfWidthPx = maxWidthPx / 2f
            val scrollOffsetPx = halfWidthPx - (playheadMs * pxPerMs)

            Column(modifier = Modifier.fillMaxSize()) {
                // Timecode Ruler
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                        .background(StudioSurface)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val totalSec = (project.totalDurationMs / 1000L).toInt() + 2
                        for (sec in 0..totalSec) {
                            val x = scrollOffsetPx + (sec * 1000f * pxPerMs)
                            if (x in -20f..(size.width + 20f)) {
                                drawLine(
                                    color = TextMuted.copy(alpha = 0.6f),
                                    start = Offset(x, size.height * 0.45f),
                                    end = Offset(x, size.height),
                                    strokeWidth = 2f
                                )
                            }
                            val halfX = x + 500f * pxPerMs
                            if (halfX in -20f..(size.width + 20f)) {
                                drawLine(
                                    color = TextMuted.copy(alpha = 0.3f),
                                    start = Offset(halfX, size.height * 0.72f),
                                    end = Offset(halfX, size.height),
                                    strokeWidth = 1.5f
                                )
                            }
                        }
                    }

                    // Second labels
                    val totalSec = (project.totalDurationMs / 1000L).toInt() + 1
                    for (sec in 0..totalSec step 2) {
                        val xPx = scrollOffsetPx + (sec * 1000f * pxPerMs)
                        if (xPx in -40f..(maxWidthPx + 40f)) {
                            Text(
                                text = "${sec}s",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted,
                                modifier = Modifier
                                    .offset { IntOffset((xPx + 4).roundToInt(), 3) }
                            )
                        }
                    }
                }

                // Stacked 6 Tracks
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TrackType.entries.sortedBy { it.order }.forEach { trackType ->
                        val clipsOnTrack = project.clips.filter { it.trackType == trackType }
                        TimelineTrackRow(
                            trackType = trackType,
                            clips = clipsOnTrack,
                            allProjectClips = project.clips,
                            selectedClipId = selectedClipId,
                            scrollOffsetPx = scrollOffsetPx,
                            pxPerMs = pxPerMs,
                            isBangla = isBangla,
                            onSelectClip = onSelectClip
                        )
                    }
                }
            }

            // Pinned Center Playhead Line
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxHeight()
                    .width(2.dp)
                    .background(CyanPlayhead)
            )
            // Playhead Diamond Head
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 1.dp)
                    .size(12.dp)
                    .background(CyanPlayhead, RoundedCornerShape(3.dp))
            )
        }
    }
}

@Composable
private fun TimelineTrackRow(
    trackType: TrackType,
    clips: List<TimelineClip>,
    allProjectClips: List<TimelineClip>,
    selectedClipId: String?,
    scrollOffsetPx: Float,
    pxPerMs: Float,
    isBangla: Boolean,
    onSelectClip: (String?) -> Unit
) {
    val trackColor = when (trackType) {
        TrackType.TEXT -> TrackTextColor
        TrackType.GRAPHICS -> TrackGraphicsColor
        TrackType.ADJUSTMENT -> TrackAdjustmentColor
        TrackType.VIDEO -> TrackVideoColor
        TrackType.VOICEOVER -> TrackVoiceColor
        TrackType.MUSIC -> TrackMusicColor
    }
    val trackIcon = when (trackType) {
        TrackType.TEXT -> Icons.Default.TextFields
        TrackType.GRAPHICS -> Icons.Default.Image
        TrackType.ADJUSTMENT -> Icons.Default.Tune
        TrackType.VIDEO -> Icons.Default.Movie
        TrackType.VOICEOVER -> Icons.Default.GraphicEq
        TrackType.MUSIC -> Icons.Default.MusicNote
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .background(StudioSurface.copy(alpha = 0.72f))
    ) {
        // Clips rendered along time axis
        clips.forEach { clip ->
            val leftPx = scrollOffsetPx + (clip.startMs * pxPerMs)
            val widthDp = with(LocalDensity.current) { (clip.durationMs * pxPerMs).coerceAtLeast(36f).toDp() }
            val isSelected = clip.id == selectedClipId

            Box(
                modifier = Modifier
                    .offset { IntOffset(leftPx.roundToInt(), 2) }
                    .width(widthDp)
                    .height(30.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(trackColor.copy(alpha = if (isSelected) 0.95f else 0.72f))
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) CyanPlayhead else Color.White.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(7.dp)
                    )
                    .clickable { onSelectClip(clip.id) }
                    .testTag("timeline_clip_${clip.id}")
            ) {
                // Waveform & Auto-Ducking curve for audio tracks
                if (trackType == TrackType.VOICEOVER || trackType == TrackType.MUSIC) {
                    val voClips = allProjectClips.filter { it.trackType == TrackType.VOICEOVER }
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val bars = (size.width / 5f).toInt().coerceIn(8, 120)
                        val step = size.width / bars
                        val duckPath = Path()
                        for (i in 0 until bars) {
                            val norm = i.toFloat() / bars
                            val tMs = clip.startMs + (norm * clip.durationMs).toLong()
                            val isDucked = trackType == TrackType.MUSIC && clip.audioConfig.autoDuckingEnabled &&
                                voClips.any { tMs in (it.startMs - 150L)..(it.endMs + 150L) }
                            val duckMult = if (isDucked) clip.audioConfig.duckingFloor else 1f
                            val wave = (0.28f + 0.62f * abs(sin((i + clip.audioConfig.waveformSeed) * 0.75f))) * duckMult
                            val barH = size.height * wave * 0.75f
                            val x = i * step + step * 0.3f
                            drawLine(
                                color = Color.White.copy(alpha = if (isDucked) 0.32f else 0.55f),
                                start = Offset(x, (size.height - barH) / 2f),
                                end = Offset(x, (size.height + barH) / 2f),
                                strokeWidth = 2f
                            )
                            val envY = size.height * (1f - duckMult * 0.75f)
                            if (i == 0) duckPath.moveTo(x, envY) else duckPath.lineTo(x, envY)
                        }
                        if (trackType == TrackType.MUSIC && clip.audioConfig.autoDuckingEnabled) {
                            drawPath(duckPath, color = AmberDocumentary, style = Stroke(width = 2f))
                        }
                    }
                }

                // Clip title + feature badges
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = clip.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (clip.keyframes.isNotEmpty()) {
                        Icon(
                            Icons.Default.Diamond,
                            contentDescription = "Keyframes",
                            tint = CyanPlayhead,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                    if (clip.lockedSfxId != null) {
                        Icon(
                            Icons.Default.Audiotrack,
                            contentDescription = "Locked SFX",
                            tint = EmeraldAudio,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                    if (clip.speedRamp.presetName != "Normal (1.0x)") {
                        Icon(
                            Icons.Default.Speed,
                            contentDescription = "Speed Ramp",
                            tint = AmberDocumentary,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }

                // Keyframe diamond markers along clip bottom
                clip.keyframes.forEach { kf ->
                    val frac = if (clip.durationMs > 0L) (kf.timeOffsetMs.toFloat() / clip.durationMs).coerceIn(0f, 1f) else 0f
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth(frac.coerceAtLeast(0.02f))
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(6.dp)
                                .background(CyanPlayhead, RoundedCornerShape(1.dp))
                        )
                    }
                }
            }
        }

        // Fixed left Track Label pill
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 4.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(StudioSurfaceElevated.copy(alpha = 0.88f))
                .padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = trackIcon,
                contentDescription = trackType.labelEn,
                tint = trackColor,
                modifier = Modifier.size(11.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = if (isBangla) trackType.labelBn else trackType.labelEn,
                style = MaterialTheme.typography.labelSmall,
                color = TextPrimary,
                maxLines = 1
            )
        }
    }
}
