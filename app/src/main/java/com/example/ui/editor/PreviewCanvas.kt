package com.example.ui.editor

import android.graphics.Bitmap
import android.graphics.Matrix as AndroidMatrix
import android.graphics.SurfaceTexture
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import android.os.Build
import android.view.Surface as AndroidSurface
import android.view.TextureView
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.domain.engine.AudioSynthEngine
import com.example.domain.engine.ColorGradingEngine
import com.example.domain.engine.CustomFontManager
import com.example.domain.engine.EvaluatedTransform
import com.example.domain.engine.KeyframeInterpolator
import com.example.domain.engine.SpeedRampEngine
import com.example.domain.engine.TransitionAndChromaEngine
import com.example.domain.engine.VideoExportEngine
import com.example.domain.model.BlendModeOption
import com.example.domain.model.BorderConfig
import com.example.domain.model.BorderShapeStyle
import com.example.domain.model.ChartPaletteTheme
import com.example.domain.model.ComparisonChartConfig
import com.example.domain.model.InvestigativeDocConfig
import com.example.domain.model.InvestigativeToolType
import com.example.domain.model.LowerThirdTemplate
import com.example.domain.model.LumetriColorGrade
import com.example.domain.model.MapRadarStyle
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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan
import kotlin.random.Random

@Composable
fun PreviewCanvas(
    project: StudioProject,
    playheadMs: Long,
    selectedClipId: String?,
    onSelectClip: (String?) -> Unit,
    onDirectGesture: (clipId: String, dxNorm: Float, dyNorm: Float, scale: Float, rot: Float) -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    bypassAnimationPreview: Boolean = false
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
        .sortedWith(
            compareByDescending<TimelineClip> { trackLayerMap[it.trackType]?.order ?: it.trackType.defaultOrder }
                .thenByDescending { it.laneIndex }
        )

    // Multi-octave organic handheld Camera Shake VFX calculation
    val activeShake = visualClips.firstOrNull { it.vfxConfig?.effectType == VfxEffectType.CAMERA_SHAKE }?.vfxConfig
    val shakeT = (playheadMs * 0.001f) * (activeShake?.speed ?: 1f)
    val shakeDx = if (activeShake != null) {
        (sin(shakeT * 11.3f) * 0.6f + sin(shakeT * 23.7f) * 0.4f) * 12f * activeShake.intensity
    } else 0f
    val shakeDy = if (activeShake != null) {
        (cos(shakeT * 9.7f) * 0.65f + cos(shakeT * 19.1f) * 0.35f) * 9f * activeShake.intensity
    } else 0f
    val shakeRot = if (activeShake != null) {
        (sin(shakeT * 7.9f) * 0.7f + cos(shakeT * 17.3f) * 0.3f) * 1.6f * activeShake.intensity
    } else 0f

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

            val densityVal = LocalDensity.current.density
            visualClips.forEach { clip ->
                val xform = KeyframeInterpolator.evaluate(clip, playheadMs, bypassAnimationPreview)
                val trans = TransitionAndChromaEngine.evaluateTransition(clip, playheadMs)
                val isSelected = clip.id == selectedClipId
                val effectiveGrade = if (clip.trackType == TrackType.VIDEO && hasAdjustment) {
                    ColorGradingEngine.mergeAdjustmentGrades(clip.colorGrade, activeAdjustment)
                } else if (hasAdjustment) {
                    activeAdjustment
                } else {
                    clip.colorGrade
                }

                val maskShape = buildMaskShape(clip.maskConfig)
                val borderClipShape = buildBorderClipShape(clip.borderConfig)
                val wipeShape = if (xform.clipWipe < 0.99f) {
                    GenericShape { sz, _ -> addRect(Rect(0f, 0f, sz.width * xform.clipWipe, sz.height)) }
                } else null
                val irisClipShape = if (trans.irisRadiusFrac < 0.99f) {
                    GenericShape { size, _ ->
                        val r = maxOf(size.width, size.height) * 0.72f * trans.irisRadiusFrac
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        addOval(Rect(cx - r, cy - r, cx + r, cy + r))
                    }
                } else null

                val totalBlur = maxOf(
                    if (clip.trackType == TrackType.VIDEO) effectiveGrade.blurRadius else 0f,
                    xform.blurRadius
                )
                val blurMod = if (totalBlur > 0.5f) {
                    Modifier.blur((totalBlur * 0.6f).dp)
                } else Modifier

                val applyShake = clip.trackType == TrackType.VIDEO || clip.trackType == TrackType.GRAPHICS
                val clipSpeedAtPlayhead = if (clip.durationMs > 0L) {
                    val norm = (playheadMs - clip.startMs).toFloat() / clip.durationMs.toFloat()
                    SpeedRampEngine.evaluateSpeedAt(clip.speedRamp, norm)
                } else 1f

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (maskShape != null) Modifier.clip(maskShape) else Modifier)
                        .then(if (wipeShape != null) Modifier.clip(wipeShape) else Modifier)
                        .then(if (irisClipShape != null) Modifier.clip(irisClipShape) else Modifier)
                        .offset {
                            IntOffset(
                                (((xform.posX + trans.offsetXNorm) * canvasWidthPx) + if (applyShake) shakeDx else 0f).roundToInt(),
                                (((xform.posY + trans.offsetYNorm) * canvasHeightPx) + if (applyShake) shakeDy else 0f).roundToInt()
                            )
                        }
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(xform.anchorX, xform.anchorY)
                            scaleX = xform.scale * trans.scaleMult
                            scaleY = xform.scale * trans.scaleMult
                            rotationX = xform.rotationX
                            rotationY = xform.rotationY
                            rotationZ = xform.rotation + trans.rotationDeg + if (applyShake) shakeRot else 0f
                            cameraDistance = 14f * densityVal
                            alpha = (xform.opacity * trans.alphaMult).coerceIn(0f, 1f)
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                        .then(
                            if (abs(xform.skewX) > 0.2f || abs(xform.skewY) > 0.2f) {
                                Modifier.drawWithContent {
                                    drawIntoCanvas { c ->
                                        c.nativeCanvas.save()
                                        c.nativeCanvas.translate(size.width * xform.anchorX, size.height * xform.anchorY)
                                        c.nativeCanvas.skew(
                                            tan(Math.toRadians(xform.skewX.toDouble())).toFloat(),
                                            tan(Math.toRadians(xform.skewY.toDouble())).toFloat()
                                        )
                                        c.nativeCanvas.translate(-size.width * xform.anchorX, -size.height * xform.anchorY)
                                    }
                                    drawContent()
                                    drawIntoCanvas { c -> c.nativeCanvas.restore() }
                                }
                            } else Modifier
                        )
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
                    // Inner container optionally clipped by enclosed geometric border
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (borderClipShape != null) Modifier.clip(borderClipShape) else Modifier)
                            .then(blurMod),
                        contentAlignment = Alignment.Center
                    ) {
                        // 1. Media photo / video (Hardware TextureView for smooth playback, MMR when chroma-key active)
                        clip.mediaSource?.let { src ->
                            RenderMediaSource(
                                src = src,
                                playheadLocalMs = SpeedRampEngine.computeRemappedVideoTimeMs(
                                    clip.speedRamp,
                                    (playheadMs - clip.startMs).coerceAtLeast(0L),
                                    clip.durationMs
                                ),
                                isPlaying = isPlaying,
                                playbackSpeed = clipSpeedAtPlayhead,
                                colorFilter = ColorFilter.colorMatrix(ColorGradingEngine.buildComposeColorMatrix(effectiveGrade)),
                                chromaKeyEnabled = effectiveGrade.chromaKeyEnabled,
                                chromaKeyStrength = effectiveGrade.chromaKeyStrength,
                                modifier = Modifier.fillMaxSize()
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

                        // 4. Investigative Documentary Graphics (Highlighter, Newspaper Loupe, Redaction, Radar Map, Counter)
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

                        // 5. Hollywood VFX Overlay
                        clip.vfxConfig?.let { vfx ->
                            VfxOverlayCanvas(
                                vfx = vfx,
                                playheadMs = playheadMs,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // 6. Text / Documentary Lower Third Overlay with all 16 Text Animations
                        clip.textConfig?.let {
                            TextAndLowerThirdOverlay(
                                clip = clip,
                                playheadMs = playheadMs,
                                xform = xform,
                                isSelected = isSelected
                            )
                        }
                    }

                    // 7. Geometric Border Frame Overlay
                    if (clip.borderConfig.style != BorderShapeStyle.NONE) {
                        BorderShapeOverlay(
                            border = clip.borderConfig,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Loop Glow / Glitch RGB burst overlays
                    if (xform.glowBoost > 0.05f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.radialGradient(
                                        listOf(AmberDocumentary.copy(alpha = (xform.glowBoost * 0.25f).coerceIn(0f, 0.4f)), Color.Transparent)
                                    )
                                )
                        )
                    }
                    if (xform.glitchIntensity > 0.08f) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val rand = Random((playheadMs / 40L).toInt() + clip.id.hashCode())
                            repeat((xform.glitchIntensity * 5).toInt().coerceIn(2, 8)) { idx ->
                                val gy = rand.nextFloat() * size.height
                                val gh = 5f + rand.nextFloat() * 14f
                                drawRect(
                                    color = if (idx % 2 == 0) CyanPlayhead.copy(alpha = 0.4f) else Color(0xFFFF4D6D).copy(alpha = 0.4f),
                                    topLeft = Offset(0f, gy),
                                    size = Size(size.width, gh)
                                )
                            }
                        }
                    }

                    // Selection border for non-text overlays
                    if (isSelected && clip.textConfig == null && clip.trackType != TrackType.VIDEO && clip.trackType != TrackType.VFX) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize(0.92f)
                                .border(1.5.dp, AmberDocumentary, RoundedCornerShape(10.dp))
                        )
                    }

                    // Transition / Animation Flash overlay
                    val totalFlash = maxOf(trans.flashAlpha, xform.flashAlpha)
                    if (totalFlash > 0.02f) {
                        val flashCol = if (trans.isWarmFlash) {
                            Color(0xFFFF821E).copy(alpha = (totalFlash * 0.72f).coerceIn(0f, 1f))
                        } else {
                            Color.White.copy(alpha = (totalFlash * 0.68f).coerceIn(0f, 1f))
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(flashCol)
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
    isPlaying: Boolean,
    playbackSpeed: Float,
    colorFilter: ColorFilter,
    chromaKeyEnabled: Boolean = false,
    chromaKeyStrength: Float = 0.45f,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    if (src.uriString != null) {
        if (src.isVideo && !chromaKeyEnabled) {
            VideoSurfacePreview(
                uriString = src.uriString,
                playheadLocalMs = playheadLocalMs,
                isPlaying = isPlaying,
                playbackSpeed = playbackSpeed,
                modifier = modifier
            )
        } else if (src.isVideo) {
            VideoFramePreview(
                uriString = src.uriString,
                playheadLocalMs = playheadLocalMs,
                colorFilter = colorFilter,
                chromaKeyEnabled = true,
                chromaKeyStrength = chromaKeyStrength,
                modifier = modifier
            )
        } else if (chromaKeyEnabled) {
            val keyedBmp by produceState<Bitmap?>(initialValue = null, src.uriString, chromaKeyStrength) {
                value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(Uri.parse(src.uriString))?.use { stream ->
                            val raw = android.graphics.BitmapFactory.decodeStream(stream) ?: return@use null
                            val maxDim = 720
                            val scale = minOf(1f, maxDim.toFloat() / maxOf(raw.width, raw.height).coerceAtLeast(1))
                            val resized = if (scale < 0.99f) {
                                Bitmap.createScaledBitmap(raw, (raw.width * scale).toInt().coerceAtLeast(1), (raw.height * scale).toInt().coerceAtLeast(1), true).also {
                                    if (it !== raw) raw.recycle()
                                }
                            } else raw
                            TransitionAndChromaEngine.applyGreenScreenChromaKey(resized, chromaKeyStrength).also {
                                if (resized !== it) resized.recycle()
                            }
                        }
                    }.getOrNull()
                }
            }
            if (keyedBmp != null) {
                Image(
                    bitmap = keyedBmp!!.asImageBitmap(),
                    contentDescription = src.label,
                    contentScale = ContentScale.Crop,
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
internal fun ComparisonChartOverlay(
    config: ComparisonChartConfig,
    animProgress: Float,
    modifier: Modifier = Modifier
) {
    val activeBars = rememberChartBars(config)
    val maxVal = (activeBars.maxOfOrNull { it.value } ?: 100f).coerceAtLeast(1f)
    val paletteColors = config.paletteTheme.colors
    val resolvedStyle = if (config.chartStyle == com.example.domain.model.ChartVisualStyle.HORIZONTAL_BAR && !config.isHorizontal) {
        com.example.domain.model.ChartVisualStyle.VERTICAL_BAR
    } else {
        config.chartStyle
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xEB0B101B), Color(0xE0070B14))
                )
            )
            .border(1.2.dp, CyanPlayhead.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .padding(11.dp)
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
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Surface(
                color = CyanPlayhead.copy(alpha = 0.14f),
                shape = RoundedCornerShape(6.dp),
                border = androidx.compose.foundation.BorderStroke(0.8.dp, CyanPlayhead.copy(alpha = 0.45f))
            ) {
                Text(
                    text = "${activeBars.size} · ${resolvedStyle.labelBn}",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyanPlayhead,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(7.dp))

        if (activeBars.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF101726))
                    .border(1.dp, CyanPlayhead.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "০টি ডেটা · স্টুডিও থেকে নিজের মতো আইটেম (+ যোগ করুন)",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(12.dp)
                )
            }
            return@Column
        }

        fun barColorAt(idx: Int, itemColorHex: Long): Color =
            if (config.paletteTheme == ChartPaletteTheme.DEFAULT_MIX) Color(itemColorHex)
            else Color(paletteColors[idx % paletteColors.size])

        when (resolvedStyle) {
            com.example.domain.model.ChartVisualStyle.HORIZONTAL_BAR -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(if (activeBars.size > 24) 1.dp else 3.dp)
                ) {
                    activeBars.forEachIndexed { idx, bar ->
                        val barColor = barColorAt(idx, bar.colorHex)
                        val frac = ((bar.value / maxVal) * animProgress).coerceIn(0.03f, 1f)
                        Row(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (activeBars.size <= 22) {
                                if (activeBars.size <= 14) {
                                    Text(
                                        text = "#${idx + 1}",
                                        fontSize = 7.sp,
                                        color = barColor.copy(alpha = 0.85f),
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.width(18.dp)
                                    )
                                }
                                Text(
                                    text = bar.label,
                                    fontSize = if (activeBars.size > 14) 7.sp else 9.sp,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.width(56.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(0.76f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF151C2C))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(frac)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(barColor.copy(alpha = 0.72f), barColor)
                                            )
                                        )
                                )
                            }
                            if (activeBars.size <= 18) {
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "${bar.value.toInt()}${config.unitSuffix}",
                                    fontSize = 8.sp,
                                    color = barColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            com.example.domain.model.ChartVisualStyle.VERTICAL_BAR -> {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(if (activeBars.size > 30) 1.dp else 3.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    activeBars.forEachIndexed { idx, bar ->
                        val barColor = barColorAt(idx, bar.colorHex)
                        val frac = ((bar.value / maxVal) * animProgress).coerceIn(0.04f, 1f)
                        Column(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            if (activeBars.size <= 10) {
                                Text(
                                    text = "${bar.value.toInt()}",
                                    fontSize = 7.sp,
                                    color = barColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(frac * 0.82f)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(barColor, barColor.copy(alpha = 0.65f))
                                        )
                                    )
                            )
                            if (activeBars.size <= 12) {
                                Text(
                                    text = bar.label.take(5),
                                    fontSize = 7.sp,
                                    color = TextSecondary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            com.example.domain.model.ChartVisualStyle.PIE_CIRCLE,
            com.example.domain.model.ChartVisualStyle.DONUT_RING -> {
                val isDonut = resolvedStyle == com.example.domain.model.ChartVisualStyle.DONUT_RING
                val sumValues = activeBars.sumOf { it.value.toDouble().coerceAtLeast(0.01) }.toFloat().coerceAtLeast(1f)
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier.weight(1.1f).fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                            val d = minOf(size.width, size.height)
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val strokeW = d * 0.23f
                            val arcTopLeft = Offset(cx - d / 2f + if (isDonut) strokeW / 2f else 0f, cy - d / 2f + if (isDonut) strokeW / 2f else 0f)
                            val arcSize = if (isDonut) Size(d - strokeW, d - strokeW) else Size(d, d)

                            var startAngle = -90f
                            val maxSweepTotal = 360f * animProgress.coerceIn(0.04f, 1f)
                            var sweptSoFar = 0f

                            activeBars.forEachIndexed { idx, bar ->
                                val col = barColorAt(idx, bar.colorHex)
                                val sliceFull = ((bar.value.coerceAtLeast(0.01f) / sumValues) * 360f)
                                val remainingAllowed = (maxSweepTotal - sweptSoFar).coerceAtLeast(0f)
                                val sweep = minOf(sliceFull, remainingAllowed)
                                if (sweep > 0.4f) {
                                    if (isDonut) {
                                        drawArc(
                                            color = col,
                                            startAngle = startAngle,
                                            sweepAngle = (sweep - 1.2f).coerceAtLeast(0.5f),
                                            useCenter = false,
                                            topLeft = arcTopLeft,
                                            size = arcSize,
                                            style = Stroke(width = strokeW, cap = StrokeCap.Butt)
                                        )
                                    } else {
                                        drawArc(
                                            color = col,
                                            startAngle = startAngle,
                                            sweepAngle = sweep,
                                            useCenter = true,
                                            topLeft = arcTopLeft,
                                            size = arcSize
                                        )
                                        drawArc(
                                            color = Color(0xFF0A0F1A),
                                            startAngle = startAngle,
                                            sweepAngle = sweep,
                                            useCenter = true,
                                            topLeft = arcTopLeft,
                                            size = arcSize,
                                            style = Stroke(width = 1.6f)
                                        )
                                    }
                                }
                                startAngle += sliceFull
                                sweptSoFar += sliceFull
                            }
                        }
                        if (isDonut) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${sumValues.toInt()}${config.unitSuffix}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "মোট",
                                    fontSize = 8.sp,
                                    color = AmberDocumentary
                                )
                            }
                        }
                    }

                    // Legend column on the right
                    Column(
                        modifier = Modifier.weight(0.95f).fillMaxHeight(),
                        verticalArrangement = Arrangement.Center
                    ) {
                        activeBars.take(8).forEachIndexed { idx, bar ->
                            val col = barColorAt(idx, bar.colorHex)
                            val pct = ((bar.value / sumValues) * 100f).toInt()
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 1.5.dp)
                            ) {
                                Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(col))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = bar.label,
                                    fontSize = 8.sp,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "$pct%",
                                    fontSize = 8.sp,
                                    color = col,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            com.example.domain.model.ChartVisualStyle.LINE_GRAPH,
            com.example.domain.model.ChartVisualStyle.AREA_WAVE -> {
                val isWaveArea = resolvedStyle == com.example.domain.model.ChartVisualStyle.AREA_WAVE
                val primaryCol = Color(paletteColors.first())
                val secondaryCol = Color(paletteColors[1 % paletteColors.size])
                Column(modifier = Modifier.fillMaxSize()) {
                    Canvas(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
                        val w = size.width
                        val h = size.height
                        // Subtle reference lines
                        for (g in 0..3) {
                            val gy = h * (g / 3f)
                            drawLine(Color.White.copy(alpha = 0.08f), Offset(0f, gy), Offset(w, gy), strokeWidth = 1f)
                        }

                        val pts = activeBars.mapIndexed { idx, bar ->
                            val x = if (activeBars.size == 1) w / 2f else (idx.toFloat() / (activeBars.size - 1)) * w
                            val normY = (bar.value / maxVal).coerceIn(0.03f, 1f)
                            val y = h - (normY * h * 0.88f)
                            Offset(x, y)
                        }

                        val linePath = Path()
                        val maxDrawX = w * animProgress.coerceIn(0.04f, 1f)
                        val visiblePts = pts.filter { it.x <= maxDrawX + 2f }.ifEmpty { listOf(pts.first()) }

                        if (visiblePts.size == 1) {
                            linePath.moveTo(0f, visiblePts[0].y)
                            linePath.lineTo(visiblePts[0].x, visiblePts[0].y)
                        } else {
                            linePath.moveTo(visiblePts[0].x, visiblePts[0].y)
                            for (i in 1 until visiblePts.size) {
                                val prev = visiblePts[i - 1]
                                val cur = visiblePts[i]
                                if (isWaveArea) {
                                    val midX = (prev.x + cur.x) / 2f
                                    linePath.cubicTo(midX, prev.y, midX, cur.y, cur.x, cur.y)
                                } else {
                                    linePath.lineTo(cur.x, cur.y)
                                }
                            }
                        }

                        if (isWaveArea && visiblePts.size >= 1) {
                            val fillPath = Path().apply {
                                addPath(linePath)
                                lineTo(visiblePts.last().x, h)
                                lineTo(visiblePts.first().x, h)
                                close()
                            }
                            drawPath(
                                path = fillPath,
                                brush = Brush.verticalGradient(
                                    listOf(primaryCol.copy(alpha = 0.50f), primaryCol.copy(alpha = 0.04f))
                                )
                            )
                        }

                        drawPath(
                            path = linePath,
                            color = primaryCol.copy(alpha = 0.30f),
                            style = Stroke(width = 8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                        drawPath(
                            path = linePath,
                            brush = Brush.horizontalGradient(listOf(primaryCol, secondaryCol)),
                            style = Stroke(width = 3.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )

                        visiblePts.forEachIndexed { i, p ->
                            val dotCol = barColorAt(i, activeBars[i].colorHex)
                            drawCircle(dotCol.copy(alpha = 0.28f), radius = 9f, center = p)
                            drawCircle(dotCol, radius = 4.5f, center = p)
                            drawCircle(Color.White, radius = 1.8f, center = p)
                        }
                    }
                    // Bottom X labels
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val step = (activeBars.size / 6).coerceAtLeast(1)
                        activeBars.filterIndexed { idx, _ -> idx % step == 0 || idx == activeBars.lastIndex }.forEach { bar ->
                            Text(
                                text = bar.label.take(6),
                                fontSize = 7.sp,
                                color = TextSecondary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            com.example.domain.model.ChartVisualStyle.RADAR_WEB -> {
                val primaryCol = Color(paletteColors.first())
                val secondaryCol = Color(paletteColors[1 % paletteColors.size])
                Box(modifier = Modifier.fillMaxSize()) {
                    Canvas(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val radius = (minOf(size.width, size.height) * 0.44f).coerceAtLeast(10f)
                        val spokes = activeBars.size.coerceAtLeast(3)

                        // Concentric web rings
                        for (ring in 1..3) {
                            val r = radius * (ring / 3f)
                            val ringPath = Path()
                            for (i in 0 until spokes) {
                                val ang = (-PI / 2.0) + (2.0 * PI * i / spokes)
                                val px = cx + (cos(ang) * r).toFloat()
                                val py = cy + (sin(ang) * r).toFloat()
                                if (i == 0) ringPath.moveTo(px, py) else ringPath.lineTo(px, py)
                            }
                            ringPath.close()
                            drawPath(ringPath, Color.White.copy(alpha = 0.14f), style = Stroke(width = 1f))
                        }
                        for (i in 0 until spokes) {
                            val ang = (-PI / 2.0) + (2.0 * PI * i / spokes)
                            drawLine(
                                color = Color.White.copy(alpha = 0.14f),
                                start = Offset(cx, cy),
                                end = Offset(cx + (cos(ang) * radius).toFloat(), cy + (sin(ang) * radius).toFloat()),
                                strokeWidth = 1f
                            )
                        }

                        val polyPath = Path()
                        val pts = (0 until spokes).map { i ->
                            val item = activeBars[i % activeBars.size]
                            val frac = ((item.value / maxVal) * animProgress).coerceIn(0.08f, 1f)
                            val ang = (-PI / 2.0) + (2.0 * PI * i / spokes)
                            Offset(
                                cx + (cos(ang) * radius * frac).toFloat(),
                                cy + (sin(ang) * radius * frac).toFloat()
                            )
                        }
                        pts.forEachIndexed { i, pt ->
                            if (i == 0) polyPath.moveTo(pt.x, pt.y) else polyPath.lineTo(pt.x, pt.y)
                        }
                        polyPath.close()

                        drawPath(
                            path = polyPath,
                            brush = Brush.radialGradient(listOf(primaryCol.copy(alpha = 0.48f), secondaryCol.copy(alpha = 0.22f)))
                        )
                        drawPath(
                            path = polyPath,
                            color = primaryCol,
                            style = Stroke(width = 2.8f, join = StrokeJoin.Round)
                        )
                        pts.forEachIndexed { i, pt ->
                            val dotCol = barColorAt(i % activeBars.size, activeBars[i % activeBars.size].colorHex)
                            drawCircle(dotCol, radius = 4.5f, center = pt)
                        }
                    }
                }
            }
        }
    }
}

private fun rememberChartBars(config: ComparisonChartConfig) =
    config.bars
        .take(config.barCount.coerceIn(0, 100))
        .let { list -> if (config.sortDescending) list.sortedByDescending { it.value } else list }

private fun buildBorderClipShape(border: BorderConfig): Shape? {
    val inset = border.insetPercent.coerceIn(0f, 0.25f)
    return when (border.style) {
        BorderShapeStyle.ROUNDED_CINEMA -> GenericShape { size, _ ->
            val ix = size.width * inset
            val iy = size.height * inset
            val r = minOf(size.width, size.height) * 0.06f + border.cornerRadiusDp
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    rect = Rect(ix, iy, size.width - ix, size.height - iy),
                    cornerRadius = CornerRadius(r, r)
                )
            )
        }
        BorderShapeStyle.CHAMFERED_OCTAGON -> GenericShape { size, _ ->
            val ix = size.width * inset
            val iy = size.height * inset
            val l = ix; val t = iy; val r = size.width - ix; val b = size.height - iy
            val cut = minOf(r - l, b - t) * 0.12f
            moveTo(l + cut, t); lineTo(r - cut, t); lineTo(r, t + cut); lineTo(r, b - cut)
            lineTo(r - cut, b); lineTo(l + cut, b); lineTo(l, b - cut); lineTo(l, t + cut)
            close()
        }
        BorderShapeStyle.DIAMOND_FRAME -> GenericShape { size, _ ->
            val ix = size.width * inset
            val iy = size.height * inset
            val cx = size.width / 2f; val cy = size.height / 2f
            moveTo(cx, iy); lineTo(size.width - ix, cy); lineTo(cx, size.height - iy); lineTo(ix, cy)
            close()
        }
        BorderShapeStyle.HEXAGON_FRAME -> GenericShape { size, _ ->
            val ix = size.width * inset; val iy = size.height * inset
            val cx = size.width / 2f; val cy = size.height / 2f
            val rx = (size.width - 2 * ix) * 0.48f
            val ry = (size.height - 2 * iy) * 0.48f
            for (i in 0 until 6) {
                val angle = (PI / 3.0) * i - PI / 2.0
                val px = cx + (rx * cos(angle)).toFloat()
                val py = cy + (ry * sin(angle)).toFloat()
                if (i == 0) moveTo(px, py) else lineTo(px, py)
            }
            close()
        }
        BorderShapeStyle.CIRCLE_PORTRAIT -> GenericShape { size, _ ->
            val ix = size.width * inset; val iy = size.height * inset
            val r = minOf(size.width - 2 * ix, size.height - 2 * iy) * 0.47f
            val cx = size.width / 2f; val cy = size.height / 2f
            addOval(Rect(cx - r, cy - r, cx + r, cy + r))
        }
        else -> null
    }
}

@Composable
private fun BorderShapeOverlay(
    border: BorderConfig,
    modifier: Modifier = Modifier
) {
    val primaryCol = Color(border.colorHex)
    val secondaryCol = Color(border.secondaryColorHex)
    Canvas(modifier = modifier) {
        val ix = size.width * border.insetPercent.coerceIn(0f, 0.25f)
        val iy = size.height * border.insetPercent.coerceIn(0f, 0.25f)
        val l = ix; val t = iy; val r = size.width - ix; val b = size.height - iy
        val w = (r - l).coerceAtLeast(4f)
        val h = (b - t).coerceAtLeast(4f)
        val cx = size.width / 2f; val cy = size.height / 2f
        val strokePx = border.thicknessDp.dp.toPx().coerceIn(1.5f, 36f)
        val glowAlpha = (border.glowRadius / 24f).coerceIn(0f, 0.55f)

        fun strokeGlowOrSolid(drawOp: (Color, Float) -> Unit) {
            if (glowAlpha > 0.04f) {
                drawOp(primaryCol.copy(alpha = glowAlpha), strokePx * 2.4f)
            }
            drawOp(primaryCol, strokePx)
        }

        when (border.style) {
            BorderShapeStyle.NONE -> Unit
            BorderShapeStyle.RECT_SOLID -> strokeGlowOrSolid { col, sw ->
                drawRect(col, Offset(l, t), Size(w, h), style = Stroke(sw))
            }
            BorderShapeStyle.ROUNDED_CINEMA -> {
                val rad = minOf(w, h) * 0.06f + border.cornerRadiusDp
                strokeGlowOrSolid { col, sw ->
                    drawRoundRect(col, Offset(l, t), Size(w, h), CornerRadius(rad, rad), style = Stroke(sw))
                }
            }
            BorderShapeStyle.DOUBLE_FRAME -> {
                drawRect(primaryCol, Offset(l, t), Size(w, h), style = Stroke(strokePx))
                val innerPad = strokePx * 1.9f
                drawRect(
                    secondaryCol,
                    Offset(l + innerPad, t + innerPad),
                    Size((w - 2 * innerPad).coerceAtLeast(2f), (h - 2 * innerPad).coerceAtLeast(2f)),
                    style = Stroke(strokePx * 0.55f)
                )
            }
            BorderShapeStyle.TECH_BRACKETS -> {
                val arm = minOf(w, h) * 0.14f
                strokeGlowOrSolid { col, sw ->
                    drawLine(col, Offset(l, t), Offset(l + arm, t), sw, StrokeCap.Round)
                    drawLine(col, Offset(l, t), Offset(l, t + arm), sw, StrokeCap.Round)
                    drawLine(col, Offset(r, t), Offset(r - arm, t), sw, StrokeCap.Round)
                    drawLine(col, Offset(r, t), Offset(r, t + arm), sw, StrokeCap.Round)
                    drawLine(col, Offset(l, b), Offset(l + arm, b), sw, StrokeCap.Round)
                    drawLine(col, Offset(l, b), Offset(l, b - arm), sw, StrokeCap.Round)
                    drawLine(col, Offset(r, b), Offset(r - arm, b), sw, StrokeCap.Round)
                    drawLine(col, Offset(r, b), Offset(r, b - arm), sw, StrokeCap.Round)
                }
            }
            BorderShapeStyle.CHAMFERED_OCTAGON -> {
                val cut = minOf(w, h) * 0.12f
                val path = Path().apply {
                    moveTo(l + cut, t); lineTo(r - cut, t); lineTo(r, t + cut); lineTo(r, b - cut)
                    lineTo(r - cut, b); lineTo(l + cut, b); lineTo(l, b - cut); lineTo(l, t + cut); close()
                }
                strokeGlowOrSolid { col, sw -> drawPath(path, col, style = Stroke(sw, join = StrokeJoin.Round)) }
            }
            BorderShapeStyle.DIAMOND_FRAME -> {
                val path = Path().apply {
                    moveTo(cx, t); lineTo(r, cy); lineTo(cx, b); lineTo(l, cy); close()
                }
                strokeGlowOrSolid { col, sw -> drawPath(path, col, style = Stroke(sw, join = StrokeJoin.Round)) }
            }
            BorderShapeStyle.HEXAGON_FRAME -> {
                val rx = w * 0.48f; val ry = h * 0.48f
                val path = Path().apply {
                    for (i in 0 until 6) {
                        val angle = (PI / 3.0) * i - PI / 2.0
                        val px = cx + (rx * cos(angle)).toFloat()
                        val py = cy + (ry * sin(angle)).toFloat()
                        if (i == 0) moveTo(px, py) else lineTo(px, py)
                    }
                    close()
                }
                strokeGlowOrSolid { col, sw -> drawPath(path, col, style = Stroke(sw, join = StrokeJoin.Round)) }
            }
            BorderShapeStyle.CIRCLE_PORTRAIT -> {
                val rad = minOf(w, h) * 0.47f
                strokeGlowOrSolid { col, sw -> drawCircle(col, radius = rad, center = Offset(cx, cy), style = Stroke(sw)) }
            }
            BorderShapeStyle.NEON_DASHED -> {
                val rad = border.cornerRadiusDp.dp.toPx()
                val dash = PathEffect.dashPathEffect(floatArrayOf(strokePx * 3.5f, strokePx * 2f), 0f)
                drawRoundRect(primaryCol, Offset(l, t), Size(w, h), CornerRadius(rad, rad), style = Stroke(strokePx, pathEffect = dash))
            }
        }
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
            val style = config.mapStyle
            val gridColor = Color(style.gridHex)
            val routeColor = Color(style.routeHex)
            val markerColor = Color(style.markerHex)
            Box(
                modifier = modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(style.bgHex))
                    .border(1.5.dp, gridColor.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cols = 9
                    val rows = 6
                    for (c in 1 until cols) {
                        val x = size.width * c / cols
                        drawLine(gridColor.copy(alpha = 0.14f), Offset(x, 0f), Offset(x, size.height), 1f)
                    }
                    for (r in 1 until rows) {
                        val y = size.height * r / rows
                        drawLine(gridColor.copy(alpha = 0.14f), Offset(0f, y), Offset(size.width, y), 1f)
                    }
                    // Subtle Radar Sweep Rings centered on Point B
                    val pA = Offset(size.width * config.pointAX.coerceIn(0.06f, 0.94f), size.height * config.pointAY.coerceIn(0.12f, 0.90f))
                    val pB = Offset(size.width * config.pointBX.coerceIn(0.06f, 0.94f), size.height * config.pointBY.coerceIn(0.12f, 0.90f))
                    val ctrl = if (config.useWaypointC) {
                        Offset(size.width * config.pointCX.coerceIn(0.06f, 0.94f), size.height * config.pointCY.coerceIn(0.12f, 0.90f))
                    } else {
                        val midX = (pA.x + pB.x) / 2f
                        val midY = minOf(pA.y, pB.y) - size.height * 0.18f
                        Offset(midX, midY.coerceAtLeast(size.height * 0.08f))
                    }

                    // Draw faint full planned route
                    val fullPath = Path().apply {
                        moveTo(pA.x, pA.y)
                        quadraticBezierTo(ctrl.x, ctrl.y, pB.x, pB.y)
                    }
                    drawPath(
                        path = fullPath,
                        color = routeColor.copy(alpha = 0.25f),
                        style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f))
                    )

                    // Sample partial Bezier up to animated progress
                    val animT = (progress * 1.15f).coerceIn(0.02f, 1f)
                    val activePath = Path()
                    val steps = 28
                    var curPt = pA
                    for (s in 0..steps) {
                        val st = (s.toFloat() / steps) * animT
                        val omt = 1f - st
                        val bx = omt * omt * pA.x + 2f * omt * st * ctrl.x + st * st * pB.x
                        val by = omt * omt * pA.y + 2f * omt * st * ctrl.y + st * st * pB.y
                        if (s == 0) activePath.moveTo(bx, by) else activePath.lineTo(bx, by)
                        if (s == steps) curPt = Offset(bx, by)
                    }
                    // Glow under-stroke + crisp main arc
                    drawPath(activePath, routeColor.copy(alpha = 0.28f), style = Stroke(width = 10f, cap = StrokeCap.Round))
                    drawPath(activePath, routeColor, style = Stroke(width = 4f, cap = StrokeCap.Round))

                    // Radar sonar rings at markers
                    val pulse = 10f + 18f * ((progress * 5f) % 1f)
                    drawCircle(markerColor.copy(alpha = 0.25f), radius = pulse, center = pA)
                    drawCircle(markerColor, radius = 7f, center = pA)

                    if (config.useWaypointC) {
                        drawCircle(gridColor.copy(alpha = 0.35f), radius = 11f, center = ctrl)
                        drawCircle(Color.White, radius = 5.5f, center = ctrl)
                    }

                    drawCircle(routeColor.copy(alpha = 0.28f), radius = pulse * 1.15f, center = pB)
                    drawCircle(routeColor, radius = 7.5f, center = pB)

                    // Moving Target Blip + Halo on Route
                    drawCircle(Color.White.copy(alpha = 0.28f), radius = 14f, center = curPt)
                    drawCircle(Color.White, radius = 6f, center = curPt)
                }

                // Point A & B floating labels
                Text(
                    text = config.pointALabel,
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 10.dp, bottom = 8.dp)
                        .background(Color(0xBB070B14), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
                if (config.distanceLabel.isNotBlank()) {
                    Text(
                        text = config.distanceLabel,
                        color = routeColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 10.dp, bottom = 8.dp)
                            .background(Color(0xCC070B14), RoundedCornerShape(6.dp))
                            .border(0.8.dp, routeColor.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                val routeSummary = if (config.useWaypointC && config.pointCLabel.isNotBlank()) {
                    "${config.headline} · ${config.pointALabel} ➔ ${config.pointCLabel} ➔ ${config.pointBLabel}"
                } else {
                    "${config.headline} · ${config.pointALabel} ➔ ${config.pointBLabel}"
                }
                Text(
                    text = routeSummary,
                    color = TextPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(Color(0xCC08101C), RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
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
                val count = (210 * inten).toInt().coerceIn(30, 260)
                repeat(count) {
                    val x = rand.nextFloat() * size.width
                    val y = rand.nextFloat() * size.height
                    val c = if (it % 2 == 0) Color.White.copy(alpha = 0.16f * inten) else Color.Black.copy(alpha = 0.24f * inten)
                    drawCircle(color = c, radius = 1.4f + (it % 2) * 0.6f, center = Offset(x, y))
                }
                // Subtle vintage gate-weave hairline scratch
                if (rand.nextFloat() < 0.35f) {
                    val sx = rand.nextFloat() * size.width
                    drawLine(Color.White.copy(alpha = 0.14f * inten), Offset(sx, 0f), Offset(sx + 2f, size.height), strokeWidth = 1f)
                }
            }
            VfxEffectType.ANAMORPHIC_FLARE -> {
                val fy = size.height * (0.28f + 0.09f * sin(t * 1.4f))
                val fx = size.width * (0.5f + 0.15f * cos(t * 0.8f))
                // Wide Cinema Cyan-White Lens Streak
                drawRect(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, CyanPlayhead.copy(alpha = 0.32f * inten), Color.White.copy(alpha = 0.65f * inten), CyanPlayhead.copy(alpha = 0.32f * inten), Color.Transparent)
                    ),
                    topLeft = Offset(0f, fy - 18f),
                    size = Size(size.width, 36f),
                    blendMode = BlendMode.Screen
                )
                // Core Radial Halation & Optical Lens Bokeh Rings
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.55f * inten), CyanPlayhead.copy(alpha = 0.25f * inten), Color.Transparent),
                        center = Offset(fx, fy),
                        radius = size.minDimension * 0.28f
                    ),
                    center = Offset(fx, fy),
                    radius = size.minDimension * 0.28f,
                    blendMode = BlendMode.Screen
                )
                drawCircle(
                    color = AmberDocumentary.copy(alpha = 0.18f * inten),
                    radius = 22f,
                    center = Offset(size.width - fx * 0.65f, fy + 24f),
                    style = Stroke(width = 4f)
                )
            }
            VfxEffectType.CHROMATIC_ABERRATION -> {
                val shift = 16f * inten
                drawRect(Color(0x55FF2244), topLeft = Offset(-shift, -2f), size = size, style = Stroke(width = 7f))
                drawRect(Color(0x5500E5FF), topLeft = Offset(shift, 2f), size = size, style = Stroke(width = 7f))
                drawCircle(Color(0x28FF2255), radius = size.maxDimension * 0.48f, center = Offset(size.width / 2f - shift * 0.5f, size.height / 2f), style = Stroke(width = 18f))
                drawCircle(Color(0x2800E5FF), radius = size.maxDimension * 0.48f, center = Offset(size.width / 2f + shift * 0.5f, size.height / 2f), style = Stroke(width = 18f))
            }
            VfxEffectType.GLITCH_TEAR -> {
                val rand = Random((playheadMs / 70L).toInt())
                repeat((8 * inten).toInt().coerceAtLeast(3)) {
                    val y = rand.nextFloat() * size.height
                    val h = 5f + rand.nextFloat() * 18f
                    val col = when (it % 3) {
                        0 -> CyanPlayhead.copy(alpha = 0.38f * inten)
                        1 -> Color(0xFFFF4D6D).copy(alpha = 0.36f * inten)
                        else -> Color.White.copy(alpha = 0.22f * inten)
                    }
                    val xOff = (rand.nextFloat() - 0.5f) * 36f * inten
                    drawRect(col, topLeft = Offset(xOff, y), size = Size(size.width * (0.65f + rand.nextFloat() * 0.35f), h))
                }
            }
            VfxEffectType.CINEMA_RAIN -> {
                val rand = Random((playheadMs / 40L).toInt())
                // Depth mist layer
                drawRect(
                    brush = Brush.verticalGradient(listOf(Color.Transparent, Color(0x2279B8FF).copy(alpha = 0.2f * inten))),
                    blendMode = BlendMode.Screen
                )
                repeat((60 * inten).toInt().coerceAtLeast(16)) { idx ->
                    val x = rand.nextFloat() * size.width
                    val y = rand.nextFloat() * size.height
                    val isNear = idx % 3 == 0
                    val len = if (isNear) 44f else 24f
                    drawLine(
                        color = Color(0xCCBCE3FF).copy(alpha = (if (isNear) 0.55f else 0.28f) * inten),
                        start = Offset(x, y),
                        end = Offset(x - len * 0.28f, y + len),
                        strokeWidth = if (isNear) 2.2f else 1.2f
                    )
                }
            }
            VfxEffectType.EMBERS_SPARK -> {
                val rand = Random(42)
                repeat((36 * inten).toInt().coerceAtLeast(10)) { idx ->
                    val x = ((rand.nextFloat() + 0.09f * sin(t * 2.1f + idx)) % 1f) * size.width
                    val y = ((1f - ((rand.nextFloat() + t * (0.22f + (idx % 4) * 0.05f)) % 1f))) * size.height
                    val r = 2.5f + (idx % 4) * 1.4f
                    drawCircle(color = Color(0xFFFF6D00).copy(alpha = 0.26f * inten), radius = r * 2.6f, center = Offset(x, y), blendMode = BlendMode.Screen)
                    drawCircle(color = Color(0xFFFFCA28).copy(alpha = 0.85f * inten), radius = r, center = Offset(x, y))
                }
            }
            VfxEffectType.LIGHT_LEAK -> {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFFF8C00).copy(alpha = 0.54f * inten), Color(0xFFFF2E63).copy(alpha = 0.25f * inten), Color.Transparent),
                        center = Offset(size.width * (0.14f + 0.12f * sin(t)), size.height * (0.18f + 0.08f * cos(t * 0.7f))),
                        radius = size.maxDimension * 0.75f
                    ),
                    blendMode = BlendMode.Screen
                )
            }
            VfxEffectType.LETTERBOX_239 -> {
                val barH = size.height * 0.13f * inten
                drawRect(Color.Black, Offset.Zero, Size(size.width, barH))
                drawRect(Color.Black, Offset(0f, size.height - barH), Size(size.width, barH))
            }
            VfxEffectType.VOLUMETRIC_GOD_RAYS -> {
                val origin = Offset(size.width * 0.12f, -size.height * 0.1f)
                repeat(5) { idx ->
                    val sweep = 0.18f + idx * 0.16f + 0.04f * sin(t * 0.9f + idx)
                    val rayPath = Path().apply {
                        moveTo(origin.x, origin.y)
                        lineTo(size.width * sweep, size.height)
                        lineTo(size.width * (sweep + 0.12f), size.height)
                        close()
                    }
                    drawPath(
                        path = rayPath,
                        brush = Brush.verticalGradient(
                            listOf(Color(0xFFFFE082).copy(alpha = 0.28f * inten), Color.Transparent)
                        ),
                        blendMode = BlendMode.Screen
                    )
                }
            }
            VfxEffectType.GOLD_DUST_BOKEH -> {
                val rand = Random(99)
                repeat((18 * inten).toInt().coerceAtLeast(6)) { idx ->
                    val bx = ((rand.nextFloat() + 0.05f * sin(t + idx * 1.3f)) % 1f) * size.width
                    val by = ((rand.nextFloat() + 0.04f * cos(t * 0.8f + idx)) % 1f) * size.height
                    val radius = 14f + (idx % 5) * 9f
                    val col = if (idx % 2 == 0) AmberDocumentary else CyanPlayhead
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(col.copy(alpha = 0.26f * inten), col.copy(alpha = 0.06f * inten), Color.Transparent),
                            center = Offset(bx, by),
                            radius = radius
                        ),
                        center = Offset(bx, by),
                        radius = radius,
                        blendMode = BlendMode.Screen
                    )
                }
            }
            VfxEffectType.DREAMY_HALATION -> {
                drawRect(
                    brush = Brush.radialGradient(
                        listOf(Color(0xFFFFE4B5).copy(alpha = 0.34f * inten), Color(0xFFFF9E80).copy(alpha = 0.18f * inten), Color.Transparent),
                        center = Offset(size.width * 0.5f, size.height * 0.45f),
                        radius = size.maxDimension * 0.7f
                    ),
                    blendMode = BlendMode.Screen
                )
            }
            VfxEffectType.CAMERA_SHAKE, VfxEffectType.NONE -> Unit
        }
    }
}

@Composable
private fun VideoSurfacePreview(
    uriString: String,
    playheadLocalMs: Long,
    isPlaying: Boolean,
    playbackSpeed: Float,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var mediaPlayer by remember(uriString) { mutableStateOf<MediaPlayer?>(null) }
    var isPrepared by remember(uriString) { mutableStateOf(false) }
    var videoW by remember(uriString) { mutableStateOf(16) }
    var videoH by remember(uriString) { mutableStateOf(9) }
    var lastAppliedSpeed by remember(uriString) { mutableStateOf(1f) }
    var lastSeekTargetMs by remember(uriString) { mutableStateOf(-1L) }

    DisposableEffect(uriString) {
        onDispose {
            runCatching {
                mediaPlayer?.stop()
                mediaPlayer?.release()
            }
            mediaPlayer = null
            isPrepared = false
        }
    }

    LaunchedEffect(isPlaying, isPrepared, playbackSpeed) {
        val mp = mediaPlayer ?: return@LaunchedEffect
        if (!isPrepared) return@LaunchedEffect
        runCatching {
            val quantizedSpeed = ((playbackSpeed.coerceIn(0.25f, 3.0f) * 10f).toInt() / 10f)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && isPlaying && abs(quantizedSpeed - lastAppliedSpeed) > 0.05f) {
                mp.playbackParams = PlaybackParams().apply { speed = quantizedSpeed }
                lastAppliedSpeed = quantizedSpeed
            }
            if (isPlaying && !mp.isPlaying) {
                val dur = mp.duration.coerceAtLeast(1)
                mp.seekTo((playheadLocalMs % dur).toInt().coerceAtLeast(0))
                mp.start()
            } else if (!isPlaying && mp.isPlaying) {
                mp.pause()
            }
        }
    }

    LaunchedEffect(playheadLocalMs, isPrepared, isPlaying) {
        val mp = mediaPlayer ?: return@LaunchedEffect
        if (!isPrepared) return@LaunchedEffect
        runCatching {
            val dur = mp.duration.coerceAtLeast(1)
            val targetMs = (playheadLocalMs % dur).toInt().coerceIn(0, dur - 1)
            val diff = abs(mp.currentPosition - targetMs)
            if (!isPlaying) {
                if (abs(targetMs.toLong() - lastSeekTargetMs) >= 60L) {
                    lastSeekTargetMs = targetMs.toLong()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        mp.seekTo(targetMs.toLong(), MediaPlayer.SEEK_PREVIOUS_SYNC)
                    } else {
                        mp.seekTo(targetMs)
                    }
                }
            } else if (diff > 550) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    mp.seekTo(targetMs.toLong(), MediaPlayer.SEEK_PREVIOUS_SYNC)
                } else {
                    mp.seekTo(targetMs)
                }
            }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextureView(ctx).apply {
                val textureView = this
                isOpaque = false
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                        runCatching {
                            val mp = MediaPlayer().apply {
                                setDataSource(context, Uri.parse(uriString))
                                setSurface(AndroidSurface(surfaceTexture))
                                isLooping = true
                                setVolume(0f, 0f)
                                setOnErrorListener { _, _, _ -> true }
                                setOnVideoSizeChangedListener { _, w, h ->
                                    if (w > 0 && h > 0) {
                                        videoW = w
                                        videoH = h
                                        applyCenterCropTransform(textureView, textureView.width.coerceAtLeast(width), textureView.height.coerceAtLeast(height), w, h)
                                    }
                                }
                                setOnPreparedListener { readyMp ->
                                    isPrepared = true
                                    readyMp.setVolume(0f, 0f)
                                    videoW = readyMp.videoWidth.coerceAtLeast(16)
                                    videoH = readyMp.videoHeight.coerceAtLeast(9)
                                    applyCenterCropTransform(textureView, textureView.width.coerceAtLeast(width), textureView.height.coerceAtLeast(height), videoW, videoH)
                                    val dur = readyMp.duration.coerceAtLeast(1)
                                    readyMp.seekTo((playheadLocalMs % dur).toInt().coerceAtLeast(0))
                                    if (isPlaying) readyMp.start()
                                }
                                prepareAsync()
                            }
                            mediaPlayer = mp
                        }
                    }

                    override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                        applyCenterCropTransform(textureView, width, height, videoW, videoH)
                    }

                    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                        runCatching {
                            mediaPlayer?.stop()
                            mediaPlayer?.release()
                        }
                        mediaPlayer = null
                        isPrepared = false
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit
                }
            }
        },
        update = { tv ->
            if (tv.width > 0 && tv.height > 0) {
                applyCenterCropTransform(tv, tv.width, tv.height, videoW, videoH)
            }
        }
    )
}

private fun applyCenterCropTransform(view: TextureView, viewW: Int, viewH: Int, videoW: Int, videoH: Int) {
    if (viewW <= 0 || viewH <= 0 || videoW <= 0 || videoH <= 0) return
    val scaleX = videoW.toFloat() / viewW.toFloat()
    val scaleY = videoH.toFloat() / viewH.toFloat()
    val maxScale = maxOf(scaleX, scaleY)
    val sx = scaleX / minOf(scaleX, scaleY)
    val sy = scaleY / minOf(scaleX, scaleY)
    val matrix = AndroidMatrix()
    matrix.setScale(if (maxScale > 0f) sx else 1f, if (maxScale > 0f) sy else 1f, viewW / 2f, viewH / 2f)
    view.setTransform(matrix)
}

@Composable
private fun VideoFramePreview(
    uriString: String,
    playheadLocalMs: Long,
    colorFilter: ColorFilter,
    chromaKeyEnabled: Boolean = false,
    chromaKeyStrength: Float = 0.45f,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val retriever = remember(uriString) {
        runCatching {
            MediaMetadataRetriever().apply { setDataSource(context, Uri.parse(uriString)) }
        }.getOrNull()
    }
    DisposableEffect(uriString) {
        onDispose {
            runCatching { retriever?.release() }
        }
    }

    val bucketMs = (playheadLocalMs / 100L) * 100L
    val bitmap by produceState<Bitmap?>(initialValue = null, uriString, bucketMs, chromaKeyEnabled, chromaKeyStrength) {
        val mmr = retriever ?: return@produceState
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val frame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    mmr.getScaledFrameAtTime(bucketMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 640, 640)
                } else {
                    mmr.getFrameAtTime(bucketMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }
                if (frame != null && chromaKeyEnabled) {
                    val keyed = TransitionAndChromaEngine.applyGreenScreenChromaKey(frame, chromaKeyStrength)
                    frame.recycle()
                    keyed
                } else {
                    frame
                }
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
    xform: EvaluatedTransform,
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

    val visibleText = if (xform.textRevealFraction < 0.999f) {
        when {
            xform.textWordMode -> {
                val words = cfg.text.split(" ")
                val keep = (words.size * xform.textRevealFraction).toInt().coerceIn(1, words.size.coerceAtLeast(1))
                words.take(keep).joinToString(" ")
            }
            xform.textLineMode -> {
                val lines = cfg.text.split("\n")
                val keep = (lines.size * xform.textRevealFraction).toInt().coerceIn(1, lines.size.coerceAtLeast(1))
                lines.take(keep).joinToString("\n")
            }
            else -> {
                val keepChars = (cfg.text.length * xform.textRevealFraction).toInt().coerceIn(0, cfg.text.length)
                cfg.text.take(keepChars)
            }
        }
    } else {
        cfg.text
    }

    val gradientBrush = if (cfg.useGradient && !cfg.karaokeHighlight) {
        Brush.linearGradient(listOf(Color(cfg.gradientStartHex), Color(cfg.gradientEndHex)))
    } else null

    val annotatedText = if (cfg.karaokeHighlight) {
        val words = visibleText.split(" ")
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
    } else if (xform.splitCharWave > 0.05f) {
        buildAnnotatedString {
            visibleText.forEachIndexed { i, ch ->
                val wave = sin(playheadMs * 0.025f + i * 0.65f)
                withStyle(
                    SpanStyle(
                        color = mainColor.copy(alpha = (0.72f + 0.28f * wave).coerceIn(0.4f, 1f)),
                        fontWeight = FontWeight.Bold
                    )
                ) {
                    append(ch)
                }
            }
        }
    } else {
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(visibleText)
            }
        }
    }

    val fontSize = (cfg.fontSizeSp * 0.72f).sp
    val lineHeight = (cfg.fontSizeSp * 0.72f * cfg.lineHeightMult.coerceIn(0.85f, 2.2f)).sp
    val letterSpacing = (cfg.letterSpacingSp + xform.textSpacingBoostSp).sp
    val effShadowRadius = maxOf(cfg.shadowRadius, xform.shadowStrength * 14f)
    val shadow = if (effShadowRadius > 0.2f) {
        Shadow(
            color = Color(cfg.shadowColorHex),
            offset = Offset(0f, 3f),
            blurRadius = effShadowRadius
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
                            text = visibleText,
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
                if (xform.underlineProgress > 0.02f) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(xform.underlineProgress.coerceIn(0.02f, 1f))
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(accentColor)
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
