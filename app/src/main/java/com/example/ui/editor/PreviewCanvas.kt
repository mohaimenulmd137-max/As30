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
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.domain.engine.AudioSynthEngine
import com.example.domain.engine.ColorGradingEngine
import com.example.domain.engine.KeyframeInterpolator
import com.example.domain.engine.SpeedRampEngine
import com.example.domain.engine.VideoExportEngine
import com.example.domain.model.BlendModeOption
import com.example.domain.model.LowerThirdTemplate
import com.example.domain.model.LumetriColorGrade
import com.example.domain.model.MaskConfig
import com.example.domain.model.MaskShape
import com.example.domain.model.StudioFontOption
import com.example.domain.model.StudioProject
import com.example.domain.model.TimelineClip
import com.example.domain.model.TrackType
import com.example.ui.theme.AmberDocumentary
import com.example.ui.theme.CyanPlayhead
import com.example.ui.theme.EmeraldAudio
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.roundToInt

@Composable
fun PreviewCanvas(
    project: StudioProject,
    playheadMs: Long,
    selectedClipId: String?,
    onSelectClip: (String?) -> Unit,
    onDirectGesture: (clipId: String, dxNorm: Float, dyNorm: Float, scale: Float, rot: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeAdjustment = project.clips
        .filter { it.trackType == TrackType.ADJUSTMENT && playheadMs in it.startMs..it.endMs }
        .fold(LumetriColorGrade()) { acc, c -> ColorGradingEngine.mergeAdjustmentGrades(acc, c.colorGrade) }
    val hasAdjustment = project.clips.any { it.trackType == TrackType.ADJUSTMENT && playheadMs in it.startMs..it.endMs }

    val visualClips = project.clips
        .filter {
            (it.trackType == TrackType.VIDEO || it.trackType == TrackType.GRAPHICS || it.trackType == TrackType.TEXT) &&
                playheadMs in it.startMs..it.endMs
        }
        .sortedByDescending { it.trackType.order }

    val activeMusic = project.clips.firstOrNull { it.trackType == TrackType.MUSIC && playheadMs in it.startMs..it.endMs }
    val activeVo = project.clips.firstOrNull { it.trackType == TrackType.VOICEOVER && playheadMs in it.startMs..it.endMs }
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
            .background(Color(0xFF06070B))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(project.aspectRatio.ratio, matchHeightConstraintsFirst = true)
                .clip(RoundedCornerShape(14.dp))
                .background(StudioObsidian)
                .border(1.dp, StudioBorder, RoundedCornerShape(14.dp))
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
                        text = "ChitraFlow Canvas",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "নিচের + বাটন থেকে ভিডিও, ছবি বা বাংলা টেক্সট যোগ করুন",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary.copy(alpha = 0.7f),
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

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (maskShape != null) Modifier.clip(maskShape) else Modifier)
                        .offset {
                            IntOffset(
                                (xform.posX * canvasWidthPx).roundToInt(),
                                (xform.posY * canvasHeightPx).roundToInt()
                            )
                        }
                        .graphicsLayer {
                            scaleX = xform.scale
                            scaleY = xform.scale
                            rotationZ = xform.rotation
                            alpha = xform.opacity
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                        .pointerInput(clip.id, canvasWidthPx, canvasHeightPx) {
                            detectTransformGestures { _, pan, zoom, rotation ->
                                onSelectClip(clip.id)
                                onDirectGesture(
                                    clip.id,
                                    pan.x / canvasWidthPx,
                                    pan.y / canvasHeightPx,
                                    zoom,
                                    rotation
                                )
                            }
                        }
                        .pointerInput(clip.id) {
                            detectTapGestures { onSelectClip(clip.id) }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Media image / video frame
                    clip.mediaSource?.let { src ->
                        val colorFilter = ColorFilter.colorMatrix(ColorGradingEngine.buildComposeColorMatrix(effectiveGrade))
                        if (src.uriString != null) {
                            if (src.isVideo) {
                                VideoFramePreview(
                                    uriString = src.uriString,
                                    playheadLocalMs = SpeedRampEngine.computeRemappedVideoTimeMs(
                                        clip.speedRamp,
                                        (playheadMs - clip.startMs).coerceAtLeast(0L),
                                        clip.durationMs
                                    ),
                                    colorFilter = colorFilter,
                                    modifier = Modifier.fillMaxSize().then(blurMod)
                                )
                            } else {
                                AsyncImage(
                                    model = Uri.parse(src.uriString),
                                    contentDescription = clip.title,
                                    contentScale = ContentScale.Crop,
                                    colorFilter = colorFilter,
                                    modifier = Modifier.fillMaxSize().then(blurMod)
                                )
                            }
                        } else if (src.drawableName != null) {
                            Image(
                                painter = painterResource(id = VideoExportEngine.resolveDrawableId(src.drawableName)),
                                contentDescription = clip.title,
                                contentScale = ContentScale.Crop,
                                colorFilter = colorFilter,
                                modifier = Modifier.fillMaxSize().then(blurMod)
                            )
                        }
                    }

                    // Blend tint / Glow halo
                    if (clip.blendMode != BlendModeOption.NORMAL) {
                        val tintAlpha = 0.18f
                        val tintColor = when (clip.blendMode) {
                            BlendModeOption.SCREEN -> CyanPlayhead.copy(alpha = tintAlpha)
                            BlendModeOption.OVERLAY -> AmberDocumentary.copy(alpha = tintAlpha)
                            BlendModeOption.MULTIPLY -> Color(0xFF0A0C12).copy(alpha = 0.35f)
                            BlendModeOption.PLUS_ADD -> Color.White.copy(alpha = 0.22f)
                            BlendModeOption.NORMAL -> Color.Transparent
                        }
                        Box(modifier = Modifier.fillMaxSize().background(tintColor))
                    }

                    // Text / Documentary Lower Third Overlay
                    clip.textConfig?.let { tc ->
                        TextAndLowerThirdOverlay(
                            clip = clip,
                            playheadMs = playheadMs,
                            isSelected = isSelected
                        )
                    }

                    // Selection border for non-text overlays
                    if (isSelected && clip.textConfig == null && clip.trackType != TrackType.VIDEO) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize(0.92f)
                                .border(1.5.dp, CyanPlayhead, RoundedCornerShape(10.dp))
                        )
                    }
                }
            }

            // Adjustment Glow & Vignette pass
            val vig = if (hasAdjustment) activeAdjustment.vignette else 0.12f
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

            // Live Speed & Auto-Ducking HUD badges
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
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
                            Text(
                                text = "%.2fx".format(liveSpeed),
                                style = MaterialTheme.typography.labelSmall,
                                color = CyanPlayhead
                            )
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
                            Text(
                                text = "Duck ${(musicGain * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldAudio
                            )
                        }
                    }
                }
            }
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
    val fontOpt = StudioFontOption.entries.firstOrNull { it.id == cfg.fontId } ?: StudioFontOption.HIND_SILIGURI
    val accentColor = Color(cfg.accentColorHex)
    val mainColor = Color(cfg.textColorHex)

    val progress = if (clip.durationMs > 0L) {
        ((playheadMs - clip.startMs).toFloat() / clip.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

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
            withStyle(SpanStyle(color = mainColor, fontWeight = FontWeight.Bold)) {
                append(cfg.text)
            }
        }
    }

    val hasCard = cfg.lowerThirdTemplate != LowerThirdTemplate.NONE || cfg.backgroundPill
    val borderMod = if (isSelected) {
        Modifier.border(1.5.dp, CyanPlayhead, RoundedCornerShape(10.dp))
    } else Modifier

    Box(
        modifier = Modifier
            .padding(horizontal = 14.dp)
            .then(borderMod)
            .then(
                if (hasCard) {
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xDC0D111C), Color(0xBB192033))
                            )
                        )
                        .border(1.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
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
            Column(horizontalAlignment = if (hasCard) Alignment.Start else Alignment.CenterHorizontally) {
                Text(
                    text = annotatedText,
                    fontFamily = fontOpt.family,
                    fontSize = (cfg.fontSizeSp * 0.72f).sp,
                    lineHeight = (cfg.fontSizeSp * 0.92f).sp,
                    textAlign = if (hasCard) TextAlign.Start else TextAlign.Center
                )
                if (cfg.subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = cfg.subtitle,
                        fontFamily = fontOpt.family,
                        fontSize = (cfg.fontSizeSp * 0.44f).sp,
                        color = accentColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

private fun buildMaskShape(config: MaskConfig): androidx.compose.ui.graphics.Shape? {
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
