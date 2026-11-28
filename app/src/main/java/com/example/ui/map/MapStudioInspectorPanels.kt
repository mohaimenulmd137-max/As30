package com.example.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.map.MapCameraEasing
import com.example.domain.map.MapCameraKeyframe
import com.example.domain.map.MapLatLon
import com.example.domain.map.MapMarkerLayer
import com.example.domain.map.MapMarkerType
import com.example.domain.map.MapPresetCatalog
import com.example.domain.map.MapPresetCategory
import com.example.domain.map.MapProjectionMode
import com.example.domain.map.MapRegionLayer
import com.example.domain.map.MapRouteLayer
import com.example.domain.map.MapRouteStyle
import com.example.domain.map.MapRouteTipIcon
import com.example.domain.map.MapTextLayer
import com.example.domain.map.MapThemeStyle
import com.example.domain.map.UniversalMapConfig
import com.example.domain.map.WorldCartographyData
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

private val mapColorSwatches = listOf(
    0xFFFFB300L, 0xFF00E5FFL, 0xFF2CE59BL, 0xFFFF4D6DL,
    0xFFF97316L, 0xFF38BDF8L, 0xFFA855F7L, 0xFFEF4444L,
    0xFFFACC15L, 0xFFFFFFFFL
)

@Composable
internal fun MapPresetsTab(
    isBangla: Boolean,
    onApplyPreset: (UniversalMapConfig) -> Unit
) {
    var selectedCat by remember { mutableStateOf<MapPresetCategory?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = if (isBangla) "সিনেমাটিক ম্যাপ অ্যানিমেশন প্রিসেট" else "Documentary Map Presets",
            style = MaterialTheme.typography.labelLarge,
            color = AmberDocumentary,
            fontWeight = FontWeight.Bold
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                FilterChip(
                    selected = selectedCat == null,
                    onClick = { selectedCat = null },
                    label = { Text(if (isBangla) "সব" else "All", fontSize = 11.sp) }
                )
            }
            items(MapPresetCategory.entries, key = { it.name }) { cat ->
                FilterChip(
                    selected = selectedCat == cat,
                    onClick = { selectedCat = cat },
                    label = { Text(if (isBangla) cat.labelBn else cat.labelEn, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AmberDocumentary,
                        selectedLabelColor = StudioObsidian,
                        labelColor = TextPrimary
                    )
                )
            }
        }

        val filtered = remember(selectedCat) {
            if (selectedCat == null) MapPresetCatalog.presets
            else MapPresetCatalog.presets.filter { it.category == selectedCat }
        }
        filtered.forEach { preset ->
            Surface(
                onClick = { onApplyPreset(preset.config) },
                color = StudioSurface,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, StudioBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, null, tint = AmberDocumentary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (isBangla) preset.titleBn else preset.titleEn,
                                style = MaterialTheme.typography.labelLarge,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = preset.descriptionBn,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = CyanPlayhead.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, CyanPlayhead.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = if (preset.config.projectionMode == MapProjectionMode.GLOBE_3D) "3D" else "2D",
                            color = CyanPlayhead,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun MapCameraTab(
    draft: UniversalMapConfig,
    scrubFrac: Float,
    liveLat: Float,
    liveLon: Float,
    liveZoom: Float,
    liveRot: Float,
    liveTilt: Float,
    isBangla: Boolean,
    onUpdateLiveView: (lat: Float, lon: Float, zoom: Float, rot: Float, tilt: Float) -> Unit,
    onUpdateDraft: (UniversalMapConfig) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = draft.title,
                onValueChange = { onUpdateDraft(draft.copy(title = it)) },
                label = { Text(if (isBangla) "শিরোনাম" else "Map Title", fontSize = 11.sp) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AmberDocumentary, unfocusedBorderColor = StudioBorder, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
                modifier = Modifier.weight(1.2f)
            )
            OutlinedTextField(
                value = draft.subtitle,
                onValueChange = { onUpdateDraft(draft.copy(subtitle = it)) },
                label = { Text(if (isBangla) "উপ-শিরোনাম" else "Subtitle", fontSize = 11.sp) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyanPlayhead, unfocusedBorderColor = StudioBorder, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
                modifier = Modifier.weight(1f)
            )
        }

        // 2D vs 3D Globe + Theme Styles
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MapProjectionMode.entries.forEach { mode ->
                val sel = draft.projectionMode == mode
                FilterChip(
                    selected = sel,
                    onClick = { onUpdateDraft(draft.copy(projectionMode = mode)) },
                    leadingIcon = { Icon(if (mode == MapProjectionMode.GLOBE_3D) Icons.Default.Public else Icons.Default.Explore, null, modifier = Modifier.size(15.dp)) },
                    label = { Text(if (isBangla) mode.labelBn else mode.labelEn, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyanPlayhead,
                        selectedLabelColor = StudioObsidian,
                        labelColor = TextPrimary
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Text(
            text = if (isBangla) "ম্যাপ থিম ও ডিজাইন" else "Map Style Theme",
            style = MaterialTheme.typography.labelMedium,
            color = AmberDocumentary,
            fontWeight = FontWeight.Bold
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(MapThemeStyle.entries, key = { it.name }) { st ->
                FilterChip(
                    selected = draft.themeStyle == st,
                    onClick = { onUpdateDraft(draft.copy(themeStyle = st)) },
                    label = { Text(if (isBangla) st.labelBn else st.labelEn, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(st.accentHex),
                        selectedLabelColor = StudioObsidian,
                        labelColor = TextPrimary
                    )
                )
            }
        }

        // Camera Presets (Cinematic Zoom, Flyover, Orbit, Pan)
        Text(
            text = if (isBangla) "কুইক ক্যামেরা অ্যানিমেশন প্রিসেট" else "Camera Path Presets",
            style = MaterialTheme.typography.labelMedium,
            color = CyanPlayhead,
            fontWeight = FontWeight.Bold
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                FilterChip(
                    selected = false,
                    onClick = {
                        onUpdateDraft(
                            draft.copy(
                                keyframes = listOf(
                                    MapCameraKeyframe("cz1", "দূর", 0f, liveLat, liveLon, 1.2f, 0f, 0f, MapCameraEasing.EASE_IN_OUT),
                                    MapCameraKeyframe("cz2", "ফোকাস জুম", 0.85f, liveLat, liveLon, 5.4f, 0f, 18f, MapCameraEasing.CINEMA_FLIGHT)
                                )
                            )
                        )
                    },
                    label = { Text(if (isBangla) "সিনেম্যাটিক জুম" else "Cinematic Zoom", fontSize = 11.sp) }
                )
            }
            item {
                FilterChip(
                    selected = false,
                    onClick = {
                        onUpdateDraft(
                            draft.copy(
                                projectionMode = MapProjectionMode.GLOBE_3D,
                                keyframes = listOf(
                                    MapCameraKeyframe("orb1", "পশ্চিম", 0f, 24f, liveLon - 55f, 1.5f, 0f, 8f, MapCameraEasing.LINEAR),
                                    MapCameraKeyframe("orb2", "অরবিট", 1f, 24f, liveLon + 65f, 1.7f, 0f, 14f, MapCameraEasing.EASE_IN_OUT)
                                )
                            )
                        )
                    },
                    label = { Text(if (isBangla) "৩ডি গ্লোব রোটেট" else "3D Orbit", fontSize = 11.sp) }
                )
            }
            item {
                FilterChip(
                    selected = false,
                    onClick = {
                        onUpdateDraft(
                            draft.copy(
                                keyframes = listOf(
                                    MapCameraKeyframe("pan1", "বাম", 0f, liveLat, liveLon - 18f, 3.2f, -4f, 15f, MapCameraEasing.EASE_IN_OUT),
                                    MapCameraKeyframe("pan2", "ডান প্যান", 1f, liveLat, liveLon + 18f, 3.4f, 4f, 20f, MapCameraEasing.CINEMA_FLIGHT)
                                )
                            )
                        )
                    },
                    label = { Text(if (isBangla) "ক্যামেরা প্যান ও টিল্ট" else "Pan & Tilt", fontSize = 11.sp) }
                )
            }
        }

        // Direct Camera Sliders + Save to Keyframe
        Surface(
            color = StudioSurface,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, StudioBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (isBangla) "ক্যামেরা পজিশন, জুম, রোটেট ও টিল্ট" else "Camera Transform",
                    style = MaterialTheme.typography.labelMedium,
                    color = AmberDocumentary,
                    fontWeight = FontWeight.Bold
                )
                MapCompactSlider("জুম (Zoom)", liveZoom, 0.8f..12f, "%.1fx".format(liveZoom)) {
                    onUpdateLiveView(liveLat, liveLon, it, liveRot, liveTilt)
                }
                MapCompactSlider("রোটেট (Rotate)", liveRot, -180f..180f, "${liveRot.toInt()}°") {
                    onUpdateLiveView(liveLat, liveLon, liveZoom, it, liveTilt)
                }
                MapCompactSlider("টিল্ট (3D Tilt)", liveTilt, 0f..60f, "${liveTilt.toInt()}°") {
                    onUpdateLiveView(liveLat, liveLon, liveZoom, liveRot, it)
                }
                MapCompactSlider("ডিউরেশন (Duration)", draft.durationMs / 1000f, 2f..30f, "${(draft.durationMs / 1000f).toInt()}s") {
                    onUpdateDraft(draft.copy(durationMs = (it * 1000L).toLong()))
                }
                MapCompactSlider("স্পিড (Speed)", draft.speedMultiplier, 0.25f..2.5f, "%.2fx".format(draft.speedMultiplier)) {
                    onUpdateDraft(draft.copy(speedMultiplier = it))
                }
            }
        }

        // Keyframes list
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isBangla) "ক্যামেরা কী-ফ্রেম পাথ (${draft.keyframes.size})" else "Camera Keyframes (${draft.keyframes.size})",
                style = MaterialTheme.typography.labelMedium,
                color = CyanPlayhead,
                fontWeight = FontWeight.Bold
            )
            Button(
                onClick = {
                    val newKf = MapCameraKeyframe(
                        label = "KF@${(scrubFrac * 100).toInt()}%",
                        timeFraction = scrubFrac.coerceIn(0f, 1f),
                        centerLat = liveLat,
                        centerLon = liveLon,
                        zoom = liveZoom,
                        rotationDeg = liveRot,
                        tiltDeg = liveTilt,
                        easing = MapCameraEasing.CINEMA_FLIGHT
                    )
                    val merged = (draft.keyframes.filterNot { kotlin.math.abs(it.timeFraction - scrubFrac) < 0.04f } + newKf)
                        .sortedBy { it.timeFraction }
                    onUpdateDraft(draft.copy(keyframes = merged))
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyanPlayhead, contentColor = StudioObsidian),
                shape = RoundedCornerShape(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text(if (isBangla) "কী-ফ্রেম যোগ" else "+ Keyframe", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        draft.keyframes.sortedBy { it.timeFraction }.forEachIndexed { idx, kf ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(StudioSurfaceElevated)
                    .border(1.dp, StudioBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "#${idx + 1} ${kf.label} (${(kf.timeFraction * 100).toInt()}%) · ${"%.1f".format(kf.zoom)}x",
                        fontSize = 11.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Lat ${"%.1f".format(kf.centerLat)}°, Lon ${"%.1f".format(kf.centerLon)}° · ${kf.easing.labelBn}",
                        fontSize = 9.sp,
                        color = TextSecondary
                    )
                }
                Surface(
                    onClick = {
                        val nextEasing = MapCameraEasing.entries[(kf.easing.ordinal + 1) % MapCameraEasing.entries.size]
                        val updated = draft.keyframes.map { if (it.id == kf.id) it.copy(easing = nextEasing) else it }
                        onUpdateDraft(draft.copy(keyframes = updated))
                    },
                    color = Color(0xFF132238),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(kf.easing.name.take(5), fontSize = 9.sp, color = CyanPlayhead, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                }
                if (draft.keyframes.size > 1) {
                    IconButton(
                        onClick = { onUpdateDraft(draft.copy(keyframes = draft.keyframes.filterNot { it.id == kf.id })) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.DeleteOutline, "Remove Keyframe", tint = Color(0xFFFF4D6D), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Display HUD Toggles
        Surface(
            color = StudioSurface,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, StudioBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                MapCompactSwitch(if (isBangla) "অক্ষাংশ/দ্রাঘিমাংশ গ্রিড" else "Lat/Lon Grid", draft.showGraticuleGrid) {
                    onUpdateDraft(draft.copy(showGraticuleGrid = it))
                }
                MapCompactSwitch(if (isBangla) "দেশের সীমানা ও নাম" else "Country Borders & Names", draft.showCountryBorders) {
                    onUpdateDraft(draft.copy(showCountryBorders = it, showCountryNames = it))
                }
                MapCompactSwitch(if (isBangla) "কোর্ডিনেট, কম্পাস ও স্কেলবার" else "Coordinates & Compass HUD", draft.showCoordinatesHud) {
                    onUpdateDraft(draft.copy(showCoordinatesHud = it, showCompassRose = it, showScaleBar = it))
                }
            }
        }
    }
}

@Composable
internal fun MapRegionsTab(
    draft: UniversalMapConfig,
    isBangla: Boolean,
    onUpdateDraft: (UniversalMapConfig) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = if (isBangla) "দেশ, সীমান্ত অঙ্কন ও ঐতিহাসিক সাম্রাজ্য" else "Countries & Historical Empires",
            style = MaterialTheme.typography.labelLarge,
            color = AmberDocumentary,
            fontWeight = FontWeight.Bold
        )
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = CyanPlayhead, modifier = Modifier.size(16.dp)) },
            placeholder = { Text(if (isBangla) "দেশ বা সাম্রাজ্য খুঁজুন (উদা: বাংলাদেশ, মোঘল)..." else "Search country or empire...", fontSize = 12.sp) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyanPlayhead, unfocusedBorderColor = StudioBorder, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
            modifier = Modifier.fillMaxWidth()
        )

        val candidates = remember(searchQuery) {
            WorldCartographyData.selectableRegions.filter {
                searchQuery.isBlank() || it.nameBn.contains(searchQuery, true) || it.nameEn.contains(searchQuery, true)
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(candidates, key = { it.id }) { reg ->
                val already = draft.regions.any { it.regionId == reg.id }
                FilterChip(
                    selected = already,
                    onClick = {
                        if (!already) {
                            val newReg = MapRegionLayer(
                                regionId = reg.id,
                                customLabel = reg.nameBn,
                                fillColorHex = if (reg.isHistorical) 0xFFD97706L else 0xFF10B981L,
                                borderColorHex = 0xFFFFB300L,
                                borderDrawAnimated = true,
                                territoryExpansion = reg.isHistorical
                            )
                            onUpdateDraft(draft.copy(regions = draft.regions + newReg))
                        }
                    },
                    label = { Text("+ ${if (isBangla) reg.nameBn else reg.nameEn}", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = EmeraldAudio,
                        selectedLabelColor = StudioObsidian,
                        labelColor = TextPrimary
                    )
                )
            }
        }

        draft.regions.forEach { reg ->
            val def = WorldCartographyData.selectableRegions.firstOrNull { it.id == reg.regionId }
            Surface(
                color = StudioSurface,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(reg.borderColorHex).copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = def?.nameBn ?: reg.regionId,
                            color = AmberDocumentary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        IconButton(
                            onClick = { onUpdateDraft(draft.copy(regions = draft.regions.filterNot { it.id == reg.id })) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.DeleteOutline, "Delete Region", tint = Color(0xFFFF4D6D), modifier = Modifier.size(16.dp))
                        }
                    }
                    OutlinedTextField(
                        value = reg.customLabel,
                        onValueChange = { lbl ->
                            onUpdateDraft(draft.copy(regions = draft.regions.map { if (it.id == reg.id) it.copy(customLabel = lbl) else it }))
                        },
                        label = { Text(if (isBangla) "কাস্টম নাম" else "Label", fontSize = 10.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyanPlayhead, unfocusedBorderColor = StudioBorder, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
                        modifier = Modifier.fillMaxWidth()
                    )
                    MapColorSwatchRow(selectedHex = reg.fillColorHex) { hex ->
                        onUpdateDraft(draft.copy(regions = draft.regions.map { if (it.id == reg.id) it.copy(fillColorHex = hex, borderColorHex = hex) else it }))
                    }
                    MapCompactSlider(if (isBangla) "রঙের স্বচ্ছতা" else "Opacity", reg.fillOpacity, 0.1f..0.85f, "${(reg.fillOpacity * 100).toInt()}%") { op ->
                        onUpdateDraft(draft.copy(regions = draft.regions.map { if (it.id == reg.id) it.copy(fillOpacity = op) else it }))
                    }
                    MapCompactSwitch(if (isBangla) "সীমান্ত অঙ্কন অ্যানিমেশন (Border Draw)" else "Animated Border Draw", reg.borderDrawAnimated) { b ->
                        onUpdateDraft(draft.copy(regions = draft.regions.map { if (it.id == reg.id) it.copy(borderDrawAnimated = b) else it }))
                    }
                    MapCompactSwitch(if (isBangla) "ভূখণ্ড সম্প্রসারণ (Territory Expansion)" else "Territory Expansion", reg.territoryExpansion) { ex ->
                        onUpdateDraft(draft.copy(regions = draft.regions.map { if (it.id == reg.id) it.copy(territoryExpansion = ex) else it }))
                    }
                }
            }
        }
    }
}

@Composable
internal fun MapRoutesTab(
    draft: UniversalMapConfig,
    selectedRouteId: String?,
    isBangla: Boolean,
    onSelectRouteForTap: (String) -> Unit,
    onUpdateDraft: (UniversalMapConfig) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isBangla) "অ্যানিমেটেড রুট, যুদ্ধ ও বাণিজ্য পথ" else "Animated Routes & Frontlines",
                style = MaterialTheme.typography.labelLarge,
                color = CyanPlayhead,
                fontWeight = FontWeight.Bold
            )
            Button(
                onClick = {
                    val newRt = MapRouteLayer(
                        name = if (isBangla) "রুট ${draft.routes.size + 1}" else "Route ${draft.routes.size + 1}",
                        waypoints = listOf(MapLatLon(23.81f, 90.41f), MapLatLon(22.35f, 91.78f)),
                        routeStyle = MapRouteStyle.ARMY_ADVANCE,
                        tipIcon = MapRouteTipIcon.ARROW,
                        colorHex = 0xFFFFB300L
                    )
                    onUpdateDraft(draft.copy(routes = draft.routes + newRt))
                    onSelectRouteForTap(newRt.id)
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyanPlayhead, contentColor = StudioObsidian),
                shape = RoundedCornerShape(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text(if (isBangla) "নতুন রুট" else "+ Route", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        draft.routes.forEach { rt ->
            val isTarget = selectedRouteId == rt.id
            Surface(
                color = StudioSurface,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(if (isTarget) 1.5.dp else 1.dp, Color(rt.colorHex).copy(alpha = if (isTarget) 0.9f else 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Route, null, tint = Color(rt.colorHex), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("${rt.name} (${rt.waypoints.size} পয়েন্ট)", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = { onSelectRouteForTap(rt.id) },
                            border = BorderStroke(1.dp, if (isTarget) AmberDocumentary else StudioBorder),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Text(if (isTarget) "ম্যাপে ট্যাপ চালু" else "ম্যাপে পয়েন্ট যোগ", fontSize = 9.sp, color = if (isTarget) AmberDocumentary else CyanPlayhead)
                        }
                        IconButton(
                            onClick = { onUpdateDraft(draft.copy(routes = draft.routes.filterNot { it.id == rt.id })) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.DeleteOutline, "Delete Route", tint = Color(0xFFFF4D6D), modifier = Modifier.size(16.dp))
                        }
                    }

                    OutlinedTextField(
                        value = rt.name,
                        onValueChange = { n ->
                            onUpdateDraft(draft.copy(routes = draft.routes.map { if (it.id == rt.id) it.copy(name = n) else it }))
                        },
                        label = { Text(if (isBangla) "রুটের নাম" else "Route Name", fontSize = 10.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyanPlayhead, unfocusedBorderColor = StudioBorder, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
                        modifier = Modifier.fillMaxWidth()
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(MapRouteStyle.entries, key = { it.name }) { st ->
                            FilterChip(
                                selected = rt.routeStyle == st,
                                onClick = {
                                    onUpdateDraft(draft.copy(routes = draft.routes.map { if (it.id == rt.id) it.copy(routeStyle = st) else it }))
                                },
                                label = { Text(if (isBangla) st.labelBn else st.labelEn, fontSize = 10.sp) }
                            )
                        }
                    }

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(MapRouteTipIcon.entries, key = { it.name }) { ic ->
                            FilterChip(
                                selected = rt.tipIcon == ic,
                                onClick = {
                                    onUpdateDraft(draft.copy(routes = draft.routes.map { if (it.id == rt.id) it.copy(tipIcon = ic) else it }))
                                },
                                label = { Text(if (isBangla) ic.labelBn else ic.labelEn, fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AmberDocumentary, selectedLabelColor = StudioObsidian, labelColor = TextSecondary)
                            )
                        }
                    }

                    MapColorSwatchRow(selectedHex = rt.colorHex) { hex ->
                        onUpdateDraft(draft.copy(routes = draft.routes.map { if (it.id == rt.id) it.copy(colorHex = hex) else it }))
                    }

                    MapCompactSlider(if (isBangla) "শুরু সময়" else "Start", rt.startFrac, 0f..0.85f, "${(rt.startFrac * 100).toInt()}%") { sf ->
                        onUpdateDraft(draft.copy(routes = draft.routes.map { if (it.id == rt.id) it.copy(startFrac = sf, endFrac = maxOf(sf + 0.1f, it.endFrac)) else it }))
                    }
                    MapCompactSlider(if (isBangla) "শেষ সময়" else "End", rt.endFrac, 0.15f..1f, "${(rt.endFrac * 100).toInt()}%") { ef ->
                        onUpdateDraft(draft.copy(routes = draft.routes.map { if (it.id == rt.id) it.copy(endFrac = maxOf(it.startFrac + 0.08f, ef)) else it }))
                    }
                    if (rt.waypoints.size > 2) {
                        OutlinedButton(
                            onClick = {
                                onUpdateDraft(draft.copy(routes = draft.routes.map { if (it.id == rt.id) it.copy(waypoints = it.waypoints.dropLast(1)) else it }))
                            },
                            border = BorderStroke(1.dp, StudioBorder),
                            modifier = Modifier.fillMaxWidth().height(30.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                        ) {
                            Text(if (isBangla) "শেষ ওয়েপয়েন্ট মুছুন" else "Remove Last Waypoint", fontSize = 10.sp, color = TextSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MapMarkersAndTextTab(
    draft: UniversalMapConfig,
    isBangla: Boolean,
    onFocusCamera: (Float, Float) -> Unit,
    onUpdateDraft: (UniversalMapConfig) -> Unit
) {
    var cityQuery by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(MapMarkerType.PIN) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = if (isBangla) "শহর খুঁজুন, মার্কার, দুর্যোগ ও টেক্সট লেয়ার" else "Cities, Markers, Hazards & Labels",
            style = MaterialTheme.typography.labelLarge,
            color = EmeraldAudio,
            fontWeight = FontWeight.Bold
        )

        // Marker Type selector (including Cyclone, Flood, Quake, Fire, Heatmap)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(MapMarkerType.entries, key = { it.name }) { mt ->
                FilterChip(
                    selected = selectedType == mt,
                    onClick = { selectedType = mt },
                    label = { Text(if (isBangla) mt.labelBn else mt.labelEn, fontSize = 10.sp) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = EmeraldAudio, selectedLabelColor = StudioObsidian, labelColor = TextPrimary)
                )
            }
        }

        // City quick-search bar
        OutlinedTextField(
            value = cityQuery,
            onValueChange = { cityQuery = it },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = CyanPlayhead, modifier = Modifier.size(16.dp)) },
            placeholder = { Text(if (isBangla) "শহর খুঁজুন (যেমন: ঢাকা, ইস্তাম্বুল, কায়রো, টোকিও)..." else "Search city to add marker...", fontSize = 12.sp) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyanPlayhead, unfocusedBorderColor = StudioBorder, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
            modifier = Modifier.fillMaxWidth()
        )
        val matchedCities = remember(cityQuery) {
            WorldCartographyData.searchableCities.filter {
                cityQuery.isBlank() || it.nameBn.contains(cityQuery, true) || it.nameEn.contains(cityQuery, true) || it.countryBn.contains(cityQuery, true)
            }.take(14)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(matchedCities, key = { it.id }) { city ->
                FilterChip(
                    selected = false,
                    onClick = {
                        val color = when (selectedType) {
                            MapMarkerType.CYCLONE -> 0xFFF97316L
                            MapMarkerType.FLOOD -> 0xFF38BDF8L
                            MapMarkerType.EARTHQUAKE -> 0xFFFACC15L
                            MapMarkerType.FIRE -> 0xFFFF3B30L
                            MapMarkerType.HEATMAP_NODE -> 0xFFFF3B30L
                            MapMarkerType.BATTLE_CLASH, MapMarkerType.ARMY_BASE -> 0xFFFF4D6DL
                            else -> 0xFFFFB300L
                        }
                        val mk = MapMarkerLayer(
                            label = if (isBangla) city.nameBn else city.nameEn,
                            subtitle = city.countryBn,
                            lat = city.lat,
                            lon = city.lon,
                            markerType = selectedType,
                            colorHex = color,
                            sizeScale = if (selectedType == MapMarkerType.HEATMAP_NODE || selectedType == MapMarkerType.CYCLONE) 1.35f else 1.0f
                        )
                        onUpdateDraft(draft.copy(markers = draft.markers + mk))
                        onFocusCamera(city.lat, city.lon)
                    },
                    leadingIcon = { Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(14.dp), tint = AmberDocumentary) },
                    label = { Text("+ ${if (isBangla) city.nameBn else city.nameEn}", fontSize = 11.sp) }
                )
            }
        }

        // Markers List
        draft.markers.forEach { mk ->
            Surface(
                color = StudioSurface,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(mk.colorHex).copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${mk.markerType.labelBn} · ${mk.label}", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        IconButton(
                            onClick = { onUpdateDraft(draft.copy(markers = draft.markers.filterNot { it.id == mk.id })) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.DeleteOutline, "Delete Marker", tint = Color(0xFFFF4D6D), modifier = Modifier.size(16.dp))
                        }
                    }
                    OutlinedTextField(
                        value = mk.label,
                        onValueChange = { lbl ->
                            onUpdateDraft(draft.copy(markers = draft.markers.map { if (it.id == mk.id) it.copy(label = lbl) else it }))
                        },
                        label = { Text(if (isBangla) "মার্কারের নাম" else "Label", fontSize = 10.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyanPlayhead, unfocusedBorderColor = StudioBorder, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
                        modifier = Modifier.fillMaxWidth()
                    )
                    MapColorSwatchRow(selectedHex = mk.colorHex) { hex ->
                        onUpdateDraft(draft.copy(markers = draft.markers.map { if (it.id == mk.id) it.copy(colorHex = hex) else it }))
                    }
                    MapCompactSlider(if (isBangla) "আকার (Size)" else "Size", mk.sizeScale, 0.6f..2.2f, "%.1fx".format(mk.sizeScale)) { sc ->
                        onUpdateDraft(draft.copy(markers = draft.markers.map { if (it.id == mk.id) it.copy(sizeScale = sc) else it }))
                    }
                    MapCompactSwitch(if (isBangla) "স্থানাঙ্ক (Lat/Lon) দেখাও" else "Show Coordinates", mk.showCoordinates) { sc ->
                        onUpdateDraft(draft.copy(markers = draft.markers.map { if (it.id == mk.id) it.copy(showCoordinates = sc) else it }))
                    }
                }
            }
        }

        // Text Callouts Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isBangla) "টেক্সট লেবেল ও কলআউট (${draft.textLabels.size})" else "Text Callouts (${draft.textLabels.size})",
                style = MaterialTheme.typography.labelMedium,
                color = AmberDocumentary,
                fontWeight = FontWeight.Bold
            )
            OutlinedButton(
                onClick = {
                    val tx = MapTextLayer(text = if (isBangla) "নতুন নোট" else "Annotation", lat = 24f, lon = 90f)
                    onUpdateDraft(draft.copy(textLabels = draft.textLabels + tx))
                },
                border = BorderStroke(1.dp, AmberDocumentary),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Icon(Icons.Default.TextFields, null, tint = AmberDocumentary, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (isBangla) "+ টেক্সট" else "+ Text", fontSize = 10.sp, color = AmberDocumentary)
            }
        }
        draft.textLabels.forEach { tx ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(StudioSurfaceElevated)
                    .border(1.dp, StudioBorder, RoundedCornerShape(8.dp))
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = tx.text,
                    onValueChange = { next ->
                        onUpdateDraft(draft.copy(textLabels = draft.textLabels.map { if (it.id == tx.id) it.copy(text = next) else it }))
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AmberDocumentary, unfocusedBorderColor = StudioBorder, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { onUpdateDraft(draft.copy(textLabels = draft.textLabels.filterNot { it.id == tx.id })) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.DeleteOutline, "Delete Text", tint = Color(0xFFFF4D6D), modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
internal fun MapLayersTab(
    draft: UniversalMapConfig,
    isBangla: Boolean,
    onUpdateDraft: (UniversalMapConfig) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = if (isBangla) "সব ম্যাপ লেয়ার ও সময় নিয়ন্ত্রণ" else "All Map Layers & Timing",
            style = MaterialTheme.typography.labelLarge,
            color = AmberDocumentary,
            fontWeight = FontWeight.Bold
        )
        if (draft.regions.isEmpty() && draft.routes.isEmpty() && draft.markers.isEmpty() && draft.textLabels.isEmpty()) {
            Text(
                text = if (isBangla) "এখনও কোনো লেয়ার যোগ করা হয়নি। প্রিসেট বা মার্কার/রুট ট্যাব থেকে লেয়ার যোগ করুন।" else "No layers yet. Add from Presets, Regions, Routes or Markers.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
        draft.regions.forEach { r ->
            LayerToggleRow(
                title = "অঞ্চল: ${r.customLabel.ifBlank { r.regionId }}",
                isVisible = r.isVisible,
                accent = Color(r.borderColorHex),
                onToggleVis = { onUpdateDraft(draft.copy(regions = draft.regions.map { if (it.id == r.id) it.copy(isVisible = !it.isVisible) else it })) },
                onDelete = { onUpdateDraft(draft.copy(regions = draft.regions.filterNot { it.id == r.id })) }
            )
        }
        draft.routes.forEach { rt ->
            LayerToggleRow(
                title = "রুট: ${rt.name} (${(rt.startFrac * 100).toInt()}-${(rt.endFrac * 100).toInt()}%)",
                isVisible = rt.isVisible,
                accent = Color(rt.colorHex),
                onToggleVis = { onUpdateDraft(draft.copy(routes = draft.routes.map { if (it.id == rt.id) it.copy(isVisible = !it.isVisible) else it })) },
                onDelete = { onUpdateDraft(draft.copy(routes = draft.routes.filterNot { it.id == rt.id })) }
            )
        }
        draft.markers.forEach { mk ->
            LayerToggleRow(
                title = "${mk.markerType.labelBn}: ${mk.label}",
                isVisible = mk.isVisible,
                accent = Color(mk.colorHex),
                onToggleVis = { onUpdateDraft(draft.copy(markers = draft.markers.map { if (it.id == mk.id) it.copy(isVisible = !it.isVisible) else it })) },
                onDelete = { onUpdateDraft(draft.copy(markers = draft.markers.filterNot { it.id == mk.id })) }
            )
        }
        draft.textLabels.forEach { tx ->
            LayerToggleRow(
                title = "টেক্সট: ${tx.text}",
                isVisible = tx.isVisible,
                accent = Color(tx.colorHex),
                onToggleVis = { onUpdateDraft(draft.copy(textLabels = draft.textLabels.map { if (it.id == tx.id) it.copy(isVisible = !it.isVisible) else it })) },
                onDelete = { onUpdateDraft(draft.copy(textLabels = draft.textLabels.filterNot { it.id == tx.id })) }
            )
        }
    }
}

@Composable
private fun LayerToggleRow(
    title: String,
    isVisible: Boolean,
    accent: Color,
    onToggleVis: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(StudioSurface)
            .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(accent))
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, color = if (isVisible) TextPrimary else TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        IconButton(onClick = onToggleVis, modifier = Modifier.size(28.dp)) {
            Icon(if (isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, "Visibility", tint = if (isVisible) CyanPlayhead else TextMuted, modifier = Modifier.size(16.dp))
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.DeleteOutline, "Delete Layer", tint = Color(0xFFFF4D6D), modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun MapColorSwatchRow(selectedHex: Long, onSelect: (Long) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(mapColorSwatches) { hex ->
            val sel = (hex and 0x00FFFFFFL) == (selectedHex and 0x00FFFFFFL)
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(hex))
                    .border(if (sel) 2.5.dp else 1.dp, if (sel) Color.White else StudioBorder, CircleShape)
                    .clickable { onSelect(hex) }
            )
        }
    }
}

@Composable
private fun MapCompactSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    onChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 11.sp, color = TextSecondary)
            Text(valueText, fontSize = 11.sp, color = AmberDocumentary, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = AmberDocumentary, activeTrackColor = AmberDocumentary, inactiveTrackColor = StudioObsidian),
            modifier = Modifier.height(26.dp)
        )
    }
}

@Composable
private fun MapCompactSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 11.sp, color = TextPrimary)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = CyanPlayhead)
        )
    }
}
