package com.example.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.domain.model.PresetCatalog
import com.example.domain.model.SpeedRampProfile
import com.example.ui.theme.AmberDocumentary
import com.example.ui.theme.CyanPlayhead
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.abs

@Composable
fun SpeedRampGraphEditor(
    profile: SpeedRampProfile,
    onProfileChange: (SpeedRampProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(StudioSurface)
            .border(1.dp, StudioBorder, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Speed Ramping (0.25x - 4.0x)",
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary
            )
            Text(
                text = profile.presetName,
                style = MaterialTheme.typography.labelSmall,
                color = AmberDocumentary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 2.dp)
        ) {
            items(PresetCatalog.speedRampPresets, key = { it.presetName }) { preset ->
                FilterChip(
                    selected = profile.presetName == preset.presetName,
                    onClick = { onProfileChange(preset) },
                    label = { Text(preset.presetName, style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyanPlayhead.copy(alpha = 0.25f),
                        selectedLabelColor = CyanPlayhead,
                        labelColor = TextSecondary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        var draggedNodeIdx by remember { mutableStateOf(-1) }
        val sortedPoints = remember(profile.points) { profile.points.sortedBy { it.normalizedTime } }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(StudioObsidian)
                .border(1.dp, StudioBorder, RoundedCornerShape(10.dp))
                .pointerInput(sortedPoints) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val xNorm = (offset.x / size.width).coerceIn(0f, 1f)
                            draggedNodeIdx = sortedPoints.indices.minByOrNull {
                                abs(sortedPoints[it].normalizedTime - xNorm)
                            } ?: -1
                        },
                        onDragEnd = { draggedNodeIdx = -1 },
                        onDragCancel = { draggedNodeIdx = -1 },
                        onDrag = { change, _ ->
                            if (draggedNodeIdx in sortedPoints.indices) {
                                val yFrac = (1f - (change.position.y / size.height)).coerceIn(0f, 1f)
                                val newSpeed = (0.25f + yFrac * 3.75f).coerceIn(0.25f, 4.0f)
                                val mutable = sortedPoints.toMutableList()
                                mutable[draggedNodeIdx] = mutable[draggedNodeIdx].copy(speed = newSpeed)
                                onProfileChange(SpeedRampProfile("Custom Curve", mutable))
                            }
                        }
                    )
                }
        ) {
            val w = size.width
            val h = size.height
            // 1.0x reference line
            val oneXFrac = (1.0f - 0.25f) / 3.75f
            val oneXY = h * (1f - oneXFrac)
            drawLine(
                color = TextMuted.copy(alpha = 0.45f),
                start = Offset(0f, oneXY),
                end = Offset(w, oneXY),
                strokeWidth = 1.5f
            )

            val path = Path()
            val fillPath = Path()
            sortedPoints.forEachIndexed { idx, pt ->
                val px = w * pt.normalizedTime
                val yFrac = ((pt.speed - 0.25f) / 3.75f).coerceIn(0f, 1f)
                val py = h * (1f - yFrac)
                if (idx == 0) {
                    path.moveTo(px, py)
                    fillPath.moveTo(px, h)
                    fillPath.lineTo(px, py)
                } else {
                    val prev = sortedPoints[idx - 1]
                    val prevX = w * prev.normalizedTime
                    val prevY = h * (1f - ((prev.speed - 0.25f) / 3.75f).coerceIn(0f, 1f))
                    val midX = (prevX + px) / 2f
                    path.cubicTo(midX, prevY, midX, py, px, py)
                    fillPath.cubicTo(midX, prevY, midX, py, px, py)
                }
                if (idx == sortedPoints.lastIndex) {
                    fillPath.lineTo(px, h)
                    fillPath.close()
                }
            }

            drawPath(
                fillPath,
                brush = Brush.verticalGradient(listOf(CyanPlayhead.copy(alpha = 0.35f), Color.Transparent))
            )
            drawPath(path, color = CyanPlayhead, style = Stroke(width = 4f, cap = StrokeCap.Round))

            sortedPoints.forEach { pt ->
                val px = w * pt.normalizedTime
                val py = h * (1f - ((pt.speed - 0.25f) / 3.75f).coerceIn(0f, 1f))
                drawCircle(color = StudioObsidian, radius = 10f, center = Offset(px, py))
                drawCircle(color = AmberDocumentary, radius = 7f, center = Offset(px, py))
            }
        }
    }
}
