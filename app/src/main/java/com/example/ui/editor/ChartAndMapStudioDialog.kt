package com.example.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.model.ChartBarItem
import com.example.domain.model.ChartPaletteTheme
import com.example.domain.model.ChartVisualStyle
import com.example.domain.model.ComparisonChartConfig
import com.example.domain.model.InvestigativeDocConfig
import com.example.domain.model.InvestigativeToolType
import com.example.domain.model.MapRadarStyle
import com.example.domain.model.PresetCatalog
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
import java.util.UUID

@Composable
fun ChartStudioDialog(
    initialConfig: ComparisonChartConfig,
    isBangla: Boolean,
    onDismiss: () -> Unit,
    onSave: (ComparisonChartConfig) -> Unit
) {
    var draft by remember(initialConfig) {
        val count = initialConfig.barCount.coerceIn(0, 100)
        val ensuredBars = if (count > 0 && initialConfig.bars.size < count) {
            val gen = PresetCatalog.generateDefaultBars(count.coerceAtMost(60))
            initialConfig.bars + gen.drop(initialConfig.bars.size)
        } else {
            initialConfig.bars.take(count)
        }
        val resolvedStyle = if (initialConfig.chartStyle == ChartVisualStyle.HORIZONTAL_BAR && !initialConfig.isHorizontal) {
            ChartVisualStyle.VERTICAL_BAR
        } else {
            initialConfig.chartStyle
        }
        mutableStateOf(
            initialConfig.copy(
                barCount = ensuredBars.size,
                bars = ensuredBars,
                chartStyle = resolvedStyle
            )
        )
    }

    fun addSingleCustomBar() {
        if (draft.bars.size >= 100) return
        val nextIdx = draft.bars.size + 1
        val palette = draft.paletteTheme.colors
        val newItem = ChartBarItem(
            id = UUID.randomUUID().toString(),
            label = if (isBangla) "ডেটা $nextIdx" else "Item $nextIdx",
            value = 50f,
            colorHex = palette[(nextIdx - 1) % palette.size]
        )
        val nextBars = draft.bars + newItem
        draft = draft.copy(barCount = nextBars.size, bars = nextBars)
    }

    fun removeBarAt(idx: Int) {
        if (idx !in draft.bars.indices) return
        val nextBars = draft.bars.toMutableList().apply { removeAt(idx) }
        draft = draft.copy(barCount = nextBars.size, bars = nextBars)
    }

    fun clearAllBarsToZero() {
        draft = draft.copy(barCount = 0, bars = emptyList())
    }

    fun updateBarCount(targetCount: Int) {
        val nextCount = targetCount.coerceIn(0, 100)
        if (nextCount == 0) {
            draft = draft.copy(barCount = 0, bars = emptyList())
            return
        }
        val nextBars = if (draft.bars.size < nextCount) {
            val palette = draft.paletteTheme.colors
            val extra = (draft.bars.size until nextCount).map { globalIdx ->
                ChartBarItem(
                    id = UUID.randomUUID().toString(),
                    label = if (isBangla) "ডেটা ${globalIdx + 1}" else "Item ${globalIdx + 1}",
                    value = (90 - (globalIdx * 4) % 75).toFloat().coerceAtLeast(12f),
                    colorHex = palette[globalIdx % palette.size]
                )
            }
            draft.bars + extra
        } else {
            draft.bars.take(nextCount)
        }
        draft = draft.copy(barCount = nextBars.size, bars = nextBars)
    }

    fun applyTemplate(title: String, unit: String, labels: List<String>, values: List<Float>, theme: ChartPaletteTheme) {
        val count = labels.size.coerceIn(0, 100)
        val bars = (0 until count).map { i ->
            ChartBarItem(
                id = UUID.randomUUID().toString(),
                label = labels[i],
                value = values.getOrElse(i) { (100 - i).toFloat().coerceAtLeast(10f) },
                colorHex = theme.colors[i % theme.colors.size]
            )
        }
        draft = draft.copy(
            title = title,
            unitSuffix = unit,
            barCount = count,
            paletteTheme = theme,
            bars = bars
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            color = StudioObsidian,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.2.dp, AmberDocumentary.copy(alpha = 0.65f))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(StudioSurfaceElevated)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.BarChart, contentDescription = null, tint = CyanPlayhead, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (isBangla) "প্রো কাস্টম চার্ট ও গ্রাফ স্টুডিও" else "Pro Custom Chart & Graph Studio",
                                style = MaterialTheme.typography.titleSmall,
                                color = AmberDocumentary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isBangla) "${draft.bars.size}টি ডেটা · ০ থেকে নিজের মতো যোগ ও গ্রাফ স্টাইল বদলান" else "${draft.bars.size} items · Custom Graph Style",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextPrimary)
                    }
                }

                // Body
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    contentPadding = PaddingValues(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 7 Switchable Graph Styles!
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = if (isBangla) "গ্রাফের স্টাইল বদলান (বার / গোল পাই / ডোনাট / দাগ লাইন / ওয়েভ / রেডার)" else "Switch Graph Visualization Style",
                                style = MaterialTheme.typography.labelMedium,
                                color = AmberDocumentary,
                                fontWeight = FontWeight.Bold
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(ChartVisualStyle.entries, key = { it.name }) { visualStyle ->
                                    val active = draft.chartStyle == visualStyle
                                    FilterChip(
                                        selected = active,
                                        onClick = {
                                            draft = draft.copy(
                                                chartStyle = visualStyle,
                                                isHorizontal = when (visualStyle) {
                                                    ChartVisualStyle.HORIZONTAL_BAR -> true
                                                    ChartVisualStyle.VERTICAL_BAR -> false
                                                    else -> draft.isHorizontal
                                                }
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = if (isBangla) visualStyle.labelBn else visualStyle.labelEn,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
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
                    }

                    // Live Preview using shared ComparisonChartOverlay so all 7 styles & 0-state render identically!
                    item {
                        ComparisonChartOverlay(
                            config = draft,
                            animProgress = 1f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(195.dp)
                        )
                    }

                    // Quick Templates (starts with Zero Blank!)
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = if (isBangla) "শুরু করার টেমপ্লেট (ঐচ্ছিক)" else "Quick Templates (Optional)",
                                style = MaterialTheme.typography.labelMedium,
                                color = CyanPlayhead,
                                fontWeight = FontWeight.SemiBold
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item {
                                    FilterChip(
                                        selected = draft.bars.isEmpty(),
                                        onClick = { clearAllBarsToZero() },
                                        label = { Text(if (isBangla) "খালি / জিরো থেকে (০)" else "Start Empty (0)", style = MaterialTheme.typography.labelSmall) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = EmeraldAudio,
                                            selectedLabelColor = StudioObsidian
                                        )
                                    )
                                }
                                item {
                                    FilterChip(
                                        selected = draft.bars.size == 8 && draft.unitSuffix == "$",
                                        onClick = {
                                            applyTemplate(
                                                title = "দক্ষিণ এশিয়া GDP প্রবৃদ্ধি",
                                                unit = "$",
                                                labels = listOf("বাংলাদেশ", "ভারত", "পাকিস্তান", "শ্রীলঙ্কা", "নেপাল", "ভুটান", "মালদ্বীপ", "ভিয়েতনাম"),
                                                values = listOf(460f, 3730f, 340f, 84f, 41f, 3f, 7f, 430f),
                                                theme = ChartPaletteTheme.DOC_GOLD
                                            )
                                        },
                                        label = { Text(if (isBangla) "GDP অর্থনীতি (৮)" else "GDP Economy (8)", style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                                item {
                                    FilterChip(
                                        selected = draft.bars.size == 12,
                                        onClick = {
                                            val months = listOf("জানু", "ফেব্রু", "মার্চ", "এপ্রিল", "মে", "জুন", "জুলাই", "আগস্ট", "সেপ্টে", "অক্টো", "নভে", "ডিসে")
                                            applyTemplate(
                                                title = "মাসিক অনুসন্ধানী রিপোর্ট (১২ মাস)",
                                                unit = "K",
                                                labels = months,
                                                values = listOf(42f, 55f, 49f, 68f, 74f, 62f, 89f, 94f, 81f, 98f, 86f, 110f),
                                                theme = ChartPaletteTheme.EMERALD_GROWTH
                                            )
                                        },
                                        label = { Text(if (isBangla) "মাসিক ১২ (১২)" else "Monthly (12)", style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                                item {
                                    FilterChip(
                                        selected = draft.bars.size == 20,
                                        onClick = {
                                            val districts = listOf(
                                                "ঢাকা", "চট্টগ্রাম", "গাজীপুর", "নারায়ণগঞ্জ", "কুমিল্লা",
                                                "সিলেট", "রাজশাহী", "খুলনা", "বগুড়া", "ময়মনসিংহ",
                                                "রংপুর", "বরিশাল", "যশোর", "টাঙ্গাইল", "ফরিদপুর",
                                                "দিনাজপুর", "নরসিংদী", "কক্সবাজার", "পাবনা", "নোয়াখালী"
                                            )
                                            applyTemplate(
                                                title = "দেশের ২০ জেলার তুলনামূলক সূচক",
                                                unit = "%",
                                                labels = districts,
                                                values = List(20) { i -> (96f - i * 3.8f).coerceAtLeast(14f) },
                                                theme = ChartPaletteTheme.CYBER_CYAN
                                            )
                                        },
                                        label = { Text(if (isBangla) "জেলা তুলনা (২০)" else "Districts (20)", style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                                item {
                                    FilterChip(
                                        selected = draft.bars.size == 60,
                                        onClick = {
                                            val items60 = PresetCatalog.generateDefaultBars(60).mapIndexed { i, item ->
                                                item.copy(id = UUID.randomUUID().toString(), colorHex = ChartPaletteTheme.DEFAULT_MIX.colors[i % ChartPaletteTheme.DEFAULT_MIX.colors.size])
                                            }
                                            draft = draft.copy(
                                                title = "৬০-বার মেগা তুলনামূলক চার্ট",
                                                unitSuffix = "%",
                                                barCount = 60,
                                                paletteTheme = ChartPaletteTheme.DEFAULT_MIX,
                                                bars = items60
                                            )
                                        },
                                        label = { Text(if (isBangla) "৬০-বার মেগা (৬০)" else "60-Bar Mega (60)", style = MaterialTheme.typography.labelSmall) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = AmberDocumentary,
                                            selectedLabelColor = StudioObsidian
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Title + Unit Suffix
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = draft.title,
                                onValueChange = { draft = draft.copy(title = it) },
                                label = { Text(if (isBangla) "চার্ট শিরোনাম" else "Chart Title") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CyanPlayhead,
                                    unfocusedBorderColor = StudioBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = draft.unitSuffix,
                                onValueChange = { draft = draft.copy(unitSuffix = it.take(8)) },
                                label = { Text(if (isBangla) "একক (%, $, কোটি)" else "Unit") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AmberDocumentary,
                                    unfocusedBorderColor = StudioBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                modifier = Modifier.width(116.dp)
                            )
                        }
                    }

                    // Bar count stepper & slider (0..100) + Add / Clear controls
                    item {
                        Surface(
                            color = StudioSurface,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, StudioBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isBangla) "মোট ডেটা আইটেম: ${draft.bars.size} (০–১০০)" else "Total Items: ${draft.bars.size} (0–100)",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = AmberDocumentary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            onClick = { updateBarCount(draft.bars.size - 1) },
                                            color = StudioSurfaceElevated,
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, StudioBorder),
                                            modifier = Modifier.size(34.dp).testTag("chart_bar_minus_btn")
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.Remove, contentDescription = "Decrease bars", tint = TextPrimary, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                        Surface(
                                            onClick = { addSingleCustomBar() },
                                            color = StudioSurfaceElevated,
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, CyanPlayhead.copy(alpha = 0.6f)),
                                            modifier = Modifier.size(34.dp).testTag("chart_bar_plus_btn")
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.Add, contentDescription = "Increase bars", tint = CyanPlayhead, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                                Slider(
                                    value = draft.bars.size.toFloat(),
                                    onValueChange = { updateBarCount(it.toInt()) },
                                    valueRange = 0f..100f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = AmberDocumentary,
                                        activeTrackColor = AmberDocumentary,
                                        inactiveTrackColor = StudioObsidian
                                    )
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { addSingleCustomBar() },
                                        colors = ButtonDefaults.buttonColors(containerColor = CyanPlayhead, contentColor = StudioObsidian),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1.4f).testTag("add_chart_bar_btn")
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isBangla) "নতুন ডেটা যোগ করুন" else "Add New Item",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = { clearAllBarsToZero() },
                                        border = BorderStroke(1.dp, Color(0xFFFF4D6D).copy(alpha = 0.7f)),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f).testTag("clear_chart_bars_btn")
                                    ) {
                                        Text(
                                            text = if (isBangla) "সব মুছুন (০)" else "Clear (0)",
                                            color = Color(0xFFFF4D6D),
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(if (isBangla) "বড় থেকে ছোট সাজানো (Rank Sort)" else "Sort Descending", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                                    Switch(
                                        checked = draft.sortDescending,
                                        onCheckedChange = { draft = draft.copy(sortDescending = it) },
                                        colors = SwitchDefaults.colors(checkedThumbColor = EmeraldAudio)
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(if (isBangla) "সিনেমা গ্রোথ অ্যানিমেশন" else "Animate Growth", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                                    Switch(
                                        checked = draft.animateGrowth,
                                        onCheckedChange = { draft = draft.copy(animateGrowth = it) },
                                        colors = SwitchDefaults.colors(checkedThumbColor = AmberDocumentary)
                                    )
                                }
                            }
                        }
                    }

                    // Color Palette Theme selector
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isBangla) "কালার প্যালেট থিম" else "Chart Palette Theme",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = CyanPlayhead
                                )
                                Surface(
                                    onClick = {
                                        val palette = draft.paletteTheme.colors
                                        val recolored = draft.bars.mapIndexed { idx, item ->
                                            item.copy(colorHex = palette[idx % palette.size])
                                        }
                                        draft = draft.copy(bars = recolored)
                                    },
                                    color = StudioSurfaceElevated,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, AmberDocumentary.copy(alpha = 0.6f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Palette, contentDescription = null, tint = AmberDocumentary, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isBangla) "থিমের রঙ সব বারে দিন" else "Apply Palette",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AmberDocumentary
                                        )
                                    }
                                }
                            }
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(ChartPaletteTheme.entries, key = { it.name }) { theme ->
                                    val active = draft.paletteTheme == theme
                                    FilterChip(
                                        selected = active,
                                        onClick = {
                                            val recolored = draft.bars.mapIndexed { idx, item ->
                                                item.copy(colorHex = theme.colors[idx % theme.colors.size])
                                            }
                                            draft = draft.copy(paletteTheme = theme, bars = recolored)
                                        },
                                        label = { Text(if (isBangla) theme.labelBn else theme.labelEn, style = MaterialTheme.typography.labelSmall) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(theme.colors.first()),
                                            selectedLabelColor = StudioObsidian,
                                            labelColor = TextPrimary
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Bar items header / Empty state prompt
                    item {
                        Text(
                            text = if (isBangla) "আপনার ডেটা তালিকা (${draft.bars.size}টি)" else "Your Data Entries (${draft.bars.size})",
                            style = MaterialTheme.typography.labelMedium,
                            color = AmberDocumentary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (draft.bars.isEmpty()) {
                        item {
                            Surface(
                                color = StudioSurface,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, CyanPlayhead.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = if (isBangla) "এখন চার্টটি খালি (০টি ডেটা)। নিচের বাটনে চাপ দিয়ে নিজের মতো নাম, মান ও রঙ যোগ করুন।" else "Chart is empty (0 items). Tap below to add your own entries.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                        textAlign = TextAlign.Center
                                    )
                                    Button(
                                        onClick = { addSingleCustomBar() },
                                        colors = ButtonDefaults.buttonColors(containerColor = CyanPlayhead, contentColor = StudioObsidian)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(if (isBangla) "+ প্রথম ডেটা যোগ করুন" else "+ Add First Entry", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // Individual Bar Editor rows
                    items((0 until draft.bars.size).toList(), key = { idx -> draft.bars[idx].id }) { idx ->
                        val item = draft.bars.getOrNull(idx) ?: return@items
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(StudioSurface)
                                .border(1.dp, StudioBorder, RoundedCornerShape(10.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "#${idx + 1}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = AmberDocumentary,
                                modifier = Modifier.width(26.dp)
                            )
                            OutlinedTextField(
                                value = item.label,
                                onValueChange = { nextLabel ->
                                    val updated = draft.bars.toMutableList()
                                    updated[idx] = item.copy(label = nextLabel)
                                    draft = draft.copy(bars = updated, barCount = updated.size)
                                },
                                label = { Text(if (isBangla) "নাম" else "Label", fontSize = 10.sp) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CyanPlayhead,
                                    unfocusedBorderColor = StudioBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                modifier = Modifier.weight(1.2f)
                            )
                            var valStr by remember(item.id, item.value) {
                                mutableStateOf(
                                    if (item.value % 1f == 0f) item.value.toInt().toString()
                                    else "%.1f".format(item.value)
                                )
                            }
                            OutlinedTextField(
                                value = valStr,
                                onValueChange = { raw ->
                                    valStr = raw
                                    raw.toFloatOrNull()?.let { parsed ->
                                        val clamped = parsed.coerceIn(0f, 10_000f)
                                        val updated = draft.bars.toMutableList()
                                        updated[idx] = item.copy(value = clamped)
                                        draft = draft.copy(bars = updated, barCount = updated.size)
                                    }
                                },
                                label = { Text(if (isBangla) "মান" else "Value", fontSize = 10.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AmberDocumentary,
                                    unfocusedBorderColor = StudioBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                modifier = Modifier.weight(0.75f)
                            )
                            // Clickable color dot cycling through swatches
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(item.colorHex))
                                    .border(2.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                                    .clickable {
                                        val swatches = PresetCatalog.textColorSwatches
                                        val curIdx = swatches.indexOfFirst { (it and 0x00FFFFFFL) == (item.colorHex and 0x00FFFFFFL) }
                                        val nextColor = swatches[(curIdx + 1) % swatches.size]
                                        val updated = draft.bars.toMutableList()
                                        updated[idx] = item.copy(colorHex = nextColor)
                                        draft = draft.copy(bars = updated)
                                    }
                            )
                            IconButton(
                                onClick = { removeBarAt(idx) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = "Delete Entry",
                                    tint = Color(0xFFFF4D6D),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Bottom Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(StudioSurfaceElevated)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        border = BorderStroke(1.dp, StudioBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isBangla) "বাতিল" else "Cancel", color = TextSecondary)
                    }
                    Button(
                        onClick = { onSave(draft.copy(barCount = draft.bars.size)) },
                        colors = ButtonDefaults.buttonColors(containerColor = AmberDocumentary, contentColor = StudioObsidian),
                        modifier = Modifier.weight(1.4f).testTag("save_chart_studio_btn")
                    ) {
                        Text(if (isBangla) "সেভ ও প্রয়োগ করুন" else "Save & Apply", fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}

@Composable
fun MapRadarStudioDialog(
    initialConfig: InvestigativeDocConfig,
    isBangla: Boolean,
    onDismiss: () -> Unit,
    onSave: (InvestigativeDocConfig) -> Unit
) {
    var draft by remember(initialConfig) {
        mutableStateOf(
            if (initialConfig.toolType == InvestigativeToolType.MAP_RADAR_ROUTE) initialConfig
            else initialConfig.copy(toolType = InvestigativeToolType.MAP_RADAR_ROUTE)
        )
    }
    var activeMarker by remember { mutableIntStateOf(0) } // 0 = A, 1 = B, 2 = C
    val activeMarkerState by rememberUpdatedState(activeMarker)
    val draftState by rememberUpdatedState(draft)

    fun moveMarkerTo(normX: Float, normY: Float) {
        val cx = normX.coerceIn(0.08f, 0.92f)
        val cy = normY.coerceIn(0.08f, 0.92f)
        draft = when (activeMarkerState) {
            0 -> draftState.copy(pointAX = cx, pointAY = cy)
            1 -> draftState.copy(pointBX = cx, pointBY = cy)
            else -> draftState.copy(pointCX = cx, pointCY = cy, useWaypointC = true)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            color = StudioObsidian,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.2.dp, CyanPlayhead.copy(alpha = 0.65f))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(StudioSurfaceElevated)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Explore, contentDescription = null, tint = EmeraldAudio, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (isBangla) "ইন্টার‍্যাক্টিভ ম্যাপ রাডার ও রুট স্টুডিও" else "Interactive Map Radar Studio",
                                style = MaterialTheme.typography.titleSmall,
                                color = EmeraldAudio,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isBangla) "ম্যাপে ট্যাপ বা ড্র্যাগ করে A / B / C পয়েন্ট বসান" else "Tap or drag on map to place points",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextPrimary)
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Map Radar Style Chips
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(MapRadarStyle.entries, key = { it.name }) { style ->
                            val selected = draft.mapStyle == style
                            FilterChip(
                                selected = selected,
                                onClick = { draft = draft.copy(mapStyle = style) },
                                label = { Text(if (isBangla) style.labelBn else style.labelEn, style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(style.routeHex),
                                    selectedLabelColor = StudioObsidian,
                                    labelColor = TextPrimary
                                )
                            )
                        }
                    }

                    // Active Marker Selector
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            0 to (if (isBangla) "পয়েন্ট A (${draft.pointALabel})" else "Point A"),
                            1 to (if (isBangla) "পয়েন্ট B (${draft.pointBLabel})" else "Point B"),
                            2 to (if (isBangla) "ওয়েপয়েন্ট C" else "Waypoint C")
                        ).forEach { (idx, label) ->
                            val isSel = activeMarker == idx
                            FilterChip(
                                selected = isSel,
                                onClick = {
                                    activeMarker = idx
                                    if (idx == 2 && !draft.useWaypointC) {
                                        draft = draft.copy(useWaypointC = true)
                                    }
                                },
                                label = { Text(label, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = when (idx) {
                                        0 -> CyanPlayhead
                                        1 -> AmberDocumentary
                                        else -> EmeraldAudio
                                    },
                                    selectedLabelColor = StudioObsidian,
                                    labelColor = TextPrimary
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Interactive Drag & Tap Map Canvas
                    val style = draft.mapStyle
                    Surface(
                        color = Color(style.bgHex),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.5.dp, Color(style.markerHex).copy(alpha = 0.7f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                            .testTag("map_radar_interactive_canvas")
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Canvas(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        detectTapGestures { offset ->
                                            if (size.width > 0 && size.height > 0) {
                                                moveMarkerTo(offset.x / size.width.toFloat(), offset.y / size.height.toFloat())
                                            }
                                        }
                                    }
                                    .pointerInput(Unit) {
                                        detectDragGestures { change, _ ->
                                            change.consume()
                                            if (size.width > 0 && size.height > 0) {
                                                moveMarkerTo(change.position.x / size.width.toFloat(), change.position.y / size.height.toFloat())
                                            }
                                        }
                                    }
                            ) {
                                val gridColor = Color(style.gridHex).copy(alpha = 0.16f)
                                val cols = 8
                                val rows = 6
                                for (c in 1 until cols) {
                                    val x = size.width * (c.toFloat() / cols)
                                    drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                                }
                                for (r in 1 until rows) {
                                    val y = size.height * (r.toFloat() / rows)
                                    drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                                }

                                val pA = Offset(size.width * draft.pointAX, size.height * draft.pointAY)
                                val pB = Offset(size.width * draft.pointBX, size.height * draft.pointBY)
                                val pCtrl = if (draft.useWaypointC) {
                                    Offset(size.width * draft.pointCX, size.height * draft.pointCY)
                                } else {
                                    Offset((pA.x + pB.x) * 0.5f, minOf(pA.y, pB.y) - size.height * 0.22f)
                                }

                                val routeColor = Color(style.routeHex)
                                val markerColor = Color(style.markerHex)
                                val path = Path().apply {
                                    moveTo(pA.x, pA.y)
                                    quadraticTo(pCtrl.x, pCtrl.y, pB.x, pB.y)
                                }
                                drawPath(
                                    path = path,
                                    color = routeColor.copy(alpha = 0.28f),
                                    style = Stroke(width = 10f, cap = StrokeCap.Round)
                                )
                                drawPath(
                                    path = path,
                                    color = routeColor,
                                    style = Stroke(
                                        width = 4f,
                                        cap = StrokeCap.Round,
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 8f), 0f)
                                    )
                                )

                                // Draw Sonar Rings + Point A
                                drawCircle(markerColor.copy(alpha = 0.25f), radius = 26f, center = pA, style = Stroke(width = 2f))
                                drawCircle(markerColor, radius = 9f, center = pA)

                                // Draw Point B
                                drawCircle(routeColor.copy(alpha = 0.25f), radius = 26f, center = pB, style = Stroke(width = 2f))
                                drawCircle(routeColor, radius = 9f, center = pB)

                                // Draw Waypoint C if active
                                if (draft.useWaypointC) {
                                    drawCircle(EmeraldAudio.copy(alpha = 0.3f), radius = 20f, center = pCtrl, style = Stroke(width = 2f))
                                    drawCircle(EmeraldAudio, radius = 7f, center = pCtrl)
                                }
                            }

                            // Corner live telemetry
                            Text(
                                text = "A(${(draft.pointAX * 100).toInt()}%,${(draft.pointAY * 100).toInt()}%) → B(${(draft.pointBX * 100).toInt()}%,${(draft.pointBY * 100).toInt()}%)",
                                fontSize = 10.sp,
                                color = Color(style.markerHex),
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(8.dp)
                                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                            if (draft.distanceLabel.isNotBlank()) {
                                Text(
                                    text = draft.distanceLabel,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = StudioObsidian,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                        .background(Color(style.routeHex), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // Fields
                    OutlinedTextField(
                        value = draft.headline,
                        onValueChange = { draft = draft.copy(headline = it) },
                        label = { Text(if (isBangla) "ম্যাপ হেডলাইন" else "Map Headline") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EmeraldAudio, unfocusedBorderColor = StudioBorder, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = draft.pointALabel,
                            onValueChange = { draft = draft.copy(pointALabel = it) },
                            label = { Text(if (isBangla) "শুরু (A)" else "Point A") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyanPlayhead, unfocusedBorderColor = StudioBorder, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = draft.pointBLabel,
                            onValueChange = { draft = draft.copy(pointBLabel = it) },
                            label = { Text(if (isBangla) "গন্তব্য (B)" else "Point B") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AmberDocumentary, unfocusedBorderColor = StudioBorder, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (isBangla) "মধ্যবর্তী ওয়েপয়েন্ট (C) যোগ করুন" else "Enable Waypoint C", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                        Switch(
                            checked = draft.useWaypointC,
                            onCheckedChange = { draft = draft.copy(useWaypointC = it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = EmeraldAudio)
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (draft.useWaypointC) {
                            OutlinedTextField(
                                value = draft.pointCLabel,
                                onValueChange = { draft = draft.copy(pointCLabel = it) },
                                label = { Text(if (isBangla) "ওয়েপয়েন্ট (C)" else "Waypoint C") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EmeraldAudio, unfocusedBorderColor = StudioBorder, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        OutlinedTextField(
                            value = draft.distanceLabel,
                            onValueChange = { draft = draft.copy(distanceLabel = it) },
                            label = { Text(if (isBangla) "দূরত্ব / ব্যাজ" else "Distance Badge") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AmberDocumentary, unfocusedBorderColor = StudioBorder, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Bottom Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(StudioSurfaceElevated)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        border = BorderStroke(1.dp, StudioBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isBangla) "বাতিল" else "Cancel", color = TextSecondary)
                    }
                    Button(
                        onClick = { onSave(draft) },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldAudio, contentColor = StudioObsidian),
                        modifier = Modifier.weight(1.4f).testTag("save_map_studio_btn")
                    ) {
                        Text(if (isBangla) "সেভ ও প্রয়োগ করুন" else "Save & Apply", fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}
