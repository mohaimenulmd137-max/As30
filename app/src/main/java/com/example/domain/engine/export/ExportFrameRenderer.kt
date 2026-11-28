package com.example.domain.engine.export

import android.graphics.BlurMaskFilter
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.domain.engine.ColorGradingEngine
import com.example.domain.engine.EvaluatedTransform
import com.example.domain.engine.KeyframeInterpolator
import com.example.domain.engine.TransitionAndChromaEngine
import com.example.domain.map.UniversalMapRenderer
import com.example.domain.model.BlendModeOption
import com.example.domain.model.BorderConfig
import com.example.domain.model.BorderShapeStyle
import com.example.domain.model.LowerThirdTemplate
import com.example.domain.model.LumetriColorGrade
import com.example.domain.model.MaskShape
import com.example.domain.model.StudioFontOption
import com.example.domain.model.StudioProject
import com.example.domain.model.StudioTextAlign
import com.example.domain.model.TimelineClip
import com.example.domain.model.TrackType
import com.example.domain.model.VfxConfig
import com.example.domain.model.VfxEffectType
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan
import kotlin.random.Random

/**
 * Reusable frame renderer writing directly into MediaCodec Surface Canvas.
 * Eliminates per-frame bitmap allocations for blur and reuses transform objects cleanly.
 */
class ExportFrameRenderer {

    private val scratchCamera = Camera()
    private val scratchMatrix = Matrix()
    private val mediaPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val scratchDestRect = RectF()

    fun renderProjectFrame(
        canvas: Canvas,
        width: Int,
        height: Int,
        project: StudioProject,
        playheadMs: Long,
        mediaProvider: MemorySafeMediaFrameProvider,
        fontCache: Map<String, Typeface>
    ) {
        mediaProvider.flushDeferredRecycles()
        canvas.drawColor(Color.parseColor("#0A0C12"))
        val layerStateMap = project.trackLayers.associateBy { it.trackType }

        val activeAdj = project.clips
            .filter {
                it.trackType == TrackType.ADJUSTMENT &&
                    !it.isHidden &&
                    (layerStateMap[TrackType.ADJUSTMENT]?.isVisible != false) &&
                    playheadMs in it.startMs..it.endMs
            }
            .fold(LumetriColorGrade()) { acc, c -> ColorGradingEngine.mergeAdjustmentGrades(acc, c.colorGrade) }

        val hasAdj = project.clips.any {
            it.trackType == TrackType.ADJUSTMENT && !it.isHidden && playheadMs in it.startMs..it.endMs
        }

        val visualClips = project.clips
            .filter {
                (it.trackType == TrackType.VIDEO || it.trackType == TrackType.GRAPHICS || it.trackType == TrackType.TEXT) &&
                    !it.isHidden &&
                    (layerStateMap[it.trackType]?.isVisible != false) &&
                    playheadMs in it.startMs..it.endMs
            }
            .sortedWith(
                compareByDescending<TimelineClip> { layerStateMap[it.trackType]?.order ?: it.trackType.defaultOrder }
                    .thenByDescending { it.laneIndex }
            )

        val activeShake = project.clips.firstOrNull {
            it.trackType == TrackType.VFX && !it.isHidden && (layerStateMap[TrackType.VFX]?.isVisible != false) &&
                playheadMs in it.startMs..it.endMs && it.vfxConfig?.effectType == VfxEffectType.CAMERA_SHAKE
        }?.vfxConfig
        val shakeT = (playheadMs * 0.001f) * (activeShake?.speed ?: 1f)
        val shakeDxPx = if (activeShake != null) {
            (sin(shakeT * 11.3f) * 0.6f + sin(shakeT * 23.7f) * 0.4f) * (width * 0.015f) * activeShake.intensity
        } else 0f
        val shakeDyPx = if (activeShake != null) {
            (cos(shakeT * 9.7f) * 0.65f + cos(shakeT * 19.1f) * 0.35f) * (height * 0.012f) * activeShake.intensity
        } else 0f
        val shakeRotDeg = if (activeShake != null) {
            (sin(shakeT * 7.9f) * 0.7f + cos(shakeT * 17.3f) * 0.3f) * 1.6f * activeShake.intensity
        } else 0f

        visualClips.forEach { clip ->
            val xform = KeyframeInterpolator.evaluate(clip, playheadMs)
            val trans = TransitionAndChromaEngine.evaluateTransition(clip, playheadMs)
            val mergedGrade = if (clip.trackType == TrackType.VIDEO || hasAdj) {
                if (hasAdj) ColorGradingEngine.mergeAdjustmentGrades(clip.colorGrade, activeAdj) else clip.colorGrade
            } else {
                clip.colorGrade
            }

            canvas.save()

            val applyShake = clip.trackType == TrackType.VIDEO || clip.trackType == TrackType.GRAPHICS
            val pivotOffsetX = (xform.anchorX - 0.5f) * width
            val pivotOffsetY = (xform.anchorY - 0.5f) * height
            val cx = width / 2f + (xform.posX + trans.offsetXNorm) * width + if (applyShake) shakeDxPx else 0f
            val cy = height / 2f + (xform.posY + trans.offsetYNorm) * height + if (applyShake) shakeDyPx else 0f
            canvas.translate(cx + pivotOffsetX, cy + pivotOffsetY)

            if (abs(xform.rotationX) > 0.2f || abs(xform.rotationY) > 0.2f) {
                scratchMatrix.reset()
                scratchCamera.save()
                scratchCamera.setLocation(0f, 0f, -18f)
                scratchCamera.rotateX(xform.rotationX)
                scratchCamera.rotateY(xform.rotationY)
                scratchCamera.getMatrix(scratchMatrix)
                scratchCamera.restore()
                canvas.concat(scratchMatrix)
            }

            canvas.rotate(xform.rotation + trans.rotationDeg + if (applyShake) shakeRotDeg else 0f)
            val effScale = xform.scale * trans.scaleMult
            canvas.scale(effScale, effScale)
            if (abs(xform.skewX) > 0.2f || abs(xform.skewY) > 0.2f) {
                canvas.skew(
                    tan(Math.toRadians(xform.skewX.toDouble())).toFloat(),
                    tan(Math.toRadians(xform.skewY.toDouble())).toFloat()
                )
            }
            canvas.translate(-pivotOffsetX, -pivotOffsetY)

            applyMaskToCanvas(canvas, clip, width, height)
            if (trans.irisRadiusFrac < 0.99f) {
                val irisPath = Path().apply {
                    addCircle(0f, 0f, maxOf(width, height) * 0.72f * trans.irisRadiusFrac, Path.Direction.CW)
                }
                canvas.clipPath(irisPath)
            }
            if (xform.clipWipe < 0.99f) {
                canvas.clipRect(-width / 2f, -height / 2f, -width / 2f + width * xform.clipWipe, height / 2f)
            }

            val effOpacity = (xform.opacity * trans.alphaMult).coerceIn(0f, 1f)
            mediaPaint.alpha = (effOpacity * 255).toInt().coerceIn(0, 255)
            mediaPaint.xfermode = when (clip.blendMode) {
                BlendModeOption.NORMAL -> null
                BlendModeOption.SCREEN -> PorterDuffXfermode(PorterDuff.Mode.SCREEN)
                BlendModeOption.OVERLAY -> PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
                BlendModeOption.MULTIPLY -> PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
                BlendModeOption.PLUS_ADD -> PorterDuffXfermode(PorterDuff.Mode.ADD)
            }
            mediaPaint.colorFilter = if (clip.trackType == TrackType.VIDEO || hasAdj) {
                ColorMatrixColorFilter(ColorGradingEngine.buildAndroidColorMatrix(mergedGrade))
            } else {
                null
            }

            val remappedLocalMs = clip.computeSourcePositionMs(playheadMs)
            val mediaBmp = if (clip.mediaSource?.isVideo == true) {
                mediaProvider.getVideoFrame(
                    clip = clip,
                    remappedTimeMs = remappedLocalMs,
                    chromaKeyEnabled = mergedGrade.chromaKeyEnabled,
                    chromaKeyStrength = mergedGrade.chromaKeyStrength
                )
            } else {
                mediaProvider.getImageBitmap(
                    clip = clip,
                    chromaKeyEnabled = mergedGrade.chromaKeyEnabled,
                    chromaKeyStrength = mergedGrade.chromaKeyStrength
                )
            }

            canvas.save()
            clipBorderShapeIfEnclosed(canvas, clip.borderConfig, width, height)

            mediaBmp?.takeIf { !it.isRecycled }?.let { bmp ->
                val effBlur = maxOf(
                    if (clip.trackType == TrackType.VIDEO) mergedGrade.blurRadius else 0f,
                    xform.blurRadius
                )
                scratchDestRect.set(-width / 2f, -height / 2f, width / 2f, height / 2f)
                if (effBlur > 1.2f) {
                    val downFactor = (1f / (1f + effBlur * 0.45f)).coerceIn(0.06f, 0.7f)
                    val proxy = mediaProvider.buildBlurProxy(bmp, downFactor)
                    if (!proxy.isRecycled) {
                        canvas.drawBitmap(proxy, null, scratchDestRect, mediaPaint)
                    }
                } else {
                    canvas.drawBitmap(bmp, null, scratchDestRect, mediaPaint)
                }
            }

            clip.chartConfig?.let { chart ->
                ExportGraphicsOverlays.drawComparisonChart(canvas, clip, chart, width, height, playheadMs, fontCache)
            }

            clip.investigativeConfig?.let { inv ->
                ExportGraphicsOverlays.drawInvestigativeOverlay(canvas, clip, inv, width, height, playheadMs, fontCache)
            }

            clip.mapConfig?.let { mapCfg ->
                val tf = fontCache[StudioFontOption.HIND_SILIGURI.id] ?: Typeface.DEFAULT
                val clipLocalMs = (playheadMs - clip.startMs).coerceAtLeast(0L)
                val scaledMs = if (clip.durationMs > 0L) {
                    ((clipLocalMs.toFloat() / clip.durationMs.toFloat()) * mapCfg.durationMs.coerceAtLeast(1000L)).toLong()
                } else clipLocalMs
                UniversalMapRenderer.renderFrame(
                    canvas = canvas,
                    width = width.toFloat(),
                    height = height.toFloat(),
                    config = mapCfg,
                    playheadMs = scaledMs,
                    typeface = tf,
                    isClipFrame = true
                )
            }

            clip.textConfig?.let {
                drawTextOrLowerThird(canvas, clip, width, height, playheadMs, effOpacity, xform, fontCache)
            }

            canvas.restore()

            if (clip.borderConfig.style != BorderShapeStyle.NONE) {
                drawBorderOverlay(canvas, clip.borderConfig, width, height, effOpacity)
            }

            if (xform.glowBoost > 0.05f) {
                val gPaint = Paint().apply {
                    shader = RadialGradient(
                        0f, 0f, maxOf(width, height) * 0.55f,
                        intArrayOf(((xform.glowBoost * 95).toInt().coerceIn(0, 140) shl 24) or 0xFFB300, 0x00FFB300),
                        null, Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(-width / 2f, -height / 2f, width / 2f, height / 2f, gPaint)
            }

            if (xform.glitchIntensity > 0.08f) {
                val gRand = Random((playheadMs / 40L).toInt() + clip.id.hashCode())
                val bars = (xform.glitchIntensity * 6f).toInt().coerceIn(2, 10)
                val gp = Paint()
                for (g in 0 until bars) {
                    val gy = -height / 2f + gRand.nextFloat() * height
                    val gh = 4f + gRand.nextFloat() * 14f
                    gp.color = if (g % 2 == 0) 0x7700E5FF else 0x77FF4D6D
                    canvas.drawRect(-width / 2f, gy, width / 2f, gy + gh, gp)
                }
            }

            val totalFlash = maxOf(trans.flashAlpha, xform.flashAlpha)
            if (totalFlash > 0.02f) {
                val flashCol = if (trans.isWarmFlash) {
                    Color.argb((totalFlash * 185).toInt().coerceIn(0, 255), 255, 130, 30)
                } else {
                    Color.argb((totalFlash * 175).toInt().coerceIn(0, 255), 255, 255, 255)
                }
                canvas.drawRect(-width / 2f, -height / 2f, width / 2f, height / 2f, Paint().apply { color = flashCol })
            }

            canvas.restore()
        }

        project.clips.filter {
            it.trackType == TrackType.VFX &&
                !it.isHidden &&
                (layerStateMap[TrackType.VFX]?.isVisible != false) &&
                playheadMs in it.startMs..it.endMs
        }.forEach { vfxClip ->
            vfxClip.vfxConfig?.let { drawVfxToCanvas(canvas, width, height, it, playheadMs) }
        }

        val vig = if (hasAdj) activeAdj.vignette else 0.12f
        if (vig > 0.02f) {
            val vigPaint = Paint().apply {
                shader = RadialGradient(
                    width / 2f, height / 2f, maxOf(width, height) * 0.72f,
                    intArrayOf(0x00000000, ((vig * 190).toInt().coerceIn(0, 220) shl 24)),
                    floatArrayOf(0.45f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), vigPaint)
        }
    }

    private fun buildBorderRect(border: BorderConfig, width: Int, height: Int): RectF {
        val insetX = width * border.insetPercent.coerceIn(0f, 0.25f)
        val insetY = height * border.insetPercent.coerceIn(0f, 0.25f)
        return RectF(-width / 2f + insetX, -height / 2f + insetY, width / 2f - insetX, height / 2f - insetY)
    }

    private fun buildBorderPolygonPath(style: BorderShapeStyle, rect: RectF, scaleFactor: Float): Path {
        val path = Path()
        val cx = rect.centerX()
        val cy = rect.centerY()
        val rx = rect.width() / 2f
        val ry = rect.height() / 2f
        when (style) {
            BorderShapeStyle.ROUNDED_CINEMA -> {
                val r = rect.width().coerceAtMost(rect.height()) * 0.06f + 8f * scaleFactor
                path.addRoundRect(rect, r, r, Path.Direction.CW)
            }
            BorderShapeStyle.CHAMFERED_OCTAGON -> {
                val cut = minOf(rect.width(), rect.height()) * 0.12f
                path.moveTo(rect.left + cut, rect.top)
                path.lineTo(rect.right - cut, rect.top)
                path.lineTo(rect.right, rect.top + cut)
                path.lineTo(rect.right, rect.bottom - cut)
                path.lineTo(rect.right - cut, rect.bottom)
                path.lineTo(rect.left + cut, rect.bottom)
                path.lineTo(rect.left, rect.bottom - cut)
                path.lineTo(rect.left, rect.top + cut)
                path.close()
            }
            BorderShapeStyle.DIAMOND_FRAME -> {
                path.moveTo(cx, rect.top); path.lineTo(rect.right, cy)
                path.lineTo(cx, rect.bottom); path.lineTo(rect.left, cy); path.close()
            }
            BorderShapeStyle.HEXAGON_FRAME -> {
                for (i in 0 until 6) {
                    val ang = Math.toRadians((60.0 * i) - 90.0)
                    val px = cx + (rx * 0.96f * cos(ang)).toFloat()
                    val py = cy + (ry * 0.96f * sin(ang)).toFloat()
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
            }
            BorderShapeStyle.CIRCLE_PORTRAIT -> path.addCircle(cx, cy, minOf(rx, ry) * 0.94f, Path.Direction.CW)
            else -> path.addRect(rect, Path.Direction.CW)
        }
        return path
    }

    private fun clipBorderShapeIfEnclosed(canvas: Canvas, border: BorderConfig, width: Int, height: Int) {
        if (border.style in listOf(
                BorderShapeStyle.ROUNDED_CINEMA,
                BorderShapeStyle.CHAMFERED_OCTAGON,
                BorderShapeStyle.DIAMOND_FRAME,
                BorderShapeStyle.HEXAGON_FRAME,
                BorderShapeStyle.CIRCLE_PORTRAIT
            )
        ) {
            val rect = buildBorderRect(border, width, height)
            val path = buildBorderPolygonPath(border.style, rect, width / 380f)
            canvas.clipPath(path)
        }
    }

    private fun drawBorderOverlay(canvas: Canvas, border: BorderConfig, width: Int, height: Int, opacity: Float) {
        val scaleFactor = width / 380f
        val rect = buildBorderRect(border, width, height)
        val alphaInt = (opacity * 255).toInt().coerceIn(0, 255)
        val primaryCol = (border.colorHex.toInt() and 0x00FFFFFF) or (alphaInt shl 24)
        val secondaryCol = (border.secondaryColorHex.toInt() and 0x00FFFFFF) or (alphaInt shl 24)
        val strokePx = border.thicknessDp.coerceIn(1f, 24f) * scaleFactor

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokePx
            color = primaryCol
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            if (border.glowRadius > 0.5f) {
                maskFilter = BlurMaskFilter(border.glowRadius * scaleFactor * 0.6f, BlurMaskFilter.Blur.SOLID)
            }
        }

        when (border.style) {
            BorderShapeStyle.NONE -> Unit
            BorderShapeStyle.RECT_SOLID -> canvas.drawRect(rect, strokePaint)
            BorderShapeStyle.ROUNDED_CINEMA,
            BorderShapeStyle.CHAMFERED_OCTAGON,
            BorderShapeStyle.DIAMOND_FRAME,
            BorderShapeStyle.HEXAGON_FRAME,
            BorderShapeStyle.CIRCLE_PORTRAIT -> canvas.drawPath(buildBorderPolygonPath(border.style, rect, scaleFactor), strokePaint)
            BorderShapeStyle.DOUBLE_FRAME -> {
                canvas.drawRect(rect, strokePaint)
                val innerInset = strokePx * 1.9f
                val innerRect = RectF(rect.left + innerInset, rect.top + innerInset, rect.right - innerInset, rect.bottom - innerInset)
                val innerPaint = Paint(strokePaint).apply {
                    color = secondaryCol
                    strokeWidth = (strokePx * 0.55f).coerceAtLeast(1.5f)
                }
                canvas.drawRect(innerRect, innerPaint)
            }
            BorderShapeStyle.TECH_BRACKETS -> {
                val arm = minOf(rect.width(), rect.height()) * 0.14f
                canvas.drawLine(rect.left, rect.top, rect.left + arm, rect.top, strokePaint)
                canvas.drawLine(rect.left, rect.top, rect.left, rect.top + arm, strokePaint)
                canvas.drawLine(rect.right, rect.top, rect.right - arm, rect.top, strokePaint)
                canvas.drawLine(rect.right, rect.top, rect.right, rect.top + arm, strokePaint)
                canvas.drawLine(rect.left, rect.bottom, rect.left + arm, rect.bottom, strokePaint)
                canvas.drawLine(rect.left, rect.bottom, rect.left, rect.bottom - arm, strokePaint)
                canvas.drawLine(rect.right, rect.bottom, rect.right - arm, rect.bottom, strokePaint)
                canvas.drawLine(rect.right, rect.bottom, rect.right, rect.bottom - arm, strokePaint)
            }
            BorderShapeStyle.NEON_DASHED -> {
                val dashPaint = Paint(strokePaint).apply {
                    pathEffect = DashPathEffect(floatArrayOf(strokePx * 3.5f, strokePx * 2f), 0f)
                }
                canvas.drawRoundRect(rect, border.cornerRadiusDp * scaleFactor, border.cornerRadiusDp * scaleFactor, dashPaint)
            }
        }
    }

    private fun drawVfxToCanvas(canvas: Canvas, width: Int, height: Int, vfx: VfxConfig, playheadMs: Long) {
        val w = width.toFloat()
        val h = height.toFloat()
        val intensity = vfx.intensity.coerceIn(0f, 1f)
        val tSec = (playheadMs / 1000f) * vfx.speed
        val rand = Random((playheadMs / 33L).toInt())

        when (vfx.effectType) {
            VfxEffectType.NONE, VfxEffectType.CAMERA_SHAKE -> Unit
            VfxEffectType.FILM_GRAIN -> {
                val lightPaint = Paint().apply { color = Color.argb((intensity * 52).toInt(), 255, 255, 255) }
                val darkPaint = Paint().apply { color = Color.argb((intensity * 58).toInt(), 12, 12, 16) }
                for (i in 0 until 160) {
                    canvas.drawCircle(rand.nextFloat() * w, rand.nextFloat() * h, 1.4f + (i % 2) * 0.6f, if (i % 2 == 0) lightPaint else darkPaint)
                }
                if (rand.nextFloat() < 0.35f) {
                    val sx = rand.nextFloat() * w
                    canvas.drawLine(sx, 0f, sx + 3f, h, Paint().apply { color = Color.argb((intensity * 38).toInt(), 255, 255, 255); strokeWidth = 1.5f })
                }
            }
            VfxEffectType.ANAMORPHIC_FLARE -> {
                val fy = h * (0.28f + 0.09f * sin(tSec * 1.4f))
                val fx = w * (0.5f + 0.15f * cos(tSec * 0.8f))
                val flarePaint = Paint().apply {
                    shader = LinearGradient(0f, fy, w, fy, intArrayOf(0x0000E5FF, ((intensity * 115).toInt() shl 24) or 0x00E5FF, ((intensity * 185).toInt() shl 24) or 0xFFFFFF, ((intensity * 115).toInt() shl 24) or 0x00E5FF, 0x0000E5FF), null, Shader.TileMode.CLAMP)
                }
                canvas.drawRect(0f, fy - h * 0.025f, w, fy + h * 0.025f, flarePaint)
                val corePaint = Paint().apply {
                    shader = RadialGradient(fx, fy, minOf(w, h) * 0.28f, intArrayOf(((intensity * 165).toInt() shl 24) or 0xFFFFFF, ((intensity * 90).toInt() shl 24) or 0x00E5FF, 0x0000E5FF), null, Shader.TileMode.CLAMP)
                }
                canvas.drawCircle(fx, fy, minOf(w, h) * 0.28f, corePaint)
            }
            VfxEffectType.CHROMATIC_ABERRATION -> {
                val shift = w * 0.015f * intensity
                canvas.drawRect(4f, 4f, w - 4f, h - 4f, Paint().apply { style = Paint.Style.STROKE; strokeWidth = w * 0.014f * intensity; color = Color.argb(105, 255, 35, 80) })
                canvas.drawRect(4f + shift, 4f, w - 4f + shift, h - 4f, Paint().apply { style = Paint.Style.STROKE; strokeWidth = w * 0.012f * intensity; color = Color.argb(95, 0, 229, 255) })
            }
            VfxEffectType.GLITCH_TEAR -> {
                val bars = (intensity * 10).toInt().coerceAtLeast(3)
                val p = Paint()
                for (i in 0 until bars) {
                    val y = rand.nextFloat() * h
                    val bh = 4f + rand.nextFloat() * 16f
                    p.color = if (i % 2 == 0) 0x6600E5FF else 0x66FF4D6D
                    val xOff = (rand.nextFloat() - 0.5f) * w * 0.08f * intensity
                    canvas.drawRect(xOff, y, xOff + w * (0.65f + rand.nextFloat() * 0.35f), y + bh, p)
                }
            }
            VfxEffectType.CINEMA_RAIN -> {
                canvas.drawRect(0f, 0f, w, h, Paint().apply {
                    shader = LinearGradient(0f, 0f, 0f, h, 0x00000000, Color.argb((intensity * 38).toInt(), 120, 185, 255), Shader.TileMode.CLAMP)
                })
                val p = Paint()
                for (i in 0 until 80) {
                    val isNear = i % 3 == 0
                    val rx = ((i * 97f + tSec * (if (isNear) 190f else 120f)) % w)
                    val ry = ((i * 53f + tSec * (if (isNear) 880f else 620f)) % h)
                    val len = if (isNear) 42f else 24f
                    p.color = Color.argb((intensity * (if (isNear) 150 else 85)).toInt(), 190, 228, 255)
                    p.strokeWidth = if (isNear) 3.2f else 1.8f
                    canvas.drawLine(rx, ry, rx - len * 0.28f, ry + len, p)
                }
            }
            VfxEffectType.EMBERS_SPARK -> {
                val glowP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb((intensity * 75).toInt(), 255, 110, 0) }
                val coreP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb((intensity * 220).toInt(), 255, 205, 50) }
                for (i in 0 until 45) {
                    val ex = ((i * 113f + sin(tSec * 1.8f + i) * 45f) % w + w) % w
                    val ey = h - ((i * 71f + tSec * 195f) % h)
                    val r = 3f + (i % 4) * 1.4f
                    canvas.drawCircle(ex, ey, r * 2.5f, glowP)
                    canvas.drawCircle(ex, ey, r, coreP)
                }
            }
            VfxEffectType.LIGHT_LEAK -> {
                val cx = w * (0.14f + 0.12f * sin(tSec))
                val cy = h * (0.18f + 0.08f * cos(tSec * 0.7f))
                canvas.drawRect(0f, 0f, w, h, Paint().apply {
                    shader = RadialGradient(cx, cy, maxOf(w, h) * 0.72f, intArrayOf(((intensity * 150).toInt() shl 24) or 0xFF8C00, ((intensity * 65).toInt() shl 24) or 0xFF2E63, 0x00FF7A00), null, Shader.TileMode.CLAMP)
                })
            }
            VfxEffectType.LETTERBOX_239 -> {
                val barH = h * 0.12f * intensity
                val p = Paint().apply { color = Color.BLACK }
                canvas.drawRect(0f, 0f, w, barH, p)
                canvas.drawRect(0f, h - barH, w, h, p)
            }
            VfxEffectType.VOLUMETRIC_GOD_RAYS -> {
                val ox = w * 0.12f; val oy = -h * 0.1f
                val p = Paint(Paint.ANTI_ALIAS_FLAG)
                for (idx in 0 until 5) {
                    val sweep = 0.18f + idx * 0.16f + 0.04f * sin(tSec * 0.9f + idx)
                    val rayPath = Path().apply { moveTo(ox, oy); lineTo(w * sweep, h); lineTo(w * (sweep + 0.12f), h); close() }
                    p.shader = LinearGradient(ox, 0f, w * sweep, h, ((intensity * 78).toInt() shl 24) or 0xFFE082, 0x00FFE082, Shader.TileMode.CLAMP)
                    canvas.drawPath(rayPath, p)
                }
            }
            VfxEffectType.GOLD_DUST_BOKEH -> {
                val bRand = Random(99)
                val count = (18 * intensity).toInt().coerceAtLeast(6)
                val p = Paint(Paint.ANTI_ALIAS_FLAG)
                for (idx in 0 until count) {
                    val bx = ((bRand.nextFloat() + 0.05f * sin(tSec + idx * 1.3f)) % 1f) * w
                    val by = ((bRand.nextFloat() + 0.04f * cos(tSec * 0.8f + idx)) % 1f) * h
                    val r = (w * 0.025f) + (idx % 5) * (w * 0.012f)
                    val colHex = if (idx % 2 == 0) 0xFFB300 else 0x00E5FF
                    p.shader = RadialGradient(bx, by, r, intArrayOf(((intensity * 72).toInt() shl 24) or colHex, 0x00000000), null, Shader.TileMode.CLAMP)
                    canvas.drawCircle(bx, by, r, p)
                }
            }
            VfxEffectType.DREAMY_HALATION -> {
                canvas.drawRect(0f, 0f, w, h, Paint().apply {
                    shader = RadialGradient(w * 0.5f, h * 0.45f, maxOf(w, h) * 0.7f, intArrayOf(((intensity * 95).toInt() shl 24) or 0xFFE4B5, ((intensity * 50).toInt() shl 24) or 0xFF9E80, 0x00000000), null, Shader.TileMode.CLAMP)
                })
            }
        }
    }

    private fun applyMaskToCanvas(canvas: Canvas, clip: TimelineClip, width: Int, height: Int) {
        val w = width.toFloat(); val h = height.toFloat()
        val s = clip.maskConfig.scale.coerceIn(0.2f, 1f)
        when (clip.maskConfig.shape) {
            MaskShape.NONE -> Unit
            MaskShape.CINEMA_BARS -> canvas.clipRect(-w / 2f, -h / 2f + h * (1f - s) * 0.35f, w / 2f, h / 2f - h * (1f - s) * 0.35f)
            MaskShape.LINEAR_TOP -> canvas.clipRect(-w / 2f, -h / 2f, w / 2f, -h / 2f + h * s)
            MaskShape.LINEAR_SPLIT -> canvas.clipRect(-w / 2f, -h / 2f, -w / 2f + w * s, h / 2f)
            MaskShape.RADIAL_SPOTLIGHT -> canvas.clipPath(Path().apply { addCircle(0f, 0f, minOf(w, h) * 0.48f * s, Path.Direction.CW) })
            MaskShape.FREEHAND_DIAMOND -> {
                val rx = w * 0.45f * s; val ry = h * 0.45f * s
                canvas.clipPath(Path().apply { moveTo(0f, -ry); lineTo(rx, 0f); lineTo(0f, ry); lineTo(-rx, 0f); close() })
            }
        }
    }

    private fun drawTextOrLowerThird(
        canvas: Canvas,
        clip: TimelineClip,
        width: Int,
        height: Int,
        playheadMs: Long,
        opacity: Float,
        xform: EvaluatedTransform,
        fontCache: Map<String, Typeface>
    ) {
        val cfg = clip.textConfig ?: return
        val scaleFactor = width / 380f
        val tf = cfg.customFontPath?.let { fontCache[it] } ?: fontCache[cfg.fontId] ?: Typeface.DEFAULT
        val alphaInt = (opacity * 255).toInt().coerceIn(0, 255)
        val accent = (cfg.accentColorHex.toInt() and 0x00FFFFFF) or (alphaInt shl 24)
        val mainColor = (cfg.textColorHex.toInt() and 0x00FFFFFF) or (alphaInt shl 24)
        val layoutAlign = when (cfg.textAlign) {
            StudioTextAlign.LEFT -> Layout.Alignment.ALIGN_NORMAL
            StudioTextAlign.CENTER -> Layout.Alignment.ALIGN_CENTER
            StudioTextAlign.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
        }

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
        } else cfg.text

        val textSizePx = cfg.fontSizeSp * scaleFactor
        val maxBoxW = (width * 0.84f).toInt().coerceAtLeast(100)
        val effShadowRadius = maxOf(cfg.shadowRadius, xform.shadowStrength * 14f)

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tf
            textSize = textSizePx
            color = mainColor
            letterSpacing = (cfg.letterSpacingSp + xform.textSpacingBoostSp) * 0.04f
            if (xform.blurRadius > 0.6f) {
                maskFilter = BlurMaskFilter(xform.blurRadius * scaleFactor * 0.5f, BlurMaskFilter.Blur.NORMAL)
            }
            if (effShadowRadius > 0.2f) {
                val shCol = (cfg.shadowColorHex.toInt() and 0x00FFFFFF) or (alphaInt shl 24)
                setShadowLayer(effShadowRadius * scaleFactor * 0.5f, 0f, 3f * scaleFactor, shCol)
            }
            if (cfg.useGradient) {
                val g1 = (cfg.gradientStartHex.toInt() and 0x00FFFFFF) or (alphaInt shl 24)
                val g2 = (cfg.gradientEndHex.toInt() and 0x00FFFFFF) or (alphaInt shl 24)
                shader = LinearGradient(-maxBoxW / 2f, 0f, maxBoxW / 2f, 0f, g1, g2, Shader.TileMode.CLAMP)
            }
        }

        val drawStr = visibleText.ifEmpty { " " }
        val layout = StaticLayout.Builder.obtain(drawStr, 0, drawStr.length, textPaint, maxBoxW)
            .setAlignment(layoutAlign)
            .setLineSpacing(0f, cfg.lineHeightMult.coerceIn(0.85f, 2.2f))
            .build()

        val boxW = layout.width.toFloat() + 36f * scaleFactor
        val boxH = layout.height.toFloat() + (if (cfg.subtitle.isNotBlank()) 34f else 18f) * scaleFactor

        if (cfg.lowerThirdTemplate != LowerThirdTemplate.NONE || cfg.backgroundPill) {
            val pillBgColor = (cfg.pillColorHex.toInt() and 0x00FFFFFF) or ((minOf(alphaInt, (cfg.pillColorHex ushr 24).toInt())) shl 24)
            val corner = cfg.pillCornerDp * scaleFactor
            canvas.drawRoundRect(RectF(-boxW / 2f, -boxH / 2f, boxW / 2f, boxH / 2f), corner, corner, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pillBgColor })

            if (cfg.lowerThirdTemplate == LowerThirdTemplate.DOCUMENTARY_DATE_CARD ||
                cfg.lowerThirdTemplate == LowerThirdTemplate.NEWS_LOWER_THIRD
            ) {
                canvas.drawRoundRect(RectF(-boxW / 2f, -boxH / 2f, -boxW / 2f + 6f * scaleFactor, boxH / 2f), 4f, 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent })
            }
        }

        canvas.save()
        canvas.translate(-layout.width / 2f, -layout.height / 2f + if (xform.splitCharWave > 0.05f) sin(playheadMs * 0.02f) * 4f * xform.splitCharWave else 0f)

        if (cfg.strokeWidthPx > 0.2f) {
            val strokePaint = TextPaint(textPaint).apply {
                shader = null; clearShadowLayer(); style = Paint.Style.STROKE
                strokeWidth = cfg.strokeWidthPx * scaleFactor * 0.9f
                strokeJoin = Paint.Join.ROUND
                color = (cfg.strokeColorHex.toInt() and 0x00FFFFFF) or (alphaInt shl 24)
            }
            StaticLayout.Builder.obtain(drawStr, 0, drawStr.length, strokePaint, maxBoxW)
                .setAlignment(layoutAlign)
                .setLineSpacing(0f, cfg.lineHeightMult.coerceIn(0.85f, 2.2f))
                .build()
                .draw(canvas)
        }

        layout.draw(canvas)
        canvas.restore()

        if (xform.underlineProgress > 0.02f) {
            val startX = -boxW / 2f + 12f * scaleFactor
            val endX = startX + (boxW - 24f * scaleFactor) * xform.underlineProgress
            canvas.drawRoundRect(RectF(startX, layout.height / 2f + 4f * scaleFactor, endX, layout.height / 2f + 8f * scaleFactor), 3f, 3f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent })
        }

        if (cfg.subtitle.isNotBlank()) {
            canvas.drawText(cfg.subtitle, 0f, boxH / 2f - 10f * scaleFactor, TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = tf; textSize = (cfg.fontSizeSp * 0.54f) * scaleFactor; color = accent; textAlign = Paint.Align.CENTER
            })
        }

        if (cfg.karaokeHighlight && clip.durationMs > 0L) {
            val progress = ((playheadMs - clip.startMs).toFloat() / clip.durationMs.toFloat()).coerceIn(0f, 1f)
            val startX = -boxW / 2f + 12f * scaleFactor
            val endX = startX + (boxW - 24f * scaleFactor) * progress
            canvas.drawRoundRect(RectF(startX, boxH / 2f - 5f * scaleFactor, endX, boxH / 2f - 2f * scaleFactor), 3f, 3f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent })
        }
    }
}
