package com.example.ui.editor

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.ComparisonChartConfig
import com.example.domain.model.ComparisonGridConfig
import com.example.domain.model.GeometricShape
import com.example.domain.model.InvestigativeDocConfig
import com.example.domain.model.InvestigativeToolType
import com.example.domain.model.PresetCatalog
import com.example.domain.model.TimelineClip
import com.example.domain.model.TransitionType
import com.example.domain.model.VfxConfig
import com.example.domain.model.VfxEffectType
import com.example.ui.theme.AmberDocumentary
import com.example.ui.theme.CyanPlayhead
import com.example.ui.theme.EmeraldAudio
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun VfxAndTransitionInspector(
    clip: TimelineClip?,
    isBangla: Boolean,
    onAddVfxClip: (VfxEffectType) -> Unit,
    onUpdateClip: (String, (TimelineClip) -> TimelineClip) -> Unit
) {
    Text(
        text = if (isBangla) "হলিউড VFX ও সিনেমাটিক ট্রানজিশন" else "Hollywood VFX & Cinema Transitions",
        style = MaterialTheme.typography.labelMedium,
        color = CyanPlayhead
    )

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(VfxEffectType.entries.filter { it != VfxEffectType.NONE }, key = { it.name }) { effect ->
            val active = clip?.vfxConfig?.effectType == effect
            FilterChip(
                selected = active,
                onClick = {
                    if (clip != null && clip.vfxConfig != null) {
                        onUpdateClip(clip.id) {
                            it.copy(
                                title = effect.labelBn,
                                vfxConfig = (it.vfxConfig ?: VfxConfig()).copy(effectType = effect)
                            )
                        }
                    } else {
                        onAddVfxClip(effect)
                    }
                },
                label = { Text(if (isBangla) effect.labelBn else effect.labelEn, style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CyanPlayhead,
                    selectedLabelColor = StudioObsidian,
                    labelColor = TextPrimary
                )
            )
        }
    }

    if (clip?.vfxConfig != null) {
        val vfx = clip.vfxConfig
        InspectorSlider(
            label = if (isBangla) "VFX তীব্রতা (Intensity)" else "VFX Intensity",
            value = vfx.intensity,
            range = 0.05f..1f,
            valueText = "${(vfx.intensity * 100).toInt()}%"
        ) { next ->
            onUpdateClip(clip.id) { it.copy(vfxConfig = vfx.copy(intensity = next)) }
        }
        InspectorSlider(
            label = if (isBangla) "VFX গতি (Speed)" else "VFX Speed",
            value = vfx.speed,
            range = 0.3f..2.5f,
            valueText = "%.1fx".format(vfx.speed)
        ) { next ->
            onUpdateClip(clip.id) { it.copy(vfxConfig = vfx.copy(speed = next)) }
        }
    }

    if (clip != null) {
        Text(
            text = if (isBangla) "ক্লিপ এন্ট্রি ট্রানজিশন" else "Clip Entry Transition",
            style = MaterialTheme.typography.labelMedium,
            color = AmberDocumentary
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(TransitionType.entries, key = { it.name }) { tr ->
                FilterChip(
                    selected = clip.transitionIn == tr,
                    onClick = { onUpdateClip(clip.id) { it.copy(transitionIn = tr) } },
                    label = { Text(if (isBangla) tr.labelBn else tr.labelEn, style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AmberDocumentary,
                        selectedLabelColor = StudioObsidian,
                        labelColor = TextSecondary
                    )
                )
            }
        }
    }
}

@Composable
fun OverlayProInspector(
    clip: TimelineClip?,
    isBangla: Boolean,
    onAddChart: (Int) -> Unit,
    onAddComparisonGrid: (Int) -> Unit,
    onAddInvestigativeTool: (InvestigativeToolType) -> Unit,
    onPickSlotMedia: (clipId: String, slotId: String) -> Unit,
    onUpdateClip: (String, (TimelineClip) -> TimelineClip) -> Unit
) {
    // Quick-create cards for Charts (2-60 bars), 2-4 Slot Comparison, and Investigative Tools
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Surface(
                onClick = { onAddChart(12) },
                color = StudioSurfaceElevated,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, CyanPlayhead.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.BarChart, contentDescription = null, tint = CyanPlayhead, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isBangla) "+ ৬০-বার চার্ট" else "+ 60-Bar Chart", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                }
            }
        }
        listOf(2, 3, 4).forEach { count ->
            item {
                Surface(
                    onClick = { onAddComparisonGrid(count) },
                    color = StudioSurfaceElevated,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, AmberDocumentary.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.GridView, contentDescription = null, tint = AmberDocumentary, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(if (isBangla) "+ $count ছবি/ভিডিও" else "+ $count-Slot Grid", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                    }
                }
            }
        }
    }

    // Investigative Documentary Tools selector
    Text(
        text = if (isBangla) "অনুসন্ধানমূলক ডকুমেন্টারি টুলস" else "Investigative Documentary Tools",
        style = MaterialTheme.typography.labelMedium,
        color = EmeraldAudio
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(InvestigativeToolType.entries.filter { it != InvestigativeToolType.NONE }, key = { it.name }) { tool ->
            FilterChip(
                selected = clip?.investigativeConfig?.toolType == tool,
                onClick = { onAddInvestigativeTool(tool) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(14.dp)) },
                label = { Text(if (isBangla) tool.labelBn else tool.labelEn, style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = EmeraldAudio,
                    selectedLabelColor = StudioObsidian,
                    labelColor = TextPrimary
                )
            )
        }
    }

    // Selected Clip Specific Editor: 60-Bar Chart
    clip?.chartConfig?.let { chart ->
        ChartConfigEditor(
            clipId = clip.id,
            chart = chart,
            isBangla = isBangla,
            onUpdateClip = onUpdateClip
        )
    }

    // Selected Clip Specific Editor: 2-4 Slot Comparison Grid with Geometric Shapes
    clip?.comparisonGrid?.let { grid ->
        ComparisonGridConfigEditor(
            clipId = clip.id,
            grid = grid,
            isBangla = isBangla,
            onPickSlotMedia = onPickSlotMedia,
            onUpdateClip = onUpdateClip
        )
    }

    // Selected Clip Specific Editor: Investigative Doc Card
    clip?.investigativeConfig?.let { doc ->
        InvestigativeConfigEditor(
            clipId = clip.id,
            doc = doc,
            isBangla = isBangla,
            onUpdateClip = onUpdateClip
        )
    }
}

@Composable
private fun ChartConfigEditor(
    clipId: String,
    chart: ComparisonChartConfig,
    isBangla: Boolean,
    onUpdateClip: (String, (TimelineClip) -> TimelineClip) -> Unit
) {
    var selectedBarIdx by remember(clipId) { mutableIntStateOf(0) }
    val safeBars = if (chart.bars.size < chart.barCount) {
        val generated = PresetCatalog.generateDefaultBars(chart.barCount)
        chart.bars + generated.drop(chart.bars.size)
    } else chart.bars

    OutlinedTextField(
        value = chart.title,
        onValueChange = { t -> onUpdateClip(clipId) { it.copy(chartConfig = chart.copy(title = t)) } },
        label = { Text(if (isBangla) "চার্ট শিরোনাম" else "Chart Title") },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyanPlayhead, unfocusedBorderColor = StudioBorder, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
        modifier = Modifier.fillMaxWidth()
    )

    InspectorSlider(
        label = if (isBangla) "মোট বারের সংখ্যা (২ থেকে ৬০টি)" else "Bar Count (2..60)",
        value = chart.barCount.toFloat(),
        range = 2f..60f,
        valueText = "${chart.barCount} টি"
    ) { countFloat ->
        val nextCount = countFloat.toInt().coerceIn(2, 60)
        val expanded = if (chart.bars.size < nextCount) {
            val defaults = PresetCatalog.generateDefaultBars(nextCount)
            chart.bars + defaults.drop(chart.bars.size)
        } else chart.bars
        onUpdateClip(clipId) { it.copy(chartConfig = chart.copy(barCount = nextCount, bars = expanded)) }
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(if (isBangla) "হরাইজন্টাল বার" else "Horizontal Bars", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
        Switch(
            checked = chart.isHorizontal,
            onCheckedChange = { h -> onUpdateClip(clipId) { it.copy(chartConfig = chart.copy(isHorizontal = h)) } },
            colors = SwitchDefaults.colors(checkedThumbColor = CyanPlayhead)
        )
    }

    // Individual Bar Editor (pick any of 1..barCount and edit label/value)
    val clampedIdx = selectedBarIdx.coerceIn(0, (chart.barCount - 1).coerceAtLeast(0))
    val activeBar = safeBars.getOrNull(clampedIdx)
    if (activeBar != null) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items((0 until chart.barCount).toList()) { idx ->
                FilterChip(
                    selected = clampedIdx == idx,
                    onClick = { selectedBarIdx = idx },
                    label = { Text("#${idx + 1}", style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = activeBar.label,
                onValueChange = { lbl ->
                    val updated = safeBars.toMutableList()
                    updated[clampedIdx] = activeBar.copy(label = lbl)
                    onUpdateClip(clipId) { it.copy(chartConfig = chart.copy(bars = updated)) }
                },
                label = { Text(if (isBangla) "বার #${clampedIdx + 1} নাম" else "Label") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyanPlayhead, unfocusedBorderColor = StudioBorder, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
                modifier = Modifier.weight(1f)
            )
        }
        InspectorSlider(
            label = if (isBangla) "মান (${activeBar.label})" else "Value",
            value = activeBar.value,
            range = 1f..100f,
            valueText = "${activeBar.value.toInt()}${chart.unitSuffix}"
        ) { v ->
            val updated = safeBars.toMutableList()
            updated[clampedIdx] = activeBar.copy(value = v)
            onUpdateClip(clipId) { it.copy(chartConfig = chart.copy(bars = updated)) }
        }
    }
}

@Composable
private fun ComparisonGridConfigEditor(
    clipId: String,
    grid: ComparisonGridConfig,
    isBangla: Boolean,
    onPickSlotMedia: (clipId: String, slotId: String) -> Unit,
    onUpdateClip: (String, (TimelineClip) -> TimelineClip) -> Unit
) {
    var activeSlotIdx by remember(clipId) { mutableIntStateOf(0) }
    val slots = grid.slots.take(grid.slotCount.coerceIn(2, 4))
    val safeIdx = activeSlotIdx.coerceIn(0, (slots.size - 1).coerceAtLeast(0))
    val currentSlot = slots.getOrNull(safeIdx)

    Text(
        text = if (isBangla) "তুলনামূলক ছবি/ভিডিও স্লট ও জ্যামিতিক শেপ" else "Comparison Slots & Geometric Shapes",
        style = MaterialTheme.typography.labelMedium,
        color = AmberDocumentary
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        slots.forEachIndexed { idx, slot ->
            FilterChip(
                selected = safeIdx == idx,
                onClick = { activeSlotIdx = idx },
                label = { Text(if (isBangla) "স্লট ${idx + 1}" else "Slot ${idx + 1}", style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AmberDocumentary,
                    selectedLabelColor = StudioObsidian
                )
            )
        }
    }

    if (currentSlot != null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = currentSlot.caption,
                onValueChange = { cap ->
                    val nextSlots = grid.slots.map { if (it.id == currentSlot.id) it.copy(caption = cap) else it }
                    onUpdateClip(clipId) { it.copy(comparisonGrid = grid.copy(slots = nextSlots)) }
                },
                label = { Text(if (isBangla) "স্লট ক্যাপশন" else "Slot Caption") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AmberDocumentary, unfocusedBorderColor = StudioBorder, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = { onPickSlotMedia(clipId, currentSlot.id) },
                colors = ButtonDefaults.buttonColors(containerColor = CyanPlayhead, contentColor = StudioObsidian),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (isBangla) "ছবি/ভিডিও" else "Media", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(GeometricShape.entries, key = { it.name }) { gShape ->
                FilterChip(
                    selected = currentSlot.shape == gShape,
                    onClick = {
                        val nextSlots = grid.slots.map { if (it.id == currentSlot.id) it.copy(shape = gShape) else it }
                        onUpdateClip(clipId) { it.copy(comparisonGrid = grid.copy(slots = nextSlots)) }
                    },
                    label = { Text(if (isBangla) gShape.labelBn else gShape.labelEn, style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyanPlayhead,
                        selectedLabelColor = StudioObsidian,
                        labelColor = TextSecondary
                    )
                )
            }
        }
    }
}

@Composable
private fun InvestigativeConfigEditor(
    clipId: String,
    doc: InvestigativeDocConfig,
    isBangla: Boolean,
    onUpdateClip: (String, (TimelineClip) -> TimelineClip) -> Unit
) {
    OutlinedTextField(
        value = doc.headline,
        onValueChange = { h -> onUpdateClip(clipId) { it.copy(investigativeConfig = doc.copy(headline = h)) } },
        label = { Text(if (isBangla) "নথি শিরোনাম" else "Document Headline") },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EmeraldAudio, unfocusedBorderColor = StudioBorder, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
        modifier = Modifier.fillMaxWidth()
    )
    if (doc.toolType == InvestigativeToolType.MAP_RADAR_ROUTE) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = doc.pointALabel,
                onValueChange = { a -> onUpdateClip(clipId) { it.copy(investigativeConfig = doc.copy(pointALabel = a)) } },
                label = { Text(if (isBangla) "শুরু (A)" else "From") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyanPlayhead, unfocusedBorderColor = StudioBorder, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = doc.pointBLabel,
                onValueChange = { b -> onUpdateClip(clipId) { it.copy(investigativeConfig = doc.copy(pointBLabel = b)) } },
                label = { Text(if (isBangla) "গন্তব্য (B)" else "To") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AmberDocumentary, unfocusedBorderColor = StudioBorder, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
                modifier = Modifier.weight(1f)
            )
        }
    } else if (doc.toolType == InvestigativeToolType.STAT_YEAR_COUNTER) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = doc.counterStart.toString(),
                onValueChange = { s ->
                    s.toIntOrNull()?.let { n -> onUpdateClip(clipId) { it.copy(investigativeConfig = doc.copy(counterStart = n)) } }
                },
                label = { Text(if (isBangla) "শুরু সংখ্যা/সাল" else "Start") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyanPlayhead, unfocusedBorderColor = StudioBorder, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = doc.counterEnd.toString(),
                onValueChange = { e ->
                    e.toIntOrNull()?.let { n -> onUpdateClip(clipId) { it.copy(investigativeConfig = doc.copy(counterEnd = n)) } }
                },
                label = { Text(if (isBangla) "শেষ সংখ্যা/সাল" else "End") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AmberDocumentary, unfocusedBorderColor = StudioBorder, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
                modifier = Modifier.weight(1f)
            )
        }
    } else {
        OutlinedTextField(
            value = doc.bodyText,
            onValueChange = { b -> onUpdateClip(clipId) { it.copy(investigativeConfig = doc.copy(bodyText = b)) } },
            label = { Text(if (isBangla) "হাইলাইট / নথির লেখা" else "Highlight Text") },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EmeraldAudio, unfocusedBorderColor = StudioBorder, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun InspectorSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Text(text = valueText, style = MaterialTheme.typography.labelSmall, color = AmberDocumentary, fontWeight = FontWeight.Bold)
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
            modifier = Modifier.height(28.dp)
        )
    }
}
