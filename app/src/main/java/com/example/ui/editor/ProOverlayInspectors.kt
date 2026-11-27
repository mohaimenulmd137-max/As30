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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
fun TransitionStudioInspector(
    clip: TimelineClip?,
    isBangla: Boolean,
    onUpdateClip: (String, (TimelineClip) -> TimelineClip) -> Unit,
    onApplyTransitionToAll: (TransitionType, Long, TransitionType, Long) -> Unit
) {
    if (clip == null) {
        Text(
            text = if (isBangla) "ট্রানজিশন সেট করতে টাইমলাইনে যেকোনো ক্লিপ সিলেক্ট করুন" else "Select any timeline clip to configure transitions",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        return
    }

    // Start (Entry) Transition
    Text(
        text = if (isBangla) "শুরুর ট্রানজিশন (Start / In)" else "Start Transition (In)",
        style = MaterialTheme.typography.labelMedium,
        color = AmberDocumentary,
        fontWeight = FontWeight.Bold
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(TransitionType.entries, key = { "in_${it.name}" }) { tr ->
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
    if (clip.transitionIn != TransitionType.NONE) {
        InspectorSlider(
            label = if (isBangla) "শুরুর সময়কাল" else "Start Duration",
            value = clip.transitionInDurationMs.toFloat(),
            range = 200f..2500f,
            valueText = "%.2fs".format(clip.transitionInDurationMs / 1000f)
        ) { ms ->
            onUpdateClip(clip.id) { it.copy(transitionInDurationMs = ms.toLong()) }
        }
    }

    // End (Exit) Transition
    Text(
        text = if (isBangla) "শেষের ট্রানজিশন (End / Out)" else "End Transition (Out)",
        style = MaterialTheme.typography.labelMedium,
        color = CyanPlayhead,
        fontWeight = FontWeight.Bold
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(TransitionType.entries, key = { "out_${it.name}" }) { tr ->
            FilterChip(
                selected = clip.transitionOut == tr,
                onClick = { onUpdateClip(clip.id) { it.copy(transitionOut = tr) } },
                label = { Text(if (isBangla) tr.labelBn else tr.labelEn, style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CyanPlayhead,
                    selectedLabelColor = StudioObsidian,
                    labelColor = TextSecondary
                )
            )
        }
    }
    if (clip.transitionOut != TransitionType.NONE) {
        InspectorSlider(
            label = if (isBangla) "শেষের সময়কাল" else "End Duration",
            value = clip.transitionOutDurationMs.toFloat(),
            range = 200f..2500f,
            valueText = "%.2fs".format(clip.transitionOutDurationMs / 1000f)
        ) { ms ->
            onUpdateClip(clip.id) { it.copy(transitionOutDurationMs = ms.toLong()) }
        }
    }

    Button(
        onClick = {
            onApplyTransitionToAll(
                clip.transitionIn,
                clip.transitionInDurationMs,
                clip.transitionOut,
                clip.transitionOutDurationMs
            )
        },
        colors = ButtonDefaults.buttonColors(containerColor = AmberDocumentary.copy(alpha = 0.2f), contentColor = AmberDocumentary),
        border = BorderStroke(1.dp, AmberDocumentary.copy(alpha = 0.65f)),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = if (isBangla) "সব ভিডিও ক্লিপে প্রয়োগ করুন" else "Apply to All Video Clips",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun VfxStudioInspector(
    clip: TimelineClip?,
    isBangla: Boolean,
    onAddVfxClip: (VfxEffectType) -> Unit,
    onUpdateClip: (String, (TimelineClip) -> TimelineClip) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (isBangla) "হলিউড VFX লেয়ার (১২+ ইফেক্ট)" else "Hollywood VFX Layers",
            style = MaterialTheme.typography.labelMedium,
            color = CyanPlayhead,
            fontWeight = FontWeight.Bold
        )
        Surface(
            onClick = { onAddVfxClip(VfxEffectType.VOLUMETRIC_GOD_RAYS) },
            color = CyanPlayhead.copy(alpha = 0.16f),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, CyanPlayhead.copy(alpha = 0.65f))
        ) {
            Text(
                text = if (isBangla) "+ নতুন VFX লেয়ার" else "+ New VFX Layer",
                style = MaterialTheme.typography.labelSmall,
                color = CyanPlayhead,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }

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
}

@Composable
fun OverlayProInspector(
    clip: TimelineClip?,
    isBangla: Boolean,
    onAddChart: (Int) -> Unit,
    onAddInvestigativeTool: (InvestigativeToolType) -> Unit,
    onOpenChartStudio: () -> Unit,
    onOpenMapStudio: () -> Unit,
    onUpdateClip: (String, (TimelineClip) -> TimelineClip) -> Unit
) {
    // Prominent Pro Studio launchers + Quick Add
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            onClick = onOpenChartStudio,
            color = Color(0xFF102235),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, CyanPlayhead.copy(alpha = 0.7f)),
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.BarChart, contentDescription = null, tint = CyanPlayhead, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isBangla) "📊 কাস্টম চার্ট ও গ্রাফ স্টুডিও" else "Chart Studio",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyanPlayhead,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Surface(
            onClick = onOpenMapStudio,
            color = Color(0xFF0E261E),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, EmeraldAudio.copy(alpha = 0.7f)),
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = EmeraldAudio, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isBangla) "🗺️ ম্যাপ রাডার স্টুডিও" else "Map Studio",
                    style = MaterialTheme.typography.labelSmall,
                    color = EmeraldAudio,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    // Quick-add pills for Investigative Documentary Tools
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Surface(
                onClick = { onAddChart(0) },
                color = StudioSurfaceElevated,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, AmberDocumentary.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.BarChart, contentDescription = null, tint = AmberDocumentary, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(if (isBangla) "+ নতুন চার্ট (০ থেকে)" else "+ New Chart (0)", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                }
            }
        }
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

    // Selected Clip Specific Editor: Custom Chart
    clip?.chartConfig?.let { chart ->
        ChartConfigEditor(
            clipId = clip.id,
            chart = chart,
            isBangla = isBangla,
            onOpenChartStudio = onOpenChartStudio,
            onUpdateClip = onUpdateClip
        )
    }

    // Selected Clip Specific Editor: Investigative Doc Card
    clip?.investigativeConfig?.let { doc ->
        InvestigativeConfigEditor(
            clipId = clip.id,
            doc = doc,
            isBangla = isBangla,
            onOpenMapStudio = onOpenMapStudio,
            onUpdateClip = onUpdateClip
        )
    }
}

@Composable
private fun ChartConfigEditor(
    clipId: String,
    chart: ComparisonChartConfig,
    isBangla: Boolean,
    onOpenChartStudio: () -> Unit,
    onUpdateClip: (String, (TimelineClip) -> TimelineClip) -> Unit
) {
    var selectedBarIdx by remember(clipId) { mutableIntStateOf(0) }
    val safeCount = chart.barCount.coerceIn(0, 100)
    val safeBars = chart.bars.take(safeCount)

    OutlinedTextField(
        value = chart.title,
        onValueChange = { t -> onUpdateClip(clipId) { it.copy(chartConfig = chart.copy(title = t)) } },
        label = { Text(if (isBangla) "চার্ট শিরোনাম" else "Chart Title") },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyanPlayhead, unfocusedBorderColor = StudioBorder, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
        modifier = Modifier.fillMaxWidth()
    )

    // Graph Visualization Style Switcher
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(com.example.domain.model.ChartVisualStyle.entries, key = { it.name }) { visualStyle ->
            val active = chart.chartStyle == visualStyle
            FilterChip(
                selected = active,
                onClick = {
                    onUpdateClip(clipId) {
                        it.copy(
                            chartConfig = chart.copy(
                                chartStyle = visualStyle,
                                isHorizontal = when (visualStyle) {
                                    com.example.domain.model.ChartVisualStyle.HORIZONTAL_BAR -> true
                                    com.example.domain.model.ChartVisualStyle.VERTICAL_BAR -> false
                                    else -> chart.isHorizontal
                                }
                            )
                        )
                    }
                },
                label = { Text(if (isBangla) visualStyle.labelBn else visualStyle.labelEn, style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CyanPlayhead,
                    selectedLabelColor = StudioObsidian,
                    labelColor = TextSecondary
                )
            )
        }
    }

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(com.example.domain.model.ChartPaletteTheme.entries, key = { it.name }) { theme ->
            val active = chart.paletteTheme == theme
            FilterChip(
                selected = active,
                onClick = {
                    val recolored = safeBars.mapIndexed { idx, item ->
                        item.copy(colorHex = theme.colors[idx % theme.colors.size])
                    }
                    onUpdateClip(clipId) { it.copy(chartConfig = chart.copy(paletteTheme = theme, bars = recolored)) }
                },
                label = { Text(if (isBangla) theme.labelBn else theme.labelEn, style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(theme.colors.first()),
                    selectedLabelColor = StudioObsidian,
                    labelColor = TextSecondary
                )
            )
        }
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = {
                if (safeBars.size < 100) {
                    val idx = safeBars.size + 1
                    val palette = chart.paletteTheme.colors
                    val added = safeBars + com.example.domain.model.ChartBarItem(
                        label = if (isBangla) "ডেটা $idx" else "Item $idx",
                        value = 50f,
                        colorHex = palette[(idx - 1) % palette.size]
                    )
                    onUpdateClip(clipId) { it.copy(chartConfig = chart.copy(barCount = added.size, bars = added)) }
                    selectedBarIdx = added.lastIndex
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = AmberDocumentary, contentColor = StudioObsidian),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.weight(1.2f)
        ) {
            Text(if (isBangla) "+ ১টি ডেটা যোগ (${safeBars.size})" else "+ Add Item (${safeBars.size})", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
        OutlinedButton(
            onClick = {
                onUpdateClip(clipId) { it.copy(chartConfig = chart.copy(barCount = 0, bars = emptyList())) }
            },
            border = BorderStroke(1.dp, Color(0xFFFF4D6D).copy(alpha = 0.6f)),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.weight(0.8f)
        ) {
            Text(if (isBangla) "সব মুছুন (০)" else "Clear (0)", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFF4D6D))
        }
    }

    val clampedIdx = selectedBarIdx.coerceIn(0, (safeBars.size - 1).coerceAtLeast(0))
    val activeBar = safeBars.getOrNull(clampedIdx)
    if (activeBar != null) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items((0 until safeBars.size).toList()) { idx ->
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
                    onUpdateClip(clipId) { it.copy(chartConfig = chart.copy(bars = updated, barCount = updated.size)) }
                },
                label = { Text(if (isBangla) "আইটেম #${clampedIdx + 1} নাম" else "Label") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyanPlayhead, unfocusedBorderColor = StudioBorder, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
                modifier = Modifier.weight(1f)
            )
        }
        InspectorSlider(
            label = if (isBangla) "মান (${activeBar.label})" else "Value",
            value = activeBar.value,
            range = 1f..1000f,
            valueText = "${activeBar.value.toInt()}${chart.unitSuffix}"
        ) { v ->
            val updated = safeBars.toMutableList()
            updated[clampedIdx] = activeBar.copy(value = v)
            onUpdateClip(clipId) { it.copy(chartConfig = chart.copy(bars = updated, barCount = updated.size)) }
        }
    }

    Button(
        onClick = onOpenChartStudio,
        colors = ButtonDefaults.buttonColors(containerColor = CyanPlayhead, contentColor = StudioObsidian),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(if (isBangla) "ফুল-স্ক্রিন চার্ট ও গ্রাফ স্টুডিও খুলুন" else "Open Full Chart Studio", fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun InvestigativeConfigEditor(
    clipId: String,
    doc: InvestigativeDocConfig,
    isBangla: Boolean,
    onOpenMapStudio: () -> Unit,
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
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(com.example.domain.model.MapRadarStyle.entries, key = { it.name }) { style ->
                FilterChip(
                    selected = doc.mapStyle == style,
                    onClick = { onUpdateClip(clipId) { it.copy(investigativeConfig = doc.copy(mapStyle = style)) } },
                    label = { Text(if (isBangla) style.labelBn else style.labelEn, style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(style.routeHex),
                        selectedLabelColor = StudioObsidian,
                        labelColor = TextSecondary
                    )
                )
            }
        }
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
        OutlinedTextField(
            value = doc.distanceLabel,
            onValueChange = { d -> onUpdateClip(clipId) { it.copy(investigativeConfig = doc.copy(distanceLabel = d)) } },
            label = { Text(if (isBangla) "দূরত্ব ব্যাজ" else "Distance Badge") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EmeraldAudio, unfocusedBorderColor = StudioBorder, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = onOpenMapStudio,
            colors = ButtonDefaults.buttonColors(containerColor = EmeraldAudio, contentColor = StudioObsidian),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isBangla) "ম্যাপে ট্যাপ করে A/B/C রুট সাজান" else "Open Interactive Map Studio", fontWeight = FontWeight.Bold)
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
