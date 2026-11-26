package com.example.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.domain.model.LumetriColorGrade
import com.example.ui.theme.CoralAction
import com.example.ui.theme.CyanPlayhead
import com.example.ui.theme.EmeraldAudio
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

enum class CurveChannel(val label: String, val color: Color) {
    MASTER("RGB", TextPrimary),
    RED("R", CoralAction),
    GREEN("G", EmeraldAudio),
    BLUE("B", CyanPlayhead)
}

@Composable
fun ColorCurvesEditor(
    grade: LumetriColorGrade,
    onGradeChange: (LumetriColorGrade) -> Unit,
    modifier: Modifier = Modifier
) {
    var channel by remember { mutableStateOf(CurveChannel.MASTER) }
    val activePoints = when (channel) {
        CurveChannel.MASTER -> grade.masterCurve
        CurveChannel.RED -> grade.redCurve
        CurveChannel.GREEN -> grade.greenCurve
        CurveChannel.BLUE -> grade.blueCurve
    }

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
                text = "Lumetri RGB Curves",
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CurveChannel.entries.forEach { ch ->
                    FilterChip(
                        selected = channel == ch,
                        onClick = { channel = ch },
                        label = { Text(ch.label, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ch.color.copy(alpha = 0.25f),
                            selectedLabelColor = ch.color,
                            labelColor = TextSecondary
                        ),
                        modifier = Modifier.height(28.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        var draggedIdx by remember { mutableStateOf(-1) }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(128.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(StudioObsidian)
                .border(1.dp, StudioBorder, RoundedCornerShape(10.dp))
                .pointerInput(channel, activePoints) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val xNorm = (offset.x / size.width).coerceIn(0f, 1f)
                            draggedIdx = (xNorm * 4f).roundToInt().coerceIn(0, 4)
                        },
                        onDragEnd = { draggedIdx = -1 },
                        onDragCancel = { draggedIdx = -1 },
                        onDrag = { change, _ ->
                            if (draggedIdx in 0..4) {
                                val yNorm = (1f - (change.position.y / size.height)).coerceIn(0f, 1f)
                                val updated = activePoints.toMutableList()
                                updated[draggedIdx] = yNorm
                                val nextGrade = when (channel) {
                                    CurveChannel.MASTER -> grade.copy(masterCurve = updated)
                                    CurveChannel.RED -> grade.copy(redCurve = updated)
                                    CurveChannel.GREEN -> grade.copy(greenCurve = updated)
                                    CurveChannel.BLUE -> grade.copy(blueCurve = updated)
                                }
                                onGradeChange(nextGrade)
                            }
                        }
                    )
                }
        ) {
            val w = size.width
            val h = size.height

            // Grid lines
            for (i in 1..3) {
                val x = w * (i / 4f)
                val y = h * (i / 4f)
                drawLine(StudioBorder.copy(alpha = 0.4f), Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
                drawLine(StudioBorder.copy(alpha = 0.4f), Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            }
            // Diagonal baseline
            drawLine(TextSecondary.copy(alpha = 0.25f), Offset(0f, h), Offset(w, 0f), strokeWidth = 1.5f)

            // Curve path
            val path = Path()
            activePoints.forEachIndexed { idx, yVal ->
                val px = w * (idx / 4f)
                val py = h * (1f - yVal.coerceIn(0f, 1f))
                if (idx == 0) path.moveTo(px, py) else {
                    val prevX = w * ((idx - 1) / 4f)
                    val prevY = h * (1f - activePoints[idx - 1].coerceIn(0f, 1f))
                    val midX = (prevX + px) / 2f
                    path.cubicTo(midX, prevY, midX, py, px, py)
                }
            }
            drawPath(path, color = channel.color, style = Stroke(width = 3.5f, cap = StrokeCap.Round))

            // Control nodes
            activePoints.forEachIndexed { idx, yVal ->
                val px = w * (idx / 4f)
                val py = h * (1f - yVal.coerceIn(0f, 1f))
                drawCircle(color = StudioObsidian, radius = 9f, center = Offset(px, py))
                drawCircle(color = channel.color, radius = 6.5f, center = Offset(px, py))
            }
        }
    }
}

private fun Float.roundToInt(): Int = kotlin.math.round(this).toInt()
