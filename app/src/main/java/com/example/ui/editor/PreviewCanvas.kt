package com.example.ui.editor

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.domain.engine.AudioSynthEngine
import com.example.domain.engine.ColorGradingEngine
import com.example.domain.engine.CustomFontManager
import com.example.domain.engine.KeyframeInterpolator
import com.example.domain.engine.SpeedRampEngine
import com.example.domain.engine.VideoExportEngine
import com.example.domain.model.BlendModeOption
import com.example.domain.model.ComparisonChartConfig
import com.example.domain.model.ComparisonGridConfig
import com.example.domain.model.GeometricShape
import com.example.domain.model.InvestigativeDocConfig
import com.example.domain.model.InvestigativeToolType
import com.example.domain.model.LowerThirdTemplate
import com.example.domain.model.LumetriColorGrade
import com.example.domain.model.MaskConfig
import com.example.domain.model.MaskShape
import com.example.domain.model.MediaSource
import com.example.domain.model.StudioFontOption
import com.example.domain.model.StudioProject
import com.example.domain.model.StudioTextAlign
import com.example.domain.model.TimelineClip
import com.example.domain.model.TrackType
import com.example.domain.model.VfxConfig
import com.example.domain.model.VfxEffectType
import com.example.ui.theme.AmberDocumentary
import com.example.ui.theme.CyanPlayhead
import com.example.ui.theme.EmeraldAudio
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun PreviewCanvas(
    project: StudioProject,
    playheadMs: Long,
    selectedClipId: String?,
    onSelectClip: (String?) -> Unit,
    onDirectGesture: (clipId: String, dxNorm: Float, dyNorm: Float, scale: Float, rot: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val trackLayerMap = project.trackLayers.associateBy { it.trackType }

    val activeAdjustment = project.clips
        .filter {
            it.trackType == TrackType.ADJUSTMENT &&
                !it.isHidden &&
                (trackLayerMap[it.trackType]?.isVisible != false) &&
                playheadMs in it.startMs..it.endMs
        }
        .fold(LumetriColorGrade()) { acc, c -> ColorGradingEngine.mergeAdjustmentGrades(acc, c.colorGrade) }
    val hasAdjustment = project.clips.any {
        it.trackType == TrackType.ADJUSTMENT &&
            !it.isHidden &&
            (trackLayerMap[it.trackType]?.isVisible != false) &&
            playheadMs in it.startMs..it.endMs
    }

    val visualClips = project.clips
        .filter { clip ->
            val layerVisible = trackLayerMap[clip.trackType]?.isVisible != false
            val videoToggleOk = clip.trackType != TrackType.VIDEO || project.videoTrackPreviewOn
            val isRenderableTrack = clip.trackType == TrackType.VIDEO ||
                clip.trackType == TrackType.GRAPHICS ||
                clip.trackType == TrackType.TEXT ||
                clip.trackType == TrackType.VFX
            isRenderableTrack && !clip.isHidden && layerVisible && videoToggleOk &&
                playheadMs in clip.startMs..clip.endMs
        }
        .sortedByDescending { trackLayerMap[it.trackType]?.order ?: it.trackType.defaultOrder }

    // Camera Shake VFX calculation
    val activeShake = visualClips.firstOrNull { it.vfxConfig?.effectType == VfxEffectType.CAMERA_SHAKE }?.vfxConfig
    val shakeDx = if (activeShake != null) sin(playheadMs * 0.045f * activeShake.speed) * 10f * activeShake.intensity else 0f
    val shakeDy = if (activeShake != null) cos(playheadMs * 0.062f * activeShake.speed) * 8f * activeShake.intensity else 0f
    val shakeRot = if (activeShake != null) sin(playheadMs * 0.031f * activeShake.speed) * 1.4f * activeShake.intensity else 0f

    val activeMusic = project.clips.firstOrNull { it.trackType == TrackType.MUSIC && !it.isHidden && playheadMs in it.startMs..it.endMs }
    val activeVo = project.clips.firstOrNull { it.trackType == TrackType.VOICEOVER && !it.isHidden && playheadMs in it.startMs..it.endMs }
    val musicGain = activeMusic?.let { AudioSynthEngine.computeEffectiveAudioGain(it, project, playheadMs) } ?: 0f
    val isDucking = activeMusic != null && activeVo != null && activeMusic.audioConfig.autoDuckingEnabled

    val activeVideo = visualClips.firstOrNull { it.trackType == TrackType.VIDEO }
    val liveSpeed = activeVideo?.let {
        val norm = if (it.durationMs > 0) (playheadMs - it.startMs).toFloat() / it.durationMs else 0f
        SpeedRampEngine.evaluateSpeedAt(it.speedRamp, norm)
    } ?: 1f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF07090F))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(project.aspectRatio.ratio, matchHeightConstraintsFirst = true)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF0E1016))
                .border(1.dp, Color(0xFF23293A), RoundedCornerShape(14.dp))
                .clipToBounds()
                .pointerInput(Unit) {
                    detectTapGestures { onSelectClip(null) }
                }
        ) {
            val canvasWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
            val canvasHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)

            if (visualClips.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (project.videoTrackPreviewOn) "ChitraFlow Pro Canvas" else "ভিডিও প্রিভিউ অফ",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextSecondary.copy(alpha = 0.65f)
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "মিডিয়া, চার্ট, ভিএফএক্স বা টেক্সট যোগ করুন",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary.copy(alpha = 0.45f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            visualClips.forEach { clip ->
                val xform = KeyframeInterpolator.evaluate(clip, playheadMs)
                val isSelected = clip.id == selectedClipId
                val effectiveGrade = if (clip.trackType == TrackType.VIDEO && hasAdjustment) {
                    ColorGradingEngine.mergeAdjustmentGrades(clip.colorGrade, activeAdjustment)
                } else if (hasAdjustment) {
                    activeAdjustment
                } else {
                    clip.colorGrade
                }

                val maskShape = buildMaskShape(clip.maskConfig)
                val blurMod = if (effectiveGrade.blurRadius > 0.5f && clip.trackType == TrackType.VIDEO) {
                    Modifier.blur((effectiveGrade.blurRadius * 0.6f).dp)
                } else Modifier

                val applyShake = clip.trackType == TrackType.VIDEO || clip.trackType == TrackType.GRAPHICS

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (maskShape != null) Modifier.clip(maskShape) else Modifier)
                        .offset {
                            IntOffset(
                                ((xform.posX * canvasWidthPx) + if (applyShake) shakeDx else 0f).roundToInt(),
                                ((xform.posY * canvasHeightPx) + if (applyShake) shakeDy else 0f).roundToInt()
                            )
                        }
                        .graphicsLayer {
                            scaleX = xform.scale
                            scaleY = xform.scale
                            rotationZ = xform.rotation + if (applyShake) shakeRot else 0f
                            alpha = xform.opacity
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                        .pointerInput(clip.id, canvasWidthPx, canvasHeightPx, project.editModeOn) {
                            detectTransformGestures { _, pan, zoom, rotation ->
                                onSelectClip(clip.id)
                                if (project.editModeOn) {
                                    onDirectGesture(
                                        clip.id,
                                        pan.x / canvasWidthPx,
                                        pan.y / canvasHeightPx,
                                        zoom,
                                        rotation
                                    )
                                }
                            }
                        }
                        .pointerInput(clip.id) {
                            detectTapGestures { onSelectClip(clip.id) }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // 1. Media photo / video frame
                    clip.mediaSource?.let { src ->
                        RenderMediaSource(
                            src = src,
                            playheadLocalMs = SpeedRampEngine.computeRemappedVideoTimeMs(
                                clip.speedRamp,
                                (playheadMs - clip.startMs).coerceAtLeast(0L),
                                clip.durationMs
                            ),
                            colorFilter = ColorFilter.colorMatrix(ColorGradingEngine.buildComposeColorMatrix(effectiveGrade)),
                            modifier = Modifier.fillMaxSize().then(blurMod)
                        )
                    }

                    // 2. Blend tint / Chroma Key visual cue
                    if (effectiveGrade.chromaKeyEnabled) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .border(1.dp, EmeraldAudio.copy(alpha = 0.3f))
                        )
                    }
                    if (clip.blendMode != BlendModeOption.NORMAL) {
                        val tintColor = when (clip.blendMode) {
                            BlendModeOption.SCREEN -> CyanPlayhead.copy(alpha = 0.18f)
                            BlendModeOption.OVERLAY -> AmberDocumentary.copy(alpha = 0.18f)
                            BlendModeOption.MULTIPLY -> Color(0xFF0A0C12).copy(alpha = 0.35f)
                            BlendModeOption.PLUS_ADD -> Color.White.copy(alpha = 0.22f)
                            BlendModeOption.NORMAL -> Color.Transparent
                        }
                        Box(modifier = Modifier.fillMaxSize().background(tintColor))
                    }

                    // 3. 2-60 Bar Customizable Comparison Chart
                    clip.chartConfig?.let { chart ->
                        val progress = if (clip.durationMs > 0L) {
                            ((playheadMs - clip.startMs).toFloat() / (clip.durationMs * 0.45f).coerceAtLeast(600f)).coerceIn(0f, 1f)
                        } else 1f
                        ComparisonChartOverlay(
                            config = chart,
                            animProgress = if (chart.animateGrowth) progress else 1f,
                            modifier = Modifier.fillMaxSize(0.88f)
                        )
                    }

                    // 4. 2-4 Slot Multi-Photo / Multi-Video Comparison Grid with Geometric Shapes
                    clip.comparisonGrid?.let { grid ->
                        ComparisonGridOverlay(
                            config = grid,
                            playheadLocalMs = (playheadMs - clip.startMs).coerceAtLeast(0L),
                            colorFilter = ColorFilter.colorMatrix(ColorGradingEngine.buildComposeColorMatrix(effectiveGrade)),
                            modifier = Modifier.fillMaxSize(0.88f)
                        )
                    }

                    // 5. Investigative Documentary Graphics (Highlighter, Newspaper Loupe, Redaction, Radar Map, Counter)
                    clip.investigativeConfig?.let { doc ->
                        val progress = if (clip.durationMs > 0L) {
                            ((playheadMs - clip.startMs).toFloat() / clip.durationMs.toFloat()).coerceIn(0f, 1f)
                        } else 1f
                        InvestigativeDocOverlay(
                            config = doc,
                            progress = progress,
                            modifier = Modifier.fillMaxSize(0.86f)
                        )
                    }

                    // 6. Hollywood VFX Overlay
                    clip.vfxConfig?.let { vfx ->
                        VfxOverlayCanvas(
                            vfx = vfx,
                            playheadMs = playheadMs,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // 7. Text / Documentary Lower Third Overlay
                    clip.textConfig?.let {
                        TextAndLowerThirdOverlay(
                            clip = clip,
                            playheadMs = playheadMs,
                            isSelected = isSelected
                        )
                    }

                    // Selection border for non-text overlays
                    if (isSelected && clip.textConfig == null && clip.trackType != TrackType.VIDEO && clip.trackType != TrackType.VFX) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize(0.92f)
                                .border(1.5.dp, AmberDocumentary, RoundedCornerShape(10.dp))
                        )
                    }
                }
            }

            // Adjustment Glow & Vignette pass
            val vig = if (hasAdjustment) activeAdjustment.vignette else 0.10f
            val glow = if (hasAdjustment) activeAdjustment.glow else 0f
            if (glow > 0.02f) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(
                        brush = Brush.radialGradient(
                            listOf(Color.White.copy(alpha = glow * 0.22f), Color.Transparent)
                        ),
                        blendMode = BlendMode.Screen
                    )
                }
            }
            if (vig > 0.02f) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = vig.coerceIn(0f, 0.85f))),
                            radius = size.maxDimension * 0.72f
                        )
                    )
                }
            }

            // Safe Zone Guide Overlay + Viewfinder Corner Brackets (┌ ┐ └ ┘) matching screenshot
            Canvas(modifier = Modifier.fillMaxSize()) {
                val pad = 8.dp.toPx()
                val arm = 16.dp.toPx()
                val stroke = 2.dp.toPx()
                val cornerColor = Color(0xFFD2D8E6)

                // Top-Left ┌
                drawLine(cornerColor, Offset(pad, pad), Offset(pad + arm, pad), stroke, cap = StrokeCap.Round)
                drawLine(cornerColor, Offset(pad, pad), Offset(pad, pad + arm), stroke, cap = StrokeCap.Round)
                // Top-Right ┐
                drawLine(cornerColor, Offset(size.width - pad, pad), Offset(size.width - pad - arm, pad), stroke, cap = StrokeCap.Round)
                drawLine(cornerColor, Offset(size.width - pad, pad), Offset(size.width - pad, pad + arm), stroke, cap = StrokeCap.Round)
                // Bottom-Left └
                drawLine(cornerColor, Offset(pad, size.height - pad), Offset(pad + arm, size.height - pad), stroke, cap = StrokeCap.Round)
                drawLine(cornerColor, Offset(pad, size.height - pad), Offset(pad, size.height - pad - arm), stroke, cap = StrokeCap.Round)
                // Bottom-Right ┘
                drawLine(cornerColor, Offset(size.width - pad, size.height - pad), Offset(size.width - pad - arm, size.height - pad), stroke, cap = StrokeCap.Round)
                drawLine(cornerColor, Offset(size.width - pad, size.height - pad), Offset(size.width - pad, size.height - pad - arm), stroke, cap = StrokeCap.Round)

                if (project.showSafeZoneGuides) {
                    val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                    // 90% Action Safe
                    drawRect(
                        color = Color.White.copy(alpha = 0.14f),
                        topLeft = Offset(size.width * 0.05f, size.height * 0.05f),
                        size = Size(size.width * 0.90f, size.height * 0.90f),
                        style = Stroke(width = 1f, pathEffect = dash)
                    )
                    // 80% Title Safe
                    drawRect(
                        color = AmberDocumentary.copy(alpha = 0.18f),
                        topLeft = Offset(size.width * 0.10f, size.height * 0.10f),
                        size = Size(size.width * 0.80f, size.height * 0.80f),
                        style = Stroke(width = 1f, pathEffect = dash)
                    )
                }
            }

            // Live Speed, Auto-Ducking, & Lock HUD badges
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (kotlin.math.abs(liveSpeed - 1f) > 0.08f) {
                    Surface(
                        color = Color(0xCC0A0C12),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyanPlayhead.copy(alpha = 0.7f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Speed, contentDescription = null, tint = CyanPlayhead, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("%.2fx".format(liveSpeed), style = MaterialTheme.typography.labelSmall, color = CyanPlayhead)
                        }
                    }
                }
                if (isDucking) {
                    Surface(
                        color = Color(0xCC0A0C12),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldAudio.copy(alpha = 0.7f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.GraphicEq, contentDescription = null, tint = EmeraldAudio, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("ডাক ${(musicGain * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = EmeraldAudio)
                        }
                    }
                }
                val selected = project.clips.firstOrNull { it.id == selectedClipId }
                if (selected?.isLocked == true) {
                    Surface(
                        color = Color(0xCC0A0C12),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AmberDocumentary.copy(alpha = 0.75f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = "Locked", tint = AmberDocumentary, modifier = Modifier.size(11.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("লকড", style = MaterialTheme.typography.labelSmall, color = AmberDocumentary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RenderMediaSource(
    src: MediaSource,
    playheadLocalMs: Long,
    colorFilter: ColorFilter,
    modifier: Modifier = Modifier
) {
    if (src.uriString != null) {
        if (src.isVideo) {
            VideoFramePreview(
                uriString = src.uriString,
                playheadLocalMs = playheadLocalMs,
                colorFilter = colorFilter,
                modifier = modifier
            )
        } else {
            AsyncImage(
                model = Uri.parse(src.uriString),
                contentDescription = src.label,
                contentScale = ContentScale.Crop,
                colorFilter = colorFilter,
                modifier = modifier
            )
        }
    } else if (src.drawableName != null) {
        Image(
            painter = painterResource(id = VideoExportEngine.resolveDrawableId(src.drawableName)),
            contentDescription = src.label,
            contentScale = ContentScale.Crop,
            colorFilter = colorFilter,
            modifier = modifier
        )
    } else {
        Box(modifier = modifier.background(StudioSurface))
    }
}

@Composable
private fun ComparisonChartOverlay(
    config: ComparisonChartConfig,
    animProgress: Float,
    modifier: Modifier = Modifier
) {
    val activeBars = rememberChartBars(config)
    val maxVal = (activeBars.maxOfOrNull { it.value } ?: 100f).coerceAtLeast(1f)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xD90A0D16))
            .border(1.dp, CyanPlayhead.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = config.title,
                style = MaterialTheme.typography.labelLarge,
                color = AmberDocumentary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${activeBars.size} বার",
                style = MaterialTheme.typography.labelSmall,
                color = CyanPlayhead
            )
        }
        Spacer(modifier = Modifier.height(6.dp))

        if (config.isHorizontal) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(if (activeBars.size > 24) 1.dp else 3.dp)
            ) {
                activeBars.forEach { bar ->
                    val frac = ((bar.value / maxVal) * animProgress).coerceIn(0.02f, 1f)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (activeBars.size <= 22) {
                            Text(
                                text = bar.label,
                                fontSize = if (activeBars.size > 14) 7.sp else 9.sp,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.width(58.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(0.76f)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFF1A2030))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(frac)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color(bar.colorHex))
                            )
                        }
                        if (activeBars.size <= 16) {
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "${bar.value.toInt()}${config.unitSuffix}",
                                fontSize = 8.sp,
                                color = Color(bar.colorHex),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        } else {
            // Vertical Columns fitting up to 60 bars cleanly inside Safe Zone
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(if (activeBars.size > 30) 1.dp else 3.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                activeBars.forEach { bar ->
                    val frac = ((bar.value / maxVal) * animProgress).coerceIn(0.04f, 1f)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(frac * 0.86f)
                                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                .background(Color(bar.colorHex))
                        )
                        if (activeBars.size <= 12) {
                            Text(
                                text = bar.label.take(4),
                                fontSize = 7.sp,
                                color = TextSecondary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun rememberChartBars(config: ComparisonChartConfig) =
    config.bars
        .take(config.barCount.coerceIn(2, 60))
        .let { list -> if (config.sortDescending) list.sortedByDescending { it.value } else list }

@Composable
private fun ComparisonGridOverlay(
    config: ComparisonGridConfig,
    playheadLocalMs: Long,
    colorFilter: ColorFilter,
    modifier: Modifier = Modifier
) {
    val activeSlots = config.slots.take(config.slotCount.coerceIn(2, 4))
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xC8090C14))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(12.dp))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = config.title,
            style = MaterialTheme.typography.labelMedium,
            color = AmberDocumentary,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(6.dp))
        val topRow = activeSlots.take(if (activeSlots.size <= 3) activeSlots.size else 2)
        val bottomRow = if (activeSlots.size == 4) activeSlots.drop(2) else emptyList()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(config.spacingDp.dp)
        ) {
            topRow.forEach { slot ->
                ComparisonSlotCard(
                    slot = slot,
                    playheadLocalMs = playheadLocalMs,
                    colorFilter = colorFilter,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
        }
        if (bottomRow.isNotEmpty()) {
            Spacer(modifier = Modifier.height(config.spacingDp.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(config.spacingDp.dp)
            ) {
                bottomRow.forEach { slot ->
                    ComparisonSlotCard(
                        slot = slot,
                        playheadLocalMs = playheadLocalMs,
                        colorFilter = colorFilter,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun ComparisonSlotCard(
    slot: com.example.domain.model.ComparisonSlot,
    playheadLocalMs: Long,
    colorFilter: ColorFilter,
    modifier: Modifier = Modifier
) {
    val shape = geometricShapeToCompose(slot.shape)
    val borderColor = Color(slot.borderColorHex)
    Box(
        modifier = modifier
            .clip(shape)
            .background(StudioSurface)
            .border(2.dp, borderColor, shape),
        contentAlignment = Alignment.BottomCenter
    ) {
        slot.mediaSource?.let { src ->
            RenderMediaSource(
                src = src,
                playheadLocalMs = playheadLocalMs,
                colorFilter = colorFilter,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xBB0A0D16))
                .padding(horizontal = 4.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = slot.caption,
                fontSize = 8.sp,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun geometricShapeToCompose(shape: GeometricShape): Shape = when (shape) {
    GeometricShape.ROUNDED_RECT -> RoundedCornerShape(12.dp)
    GeometricShape.CIRCLE -> CircleShape
    GeometricShape.HEXAGON -> GenericShape { size, _ ->
        val cx = size.width / 2f
        val cy = size.height / 2f
        val rx = size.width * 0.49f
        val ry = size.height * 0.49f
        for (i in 0 until 6) {
            val angle = (PI / 3.0) * i - PI / 2.0
            val px = cx + (rx * cos(angle)).toFloat()
            val py = cy + (ry * sin(angle)).toFloat()
            if (i == 0) moveTo(px, py) else lineTo(px, py)
        }
        close()
    }
    GeometricShape.DIAMOND -> GenericShape { size, _ ->
        moveTo(size.width / 2f, 0f)
        lineTo(size.width, size.height / 2f)
        lineTo(size.width / 2f, size.height)
        lineTo(0f, size.height / 2f)
        close()
    }
    GeometricShape.TRIANGLE -> GenericShape { size, _ ->
        moveTo(size.width / 2f, 0f)
        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        close()
    }
    GeometricShape.SHIELD -> GenericShape { size, _ ->
        moveTo(size.width * 0.1f, 0f)
        lineTo(size.width * 0.9f, 0f)
        lineTo(size.width * 0.9f, size.height * 0.56f)
        lineTo(size.width * 0.5f, size.height)
        lineTo(size.width * 0.1f, size.height * 0.56f)
        close()
    }
    GeometricShape.STAR -> GenericShape { size, _ ->
        val cx = size.width / 2f
        val cy = size.height / 2f
        val outer = minOf(cx, cy) * 0.98f
        val inner = outer * 0.46f
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) outer else inner
            val angle = -PI / 2.0 + (PI / 5.0) * i
            val px = cx + (r * cos(angle)).toFloat()
            val py = cy + (r * sin(angle)).toFloat()
            if (i == 0) moveTo(px, py) else lineTo(px, py)
        }
        close()
    }
    GeometricShape.DIAGONAL_SLASH -> GenericShape { size, _ ->
        moveTo(size.width * 0.14f, 0f)
        lineTo(size.width, 0f)
        lineTo(size.width * 0.86f, size.height)
        lineTo(0f, size.height)
        close()
    }
}

@Composable
private fun InvestigativeDocOverlay(
    config: InvestigativeDocConfig,
    progress: Float,
    modifier: Modifier = Modifier
) {
    val accent = Color(config.accentHex)
    when (config.toolType) {
        InvestigativeToolType.EVIDENCE_HIGHLIGHTER, InvestigativeToolType.NEWSPAPER_LOUPE -> {
            Box(
                modifier = modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFF3ECE0))
                    .border(2.dp, accent, RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                    Text(
                        text = config.headline,
                        color = Color(0xFF151820),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        // Animated Yellow Marker Sweep behind text
                        val sweep = (progress * 1.45f).coerceIn(0.05f, 1f)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(sweep)
                                .height(22.dp)
                                .background(accent.copy(alpha = 0.58f), RoundedCornerShape(4.dp))
                        )
                        Text(
                            text = config.bodyText,
                            color = Color(0xFF1A1D24),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 15.sp
                        )
                    }
                }
                if (config.toolType == InvestigativeToolType.NEWSPAPER_LOUPE) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val lx = size.width * (0.22f + 0.56f * progress)
                        val ly = size.height * 0.62f
                        val r = minOf(size.width, size.height) * 0.2f
                        drawCircle(color = CyanPlayhead.copy(alpha = 0.22f), radius = r, center = Offset(lx, ly))
                        drawCircle(color = Color(0xFF1E2638), radius = r, center = Offset(lx, ly), style = Stroke(width = 6f))
                        drawCircle(color = CyanPlayhead, radius = r, center = Offset(lx, ly), style = Stroke(width = 2f))
                    }
                }
            }
        }
        InvestigativeToolType.REDACTION_CENSOR -> {
            Column(
                modifier = modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xEE11151F))
                    .border(1.5.dp, Color(0xFFFF4D6D), RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = config.headline,
                    color = Color(0xFFFF4D6D),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(26.dp)
                        .background(Color.Black, RoundedCornerShape(4.dp))
                        .border(1.dp, Color(0xFFFF4D6D).copy(alpha = 0.6f), RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "████████ · REDACTED · ████████",
                        color = Color(0xFFFF4D6D),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        InvestigativeToolType.MAP_RADAR_ROUTE -> {
            Box(
                modifier = modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xDD08101C))
                    .border(1.5.dp, CyanPlayhead.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cols = 8
                    val rows = 5
                    for (c in 1 until cols) {
                        val x = size.width * c / cols
                        drawLine(CyanPlayhead.copy(alpha = 0.14f), Offset(x, 0f), Offset(x, size.height), 1f)
                    }
                    for (r in 1 until rows) {
                        val y = size.height * r / rows
                        drawLine(CyanPlayhead.copy(alpha = 0.14f), Offset(0f, y), Offset(size.width, y), 1f)
                    }
                    val pA = Offset(size.width * 0.22f, size.height * 0.68f)
                    val pB = Offset(size.width * 0.78f, size.height * 0.34f)
                    val cur = Offset(
                        pA.x + (pB.x - pA.x) * progress,
                        pA.y + (pB.y - pA.y) * progress
                    )
                    drawLine(
                        color = AmberDocumentary,
                        start = pA,
                        end = cur,
                        strokeWidth = 4f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                    )
                    val pulse = 10f + 16f * ((progress * 5f) % 1f)
                    drawCircle(CyanPlayhead.copy(alpha = 0.35f), radius = pulse, center = pA)
                    drawCircle(CyanPlayhead, radius = 7f, center = pA)
                    drawCircle(AmberDocumentary.copy(alpha = 0.35f), radius = pulse, center = pB)
                    drawCircle(AmberDocumentary, radius = 7f, center = pB)
                }
                Text(
                    text = "${config.headline}  |  ${config.pointALabel} ➔ ${config.pointBLabel}",
                    color = TextPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(Color(0xBB08101C), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }
        InvestigativeToolType.STAT_YEAR_COUNTER -> {
            val currentNum = (config.counterStart + (config.counterEnd - config.counterStart) * progress).roundToInt()
            Column(
                modifier = modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xD80A0D16))
                    .border(1.5.dp, accent, RoundedCornerShape(12.dp))
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = config.headline, color = CyanPlayhead, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${config.counterPrefix}$currentNum${config.counterSuffix}",
                    color = accent,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                if (config.bodyText.isNotBlank()) {
                    Text(text = config.bodyText, color = TextSecondary, fontSize = 10.sp, textAlign = TextAlign.Center)
                }
            }
        }
        InvestigativeToolType.NONE -> Unit
    }
}

@Composable
private fun VfxOverlayCanvas(
    vfx: VfxConfig,
    playheadMs: Long,
    modifier: Modifier = Modifier
) {
    val inten = vfx.intensity.coerceIn(0f, 1f)
    if (vfx.effectType == VfxEffectType.NONE || inten <= 0.01f) return
    val t = (playheadMs * 0.001f) * vfx.speed

    Canvas(modifier = modifier) {
        when (vfx.effectType) {
            VfxEffectType.FILM_GRAIN -> {
                val rand = Random((playheadMs / 40L).toInt())
                val count = (160 * inten).toInt().coerceIn(20, 200)
                repeat(count) {
                    val x = rand.nextFloat() * size.width
                    val y = rand.nextFloat() * size.height
                    val c = if (it % 2 == 0) Color.White.copy(alpha = 0.16f * inten) else Color.Black.copy(alpha = 0.22f * inten)
                    drawCircle(color = c, radius = 1.6f, center = Offset(x, y))
                }
            }
            VfxEffectType.ANAMORPHIC_FLARE -> {
                val fy = size.height * (0.28f + 0.1f * sin(t * 1.4f))
                drawRect(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, CyanPlayhead.copy(alpha = 0.45f * inten), Color.White.copy(alpha = 0.55f * inten), CyanPlayhead.copy(alpha = 0.45f * inten), Color.Transparent)
                    ),
                    topLeft = Offset(0f, fy - 12f),
                    size = Size(size.width, 24f),
                    blendMode = BlendMode.Screen
                )
            }
            VfxEffectType.CHROMATIC_ABERRATION -> {
                val shift = 14f * inten
                drawRect(Color(0x44FF2244), topLeft = Offset(-shift, 0f), size = size, style = Stroke(width = 6f))
                drawRect(Color(0x4400E5FF), topLeft = Offset(shift, 0f), size = size, style = Stroke(width = 6f))
            }
            VfxEffectType.GLITCH_TEAR -> {
                val rand = Random((playheadMs / 75L).toInt())
                repeat(6) {
                    val y = rand.nextFloat() * size.height
                    val h = 6f + rand.nextFloat() * 18f
                    val col = if (it % 2 == 0) CyanPlayhead.copy(alpha = 0.35f * inten) else Color(0xFFFF4D6D).copy(alpha = 0.35f * inten)
                    drawRect(col, topLeft = Offset(0f, y), size = Size(size.width, h))
                }
            }
            VfxEffectType.CINEMA_RAIN -> {
                val rand = Random((playheadMs / 45L).toInt())
                repeat((45 * inten).toInt().coerceAtLeast(10)) {
                    val x = rand.nextFloat() * size.width
                    val y = rand.nextFloat() * size.height
                    drawLine(
                        color = Color(0xAA9ED8FF).copy(alpha = 0.45f * inten),
                        start = Offset(x, y),
                        end = Offset(x - 10f, y + 32f),
                        strokeWidth = 1.6f
                    )
                }
            }
            VfxEffectType.EMBERS_SPARK -> {
                val rand = Random(42)
                repeat((28 * inten).toInt().coerceAtLeast(8)) { idx ->
                    val x = ((rand.nextFloat() + 0.08f * sin(t * 2f + idx)) % 1f) * size.width
                    val y = ((1f - ((rand.nextFloat() + t * 0.25f) % 1f))) * size.height
                    drawCircle(
                        color = Color(0xFFFF9800).copy(alpha = 0.75f * inten),
                        radius = 2.5f + (idx % 3),
                        center = Offset(x, y)
                    )
                }
            }
            VfxEffectType.LIGHT_LEAK -> {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFFF7A00).copy(alpha = 0.48f * inten), Color.Transparent),
                        center = Offset(size.width * (0.15f + 0.1f * sin(t)), size.height * 0.2f),
                        radius = size.maxDimension * 0.65f
                    ),
                    blendMode = BlendMode.Screen
                )
            }
            VfxEffectType.LETTERBOX_239 -> {
                val barH = size.height * 0.13f * inten
                drawRect(Color.Black, Offset.Zero, Size(size.width, barH))
                drawRect(Color.Black, Offset(0f, size.height - barH), Size(size.width, barH))
            }
            VfxEffectType.CAMERA_SHAKE, VfxEffectType.NONE -> Unit
        }
    }
}

@Composable
private fun VideoFramePreview(
    uriString: String,
    playheadLocalMs: Long,
    colorFilter: ColorFilter,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bucketMs = (playheadLocalMs / 250L) * 250L
    val bitmap by produceState<Bitmap?>(initialValue = null, uriString, bucketMs) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val mmr = MediaMetadataRetriever()
                mmr.setDataSource(context, Uri.parse(uriString))
                val frame = mmr.getFrameAtTime(bucketMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                mmr.release()
                frame
            }.getOrNull()
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = "Video frame",
            contentScale = ContentScale.Crop,
            colorFilter = colorFilter,
            modifier = modifier
        )
    } else {
        Box(modifier = modifier.background(StudioSurface))
    }
}

@Composable
private fun TextAndLowerThirdOverlay(
    clip: TimelineClip,
    playheadMs: Long,
    isSelected: Boolean
) {
    val cfg = clip.textConfig ?: return
    val fontFamily = CustomFontManager.resolveComposeFontFamily(cfg.fontId, cfg.customFontPath)
    val accentColor = Color(cfg.accentColorHex)
    val mainColor = Color(cfg.textColorHex)
    val textAlignment = when (cfg.textAlign) {
        StudioTextAlign.LEFT -> TextAlign.Start
        StudioTextAlign.CENTER -> TextAlign.Center
        StudioTextAlign.RIGHT -> TextAlign.End
    }
    val colHorizontal = when (cfg.textAlign) {
        StudioTextAlign.LEFT -> Alignment.Start
        StudioTextAlign.CENTER -> Alignment.CenterHorizontally
        StudioTextAlign.RIGHT -> Alignment.End
    }

    val progress = if (clip.durationMs > 0L) {
        ((playheadMs - clip.startMs).toFloat() / clip.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val gradientBrush = if (cfg.useGradient && !cfg.karaokeHighlight) {
        Brush.linearGradient(listOf(Color(cfg.gradientStartHex), Color(cfg.gradientEndHex)))
    } else null

    val annotatedText = if (cfg.karaokeHighlight) {
        val words = cfg.text.split(" ")
        val activeWordCount = ((words.size) * progress).toInt().coerceIn(0, words.size)
        buildAnnotatedString {
            words.forEachIndexed { idx, w ->
                withStyle(
                    SpanStyle(
                        color = if (idx <= activeWordCount) accentColor else mainColor.copy(alpha = 0.68f),
                        fontWeight = if (idx == activeWordCount) FontWeight.Bold else FontWeight.SemiBold
                    )
                ) {
                    append(w)
                }
                if (idx < words.lastIndex) append(" ")
            }
        }
    } else {
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(cfg.text)
            }
        }
    }

    val fontSize = (cfg.fontSizeSp * 0.72f).sp
    val lineHeight = (cfg.fontSizeSp * 0.72f * cfg.lineHeightMult.coerceIn(0.85f, 2.2f)).sp
    val letterSpacing = cfg.letterSpacingSp.sp
    val shadow = if (cfg.shadowRadius > 0.2f) {
        Shadow(
            color = Color(cfg.shadowColorHex),
            offset = Offset(0f, 3f),
            blurRadius = cfg.shadowRadius
        )
    } else null

    val fillStyle = if (gradientBrush != null) {
        TextStyle(
            brush = gradientBrush,
            fontFamily = fontFamily,
            fontSize = fontSize,
            lineHeight = lineHeight,
            letterSpacing = letterSpacing,
            shadow = shadow
        )
    } else {
        TextStyle(
            color = mainColor,
            fontFamily = fontFamily,
            fontSize = fontSize,
            lineHeight = lineHeight,
            letterSpacing = letterSpacing,
            shadow = shadow
        )
    }

    val hasCard = cfg.lowerThirdTemplate != LowerThirdTemplate.NONE || cfg.backgroundPill
    val cornerShape = RoundedCornerShape(cfg.pillCornerDp.coerceIn(0f, 28f).dp)
    val borderMod = if (isSelected) {
        Modifier.border(1.5.dp, AmberDocumentary, cornerShape)
    } else Modifier

    Box(
        modifier = Modifier
            .padding(horizontal = 14.dp)
            .then(borderMod)
            .then(
                if (hasCard) {
                    Modifier
                        .clip(cornerShape)
                        .background(Color(cfg.pillColorHex))
                        .border(1.dp, accentColor.copy(alpha = 0.45f), cornerShape)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                } else {
                    Modifier.padding(6.dp)
                }
            )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (cfg.lowerThirdTemplate == LowerThirdTemplate.DOCUMENTARY_DATE_CARD ||
                cfg.lowerThirdTemplate == LowerThirdTemplate.NEWS_LOWER_THIRD
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(34.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(accentColor)
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Column(horizontalAlignment = colHorizontal) {
                Box {
                    if (cfg.strokeWidthPx > 0.2f) {
                        Text(
                            text = cfg.text,
                            textAlign = textAlignment,
                            style = TextStyle(
                                color = Color(cfg.strokeColorHex),
                                fontFamily = fontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = fontSize,
                                lineHeight = lineHeight,
                                letterSpacing = letterSpacing,
                                drawStyle = Stroke(
                                    width = cfg.strokeWidthPx * 1.4f,
                                    join = StrokeJoin.Round
                                )
                            )
                        )
                    }
                    Text(
                        text = annotatedText,
                        textAlign = textAlignment,
                        style = fillStyle
                    )
                }
                if (cfg.subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = cfg.subtitle,
                        fontFamily = fontFamily,
                        fontSize = (cfg.fontSizeSp * 0.44f).sp,
                        color = accentColor,
                        fontWeight = FontWeight.Medium,
                        textAlign = textAlignment
                    )
                }
            }
        }
    }
}

private fun buildMaskShape(config: MaskConfig): Shape? {
    val s = config.scale.coerceIn(0.2f, 1f)
    return when (config.shape) {
        MaskShape.NONE -> null
        MaskShape.LINEAR_TOP -> GenericShape { size, _ ->
            addRect(Rect(0f, 0f, size.width, size.height * s))
        }
        MaskShape.LINEAR_SPLIT -> GenericShape { size, _ ->
            addRect(Rect(0f, 0f, size.width * s, size.height))
        }
        MaskShape.CINEMA_BARS -> GenericShape { size, _ ->
            val bar = size.height * (1f - s) * 0.35f
            addRect(Rect(0f, bar, size.width, size.height - bar))
        }
        MaskShape.RADIAL_SPOTLIGHT -> GenericShape { size, _ ->
            val r = minOf(size.width, size.height) * 0.48f * s
            addOval(Rect(size.width / 2f - r, size.height / 2f - r, size.width / 2f + r, size.height / 2f + r))
        }
        MaskShape.FREEHAND_DIAMOND -> GenericShape { size, _ ->
            val cx = size.width / 2f
            val cy = size.height / 2f
            val rx = size.width * 0.45f * s
            val ry = size.height * 0.45f * s
            moveTo(cx, cy - ry)
            lineTo(cx + rx, cy)
            lineTo(cx, cy + ry)
            lineTo(cx - rx, cy)
            close()
        }
    }
}
