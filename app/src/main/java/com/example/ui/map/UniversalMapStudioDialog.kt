package com.example.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.engine.VideoExportEngine
import com.example.domain.map.MapCameraEasing
import com.example.domain.map.MapCameraKeyframe
import com.example.domain.map.MapLatLon
import com.example.domain.map.MapMarkerLayer
import com.example.domain.map.MapMarkerType
import com.example.domain.map.MapPresetCatalog
import com.example.domain.map.MapProjectionMode
import com.example.domain.map.MapRouteLayer
import com.example.domain.map.MapRouteStyle
import com.example.domain.map.MapRouteTipIcon
import com.example.domain.map.MapTextLayer
import com.example.domain.map.UniversalMapConfig
import com.example.domain.map.UniversalMapRenderer
import com.example.domain.map.WorldCartographyData
import com.example.domain.model.AspectRatioPreset
import com.example.domain.model.ExportFps
import com.example.domain.model.PreviewResolution
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class MapStudioTab(val labelBn: String, val labelEn: String) {
    PRESETS("প্রিসেট", "Presets"),
    CAMERA("ক্যামেরা/থিম", "Camera"),
    REGIONS("দেশ/সীমানা", "Regions"),
    ROUTES("রুট/যুদ্ধ", "Routes"),
    MARKERS("মার্কার/দুর্যোগ", "Markers"),
    LAYERS("লেয়ার", "Layers")
}

private enum class MapTouchMode(val labelBn: String, val labelEn: String) {
    CAMERA("প্যান/জুম", "Pan/Zoom"),
    DROP_MARKER("মার্কার দিন", "+Marker"),
    ADD_WAYPOINT("রুট পয়েন্ট", "+Route"),
    DROP_TEXT("টেক্সট দিন", "+Text")
}

@Composable
fun UniversalMapStudioDialog(
    initialConfig: UniversalMapConfig?,
    aspectRatio: AspectRatioPreset,
    resolution: PreviewResolution,
    isBangla: Boolean,
    onDismiss: () -> Unit,
    onAddOrUpdateTimelineClip: (UniversalMapConfig) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val startConfig = remember(initialConfig) {
        initialConfig ?: MapPresetCatalog.presets.first().config
    }

    var draft by remember { mutableStateOf(startConfig) }
    val undoStack = remember { mutableStateListOf<UniversalMapConfig>() }
    val redoStack = remember { mutableStateListOf<UniversalMapConfig>() }

    fun commitDraft(next: UniversalMapConfig, pushUndo: Boolean = true) {
        if (next == draft) return
        if (pushUndo) {
            undoStack.add(draft)
            if (undoStack.size > 35) undoStack.removeAt(0)
            redoStack.clear()
        }
        draft = next
    }

    fun performUndo() {
        if (undoStack.isNotEmpty()) {
            redoStack.add(draft)
            draft = undoStack.removeAt(undoStack.lastIndex)
        }
    }

    fun performRedo() {
        if (redoStack.isNotEmpty()) {
            undoStack.add(draft)
            draft = redoStack.removeAt(redoStack.lastIndex)
        }
    }

    var activeTab by remember { mutableStateOf(MapStudioTab.PRESETS) }
    var touchMode by remember { mutableStateOf(MapTouchMode.CAMERA) }
    var selectedRouteId by remember { mutableStateOf(draft.routes.firstOrNull()?.id) }

    var scrubFrac by remember { mutableFloatStateOf(0.5f) }
    var isPlaying by remember { mutableStateOf(true) }

    // Manual camera preview overrides when user pans/zooms or adjusts sliders
    var manualCameraActive by remember { mutableStateOf(false) }
    val evaluatedCam = remember(draft, scrubFrac) {
        WorldCartographyData.evaluateCameraAt(draft, scrubFrac)
    }
    var liveLat by remember { mutableFloatStateOf(evaluatedCam.centerLat) }
    var liveLon by remember { mutableFloatStateOf(evaluatedCam.centerLon) }
    var liveZoom by remember { mutableFloatStateOf(evaluatedCam.zoom) }
    var liveRot by remember { mutableFloatStateOf(evaluatedCam.rotationDeg) }
    var liveTilt by remember { mutableFloatStateOf(evaluatedCam.tiltDeg) }

    LaunchedEffect(evaluatedCam, isPlaying) {
        if (!manualCameraActive || isPlaying) {
            liveLat = evaluatedCam.centerLat
            liveLon = evaluatedCam.centerLon
            liveZoom = evaluatedCam.zoom
            liveRot = evaluatedCam.rotationDeg
            liveTilt = evaluatedCam.tiltDeg
        }
    }

    // Smooth real-time studio animation loop
    LaunchedEffect(isPlaying, draft.durationMs, draft.speedMultiplier) {
        while (isPlaying) {
            delay(33L)
            val step = (33f * draft.speedMultiplier.coerceIn(0.25f, 3.0f)) / draft.durationMs.coerceAtLeast(1000L).toFloat()
            val next = scrubFrac + step
            scrubFrac = if (next >= 1f) 0f else next
        }
    }

    // Standalone MP4 Export State inside Studio
    var isExporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableFloatStateOf(0f) }
    var exportStatus by remember { mutableStateOf<String?>(null) }

    val latestDraft by rememberUpdatedState(draft)
    val latestTouchMode by rememberUpdatedState(touchMode)
    val latestRouteId by rememberUpdatedState(selectedRouteId)
    val latestLiveLat by rememberUpdatedState(liveLat)
    val latestLiveLon by rememberUpdatedState(liveLon)
    val latestLiveZoom by rememberUpdatedState(liveZoom)
    val latestLiveRot by rememberUpdatedState(liveRot)
    val latestLiveTilt by rememberUpdatedState(liveTilt)

    // Render config reflecting manual drag/zoom if user hasn't hit Play yet
    val renderConfig = remember(draft, manualCameraActive, liveLat, liveLon, liveZoom, liveRot, liveTilt) {
        if (!manualCameraActive) draft
        else draft.copy(
            keyframes = listOf(
                MapCameraKeyframe(
                    id = "live_override",
                    label = "Live",
                    timeFraction = 0f,
                    centerLat = liveLat,
                    centerLon = liveLon,
                    zoom = liveZoom,
                    rotationDeg = liveRot,
                    tiltDeg = liveTilt
                )
            )
        )
    }

    Dialog(
        onDismissRequest = { if (!isExporting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            color = StudioObsidian,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.2.dp, CyanPlayhead.copy(alpha = 0.65f))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 1. TOP STUDIO HEADER (Title, Undo, Redo, 2D/3D Switch, Close)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(StudioSurfaceElevated)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Public, null, tint = CyanPlayhead, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isBangla) "ইউনিভার্সাল ম্যাপ স্টুডিও" else "Map Animation Studio",
                            style = MaterialTheme.typography.titleSmall,
                            color = AmberDocumentary,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        IconButton(
                            onClick = { performUndo() },
                            enabled = undoStack.isNotEmpty(),
                            modifier = Modifier.size(32.dp).testTag("map_studio_undo")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Undo, "Undo", tint = if (undoStack.isNotEmpty()) TextPrimary else TextMuted, modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = { performRedo() },
                            enabled = redoStack.isNotEmpty(),
                            modifier = Modifier.size(32.dp).testTag("map_studio_redo")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Redo, "Redo", tint = if (redoStack.isNotEmpty()) TextPrimary else TextMuted, modifier = Modifier.size(18.dp))
                        }
                        Surface(
                            onClick = {
                                val nextMode = if (draft.projectionMode == MapProjectionMode.WORLD_2D) MapProjectionMode.GLOBE_3D else MapProjectionMode.WORLD_2D
                                commitDraft(draft.copy(projectionMode = nextMode))
                            },
                            color = Color(0xFF10283A),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, CyanPlayhead.copy(alpha = 0.7f)),
                            modifier = Modifier.testTag("map_studio_3d_toggle")
                        ) {
                            Text(
                                text = if (draft.projectionMode == MapProjectionMode.GLOBE_3D) "3D গ্লোব" else "2D ম্যাপ",
                                fontSize = 10.sp,
                                color = CyanPlayhead,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        IconButton(
                            onClick = { if (!isExporting) onDismiss() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Close, "Close", tint = TextPrimary, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // 2. INTERACTIVE LIVE 2D / 3D MAP VIEWPORT
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(225.dp)
                        .background(Color(0xFF04070D))
                        .testTag("universal_map_viewport")
                ) {
                    val renderMs = (scrubFrac * renderConfig.durationMs.coerceAtLeast(1000L)).toLong()
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, rotation ->
                                    if (latestTouchMode == MapTouchMode.CAMERA) {
                                        isPlaying = false
                                        manualCameraActive = true
                                        val nextZoom = (latestLiveZoom * zoom).coerceIn(0.8f, 12f)
                                        val degPerPx = 155f / (minOf(size.width, size.height).coerceAtLeast(1) * nextZoom)
                                        liveLat = (latestLiveLat + pan.y * degPerPx).coerceIn(-82f, 82f)
                                        liveLon = WorldCartographyData.normalizeLon(latestLiveLon - pan.x * degPerPx)
                                        liveZoom = nextZoom
                                        liveRot = (latestLiveRot + rotation).coerceIn(-180f, 180f)
                                    }
                                }
                            }
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    val mode = latestTouchMode
                                    if (mode == MapTouchMode.CAMERA) return@detectTapGestures
                                    val activeCam = com.example.domain.map.EvaluatedMapCamera(
                                        latestLiveLat, latestLiveLon, latestLiveZoom, latestLiveRot, latestLiveTilt
                                    )
                                    val geo = WorldCartographyData.unprojectScreenPoint(
                                        offset.x, offset.y, activeCam,
                                        latestDraft.projectionMode,
                                        size.width.toFloat(), size.height.toFloat()
                                    ) ?: return@detectTapGestures

                                    when (mode) {
                                        MapTouchMode.DROP_MARKER -> {
                                            val newMk = MapMarkerLayer(
                                                label = "${"%.1f".format(geo.lat)}°, ${"%.1f".format(geo.lon)}°",
                                                lat = geo.lat,
                                                lon = geo.lon,
                                                markerType = MapMarkerType.PIN,
                                                colorHex = 0xFFFFB300L,
                                                showCoordinates = true
                                            )
                                            commitDraft(latestDraft.copy(markers = latestDraft.markers + newMk))
                                        }
                                        MapTouchMode.ADD_WAYPOINT -> {
                                            val targetId = latestRouteId ?: latestDraft.routes.firstOrNull()?.id
                                            if (targetId != null) {
                                                val updatedRoutes = latestDraft.routes.map { rt ->
                                                    if (rt.id == targetId) rt.copy(waypoints = rt.waypoints + geo) else rt
                                                }
                                                commitDraft(latestDraft.copy(routes = updatedRoutes))
                                            } else {
                                                val newRt = MapRouteLayer(
                                                    name = "কাস্টম রুট ১",
                                                    waypoints = listOf(MapLatLon(latestLiveLat, latestLiveLon), geo),
                                                    routeStyle = MapRouteStyle.ARMY_ADVANCE,
                                                    tipIcon = MapRouteTipIcon.ARROW,
                                                    colorHex = 0xFF00E5FFL
                                                )
                                                selectedRouteId = newRt.id
                                                commitDraft(latestDraft.copy(routes = latestDraft.routes + newRt))
                                            }
                                        }
                                        MapTouchMode.DROP_TEXT -> {
                                            val newTx = MapTextLayer(
                                                text = if (isBangla) "নতুন স্থান" else "Location",
                                                lat = geo.lat,
                                                lon = geo.lon
                                            )
                                            commitDraft(latestDraft.copy(textLabels = latestDraft.textLabels + newTx))
                                        }
                                        MapTouchMode.CAMERA -> Unit
                                    }
                                }
                            }
                    ) {
                        drawIntoCanvas { c ->
                            UniversalMapRenderer.renderFrame(
                                canvas = c.nativeCanvas,
                                width = size.width,
                                height = size.height,
                                config = renderConfig,
                                playheadMs = renderMs,
                                isClipFrame = false
                            )
                        }
                    }

                    // Keyframe Capture floating pill when camera was manually panned/zoomed
                    if (manualCameraActive) {
                        Surface(
                            onClick = {
                                val newKf = MapCameraKeyframe(
                                    label = "Cam@${(scrubFrac * 100).toInt()}%",
                                    timeFraction = scrubFrac,
                                    centerLat = liveLat,
                                    centerLon = liveLon,
                                    zoom = liveZoom,
                                    rotationDeg = liveRot,
                                    tiltDeg = liveTilt,
                                    easing = MapCameraEasing.CINEMA_FLIGHT
                                )
                                val merged = (draft.keyframes.filterNot { kotlin.math.abs(it.timeFraction - scrubFrac) < 0.04f } + newKf)
                                    .sortedBy { it.timeFraction }
                                commitDraft(draft.copy(keyframes = merged))
                                manualCameraActive = false
                            },
                            color = AmberDocumentary,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 8.dp)
                        ) {
                            Text(
                                text = if (isBangla) "এই ক্যামেরা ভিউ কী-ফ্রেমে সেভ করুন" else "Save Camera Keyframe",
                                color = StudioObsidian,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                // 3. TOUCH MODE + PLAYBACK SCRUBBAR + KEYFRAME TIMELINE
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0B101A))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(MapTouchMode.entries, key = { it.name }) { mode ->
                            val ic = when (mode) {
                                MapTouchMode.CAMERA -> Icons.Default.PanTool
                                MapTouchMode.DROP_MARKER -> Icons.Default.AddLocationAlt
                                MapTouchMode.ADD_WAYPOINT -> Icons.Default.Route
                                MapTouchMode.DROP_TEXT -> Icons.Default.TextFields
                            }
                            FilterChip(
                                selected = touchMode == mode,
                                onClick = { touchMode = mode },
                                leadingIcon = { Icon(ic, null, modifier = Modifier.size(13.dp)) },
                                label = { Text(if (isBangla) mode.labelBn else mode.labelEn, fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AmberDocumentary,
                                    selectedLabelColor = StudioObsidian,
                                    labelColor = TextPrimary
                                ),
                                modifier = Modifier.height(28.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            onClick = {
                                manualCameraActive = false
                                isPlaying = !isPlaying
                            },
                            color = if (isPlaying) AmberDocumentary else CyanPlayhead,
                            shape = CircleShape,
                            modifier = Modifier.size(30.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play Preview",
                                    tint = StudioObsidian,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            Slider(
                                value = scrubFrac,
                                onValueChange = {
                                    isPlaying = false
                                    manualCameraActive = false
                                    scrubFrac = it
                                },
                                valueRange = 0f..1f,
                                colors = SliderDefaults.colors(thumbColor = CyanPlayhead, activeTrackColor = CyanPlayhead, inactiveTrackColor = StudioBorder),
                                modifier = Modifier.fillMaxWidth().height(26.dp)
                            )
                            // Keyframe Diamonds overlay along scrub track
                            Canvas(modifier = Modifier.fillMaxWidth().height(8.dp).align(Alignment.BottomCenter)) {
                                draft.keyframes.forEach { kf ->
                                    val x = size.width * kf.timeFraction.coerceIn(0f, 1f)
                                    drawCircle(AmberDocumentary, radius = 3.5f, center = Offset(x, size.height * 0.5f))
                                }
                            }
                        }

                        Text(
                            text = "${"%.1f".format(scrubFrac * draft.durationMs / 1000f)}s / ${(draft.durationMs / 1000f).toInt()}s",
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }
                }

                // 4. 6-TAB STUDIO SELECTOR
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(StudioSurface)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(MapStudioTab.entries, key = { it.name }) { tab ->
                        val active = activeTab == tab
                        FilterChip(
                            selected = active,
                            onClick = { activeTab = tab },
                            label = { Text(if (isBangla) tab.labelBn else tab.labelEn, fontSize = 11.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyanPlayhead,
                                selectedLabelColor = StudioObsidian,
                                labelColor = TextPrimary
                            ),
                            modifier = Modifier.height(30.dp)
                        )
                    }
                }

                // 5. SCROLLABLE INSPECTOR BODY
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp)
                ) {
                    when (activeTab) {
                        MapStudioTab.PRESETS -> MapPresetsTab(isBangla = isBangla) { presetCfg ->
                            manualCameraActive = false
                            scrubFrac = 0.45f
                            isPlaying = true
                            commitDraft(presetCfg)
                        }
                        MapStudioTab.CAMERA -> MapCameraTab(
                            draft = draft,
                            scrubFrac = scrubFrac,
                            liveLat = liveLat,
                            liveLon = liveLon,
                            liveZoom = liveZoom,
                            liveRot = liveRot,
                            liveTilt = liveTilt,
                            isBangla = isBangla,
                            onUpdateLiveView = { lat, lon, z, r, t ->
                                isPlaying = false
                                manualCameraActive = true
                                liveLat = lat; liveLon = lon; liveZoom = z; liveRot = r; liveTilt = t
                            },
                            onUpdateDraft = { commitDraft(it) }
                        )
                        MapStudioTab.REGIONS -> MapRegionsTab(draft = draft, isBangla = isBangla) {
                            commitDraft(it)
                        }
                        MapStudioTab.ROUTES -> MapRoutesTab(
                            draft = draft,
                            selectedRouteId = selectedRouteId,
                            isBangla = isBangla,
                            onSelectRouteForTap = { rtId ->
                                selectedRouteId = rtId
                                touchMode = MapTouchMode.ADD_WAYPOINT
                            },
                            onUpdateDraft = { commitDraft(it) }
                        )
                        MapStudioTab.MARKERS -> MapMarkersAndTextTab(
                            draft = draft,
                            isBangla = isBangla,
                            onFocusCamera = { lat, lon ->
                                isPlaying = false
                                manualCameraActive = true
                                liveLat = lat; liveLon = lon
                                if (liveZoom < 2.6f) liveZoom = 3.4f
                            },
                            onUpdateDraft = { commitDraft(it) }
                        )
                        MapStudioTab.LAYERS -> MapLayersTab(draft = draft, isBangla = isBangla) {
                            commitDraft(it)
                        }
                    }
                }

                // Export Progress / Result Status Strip
                if (isExporting || exportStatus != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0E1B2A))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        if (isExporting) {
                            LinearProgressIndicator(
                                progress = { exportProgress.coerceIn(0f, 1f) },
                                color = AmberDocumentary,
                                trackColor = StudioBorder,
                                modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(4.dp))
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                        }
                        exportStatus?.let { msg ->
                            Text(msg, fontSize = 11.sp, color = EmeraldAudio, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // 6. BOTTOM DUAL ACTION FOOTER: Standalone MP4 Export OR Add to Main Timeline
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(StudioSurfaceElevated)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        enabled = !isExporting,
                        onClick = {
                            isExporting = true
                            exportProgress = 0f
                            exportStatus = if (isBangla) "MP4 ম্যাপ ভিডিও তৈরি হচ্ছে..." else "Exporting MP4..."
                            scope.launch {
                                val res = VideoExportEngine.exportStandaloneMapToMp4(
                                    context = context,
                                    mapConfig = draft,
                                    aspectRatio = aspectRatio,
                                    resolution = resolution,
                                    fpsOption = ExportFps.FPS30,
                                    onProgress = { pct, label ->
                                        exportProgress = pct
                                        exportStatus = label
                                    }
                                )
                                isExporting = false
                                exportStatus = if (res.success) {
                                    if (isBangla) "সেভ সম্পন্ন: ${res.savedLocationLabel}" else "Saved: ${res.savedLocationLabel}"
                                } else {
                                    res.errorMessage ?: "Export failed"
                                }
                            }
                        },
                        border = BorderStroke(1.dp, AmberDocumentary),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f).testTag("export_map_mp4_btn")
                    ) {
                        Icon(Icons.Default.FileDownload, null, tint = AmberDocumentary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isBangla) "ভিডিও এক্সপোর্ট" else "Export MP4",
                            fontSize = 11.sp,
                            color = AmberDocumentary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }

                    Button(
                        enabled = !isExporting,
                        onClick = { onAddOrUpdateTimelineClip(draft) },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldAudio, contentColor = StudioObsidian),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1.3f).testTag("add_map_to_timeline_btn")
                    ) {
                        Icon(Icons.Default.MovieCreation, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (isBangla) "টাইমলাইনে যুক্ত করুন" else "Add to Timeline",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
