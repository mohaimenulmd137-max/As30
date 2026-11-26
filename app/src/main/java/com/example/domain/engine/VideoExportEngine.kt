package com.example.domain.engine

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.domain.model.AspectRatioPreset
import com.example.domain.model.BlendModeOption
import com.example.domain.model.ComparisonChartConfig
import com.example.domain.model.ComparisonGridConfig
import com.example.domain.model.ExportFps
import com.example.domain.model.GeometricShape
import com.example.domain.model.InvestigativeDocConfig
import com.example.domain.model.InvestigativeToolType
import com.example.domain.model.LowerThirdTemplate
import com.example.domain.model.LumetriColorGrade
import com.example.domain.model.MaskShape
import com.example.domain.model.PreviewResolution
import com.example.domain.model.StudioFontOption
import com.example.domain.model.StudioProject
import com.example.domain.model.StudioTextAlign
import com.example.domain.model.TimelineClip
import com.example.domain.model.TrackType
import com.example.domain.model.VfxConfig
import com.example.domain.model.VfxEffectType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

data class ExportResult(
    val success: Boolean,
    val savedLocationLabel: String,
    val outputUri: Uri? = null,
    val errorMessage: String? = null
)

object VideoExportEngine {

    suspend fun exportProjectToMp4(
        context: Context,
        project: StudioProject,
        resolution: PreviewResolution,
        fpsOption: ExportFps,
        onProgress: (Float, String) -> Unit
    ): ExportResult = withContext(Dispatchers.Default) {
        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        val tempFile = File(context.cacheDir, "chitraflow_export_${System.currentTimeMillis()}.mp4")

        try {
            val (width, height) = computeExportDimensions(project, resolution)
            val fps = fpsOption.fps
            val totalDurationMs = project.totalDurationMs.coerceIn(1000L, 120_000L)
            val totalFrames = ((totalDurationMs / 1000f) * fps).toInt().coerceAtLeast(30)

            onProgress(0.04f, "Mixing Voiceover, Auto-Ducked BGM & SFX...")
            val mixedPcm = AudioExportMixer.mixProjectAudioPcm(context, project)
            val encodedAudio = AudioExportMixer.encodePcmToAacFrames(mixedPcm)

            onProgress(0.08f, "Preparing hardware encoder ($width×$height @ ${fps}fps)...")

            val bitrate = when (resolution) {
                PreviewResolution.P720 -> 6_000_000
                PreviewResolution.P1080 -> 12_000_000
                PreviewResolution.UHD4K -> 24_000_000
            }

            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val inputSurface = codec.createInputSurface()
            codec.start()

            val activeMuxer = MediaMuxer(tempFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = activeMuxer
            var videoTrackIndex = -1
            var audioTrackIndex = -1
            var muxerStarted = false

            val bitmapCache = preloadClipBitmaps(context, project, width, height)
            val fontCache = preloadTypefaces(context, project)

            val bufferInfo = MediaCodec.BufferInfo()
            val frameDurationUs = 1_000_000L / fps

            for (frameIdx in 0 until totalFrames) {
                val playheadMs = ((frameIdx.toFloat() / fps) * 1000f).toLong()
                val canvas = inputSurface.lockCanvas(null)
                try {
                    renderProjectFrame(
                        canvas = canvas,
                        width = width,
                        height = height,
                        project = project,
                        playheadMs = playheadMs,
                        bitmapCache = bitmapCache,
                        fontCache = fontCache
                    )
                } finally {
                    inputSurface.unlockCanvasAndPost(canvas)
                }

                drainEncoder(codec, activeMuxer, bufferInfo, endOfStream = false,
                    onFormatChanged = { newFormat ->
                        videoTrackIndex = activeMuxer.addTrack(newFormat)
                        if (encodedAudio != null) {
                            audioTrackIndex = activeMuxer.addTrack(encodedAudio.format)
                        }
                        activeMuxer.start()
                        muxerStarted = true
                    },
                    isMuxerStarted = { muxerStarted },
                    getTrackIndex = { videoTrackIndex },
                    presentationTimeUsOverride = frameIdx * frameDurationUs
                )

                if (frameIdx % 4 == 0 || frameIdx == totalFrames - 1) {
                    val pct = 0.1f + 0.78f * (frameIdx.toFloat() / totalFrames.toFloat())
                    onProgress(pct, "Rendering frame ${frameIdx + 1}/$totalFrames...")
                }
            }

            codec.signalEndOfInputStream()
            drainEncoder(codec, activeMuxer, bufferInfo, endOfStream = true,
                onFormatChanged = { newFormat ->
                    if (!muxerStarted) {
                        videoTrackIndex = activeMuxer.addTrack(newFormat)
                        if (encodedAudio != null) {
                            audioTrackIndex = activeMuxer.addTrack(encodedAudio.format)
                        }
                        activeMuxer.start()
                        muxerStarted = true
                    }
                },
                isMuxerStarted = { muxerStarted },
                getTrackIndex = { videoTrackIndex },
                presentationTimeUsOverride = null
            )

            // Write all mixed AAC audio frames into the MP4 audio track
            if (muxerStarted && encodedAudio != null && audioTrackIndex >= 0) {
                onProgress(0.91f, "Muxing Auto-Ducked Audio Track...")
                val audioBufInfo = MediaCodec.BufferInfo()
                encodedAudio.frames.forEach { aFrame ->
                    val bb = ByteBuffer.wrap(aFrame.data)
                    audioBufInfo.set(0, aFrame.data.size, aFrame.presentationTimeUs, aFrame.flags)
                    activeMuxer.writeSampleData(audioTrackIndex, bb, audioBufInfo)
                }
            }

            bitmapCache.values.forEach { it.recycle() }
            runCatching { codec.stop() }
            runCatching { codec.release() }
            codec = null
            if (muxerStarted) runCatching { activeMuxer.stop() }
            runCatching { activeMuxer.release() }
            muxer = null

            onProgress(0.95f, "Saving MP4 to Phone Gallery...")
            val safeName = project.name.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "ChitraFlow_Video" }
            val fileName = "${safeName}_${resolution.label}_${System.currentTimeMillis()}.mp4"
            val savedUri = saveMp4ToGallery(context, tempFile, fileName)
            tempFile.delete()

            onProgress(1.0f, "Export Complete!")
            ExportResult(
                success = true,
                savedLocationLabel = "Movies/ChitraFlow Studio/$fileName",
                outputUri = savedUri
            )
        } catch (t: Throwable) {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { muxer?.release() }
            tempFile.delete()
            ExportResult(
                success = false,
                savedLocationLabel = "",
                errorMessage = t.message ?: "Hardware encoder unavailable on this device"
            )
        }
    }

    private fun computeExportDimensions(project: StudioProject, resolution: PreviewResolution): Pair<Int, Int> {
        val shortEdge = when (resolution) {
            PreviewResolution.P720 -> 720
            PreviewResolution.P1080 -> 1080
            PreviewResolution.UHD4K -> 1440
        }
        val (w, h) = when (project.aspectRatio) {
            AspectRatioPreset.PORTRAIT_9_16 -> shortEdge to (shortEdge * 16 / 9)
            AspectRatioPreset.LANDSCAPE_16_9 -> (shortEdge * 16 / 9) to shortEdge
            AspectRatioPreset.SQUARE_1_1 -> shortEdge to shortEdge
        }
        return (w / 16 * 16) to (h / 16 * 16)
    }

    private fun preloadClipBitmaps(
        context: Context,
        project: StudioProject,
        targetW: Int,
        targetH: Int
    ): Map<String, Bitmap> {
        val map = mutableMapOf<String, Bitmap>()
        fun loadSource(key: String, uriStr: String?, drawableName: String?, isVideo: Boolean) {
            val bmp: Bitmap? = when {
                uriStr != null -> runCatching {
                    val uri = Uri.parse(uriStr)
                    if (isVideo) {
                        val mmr = MediaMetadataRetriever()
                        mmr.setDataSource(context, uri)
                        val frame = mmr.getFrameAtTime(0L)
                        mmr.release()
                        frame
                    } else {
                        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                    }
                }.getOrNull()
                drawableName != null -> BitmapFactory.decodeResource(context.resources, resolveDrawableId(drawableName))
                else -> null
            }
            if (bmp != null) {
                val scaled = Bitmap.createScaledBitmap(bmp, targetW, targetH, true)
                if (scaled !== bmp) bmp.recycle()
                map[key] = scaled
            }
        }
        project.clips.forEach { clip ->
            clip.mediaSource?.let { src ->
                loadSource(clip.id, src.uriString, src.drawableName, src.isVideo)
            }
            clip.comparisonGrid?.slots?.forEach { slot ->
                slot.mediaSource?.let { sSrc ->
                    loadSource("${clip.id}_${slot.id}", sSrc.uriString, sSrc.drawableName, sSrc.isVideo)
                }
            }
        }
        return map
    }

    private fun preloadTypefaces(context: Context, project: StudioProject): Map<String, Typeface> {
        val map = mutableMapOf<String, Typeface>()
        StudioFontOption.entries.forEach { opt ->
            val tf = runCatching { ResourcesCompat.getFont(context, opt.fontResId) }.getOrNull() ?: Typeface.DEFAULT
            map[opt.id] = tf
        }
        project.clips.forEach { clip ->
            val customPath = clip.textConfig?.customFontPath
            if (!customPath.isNullOrBlank()) {
                map[customPath] = CustomFontManager.resolveTypeface(context, clip.textConfig.fontId, customPath)
            }
        }
        return map
    }

    private fun renderProjectFrame(
        canvas: Canvas,
        width: Int,
        height: Int,
        project: StudioProject,
        playheadMs: Long,
        bitmapCache: Map<String, Bitmap>,
        fontCache: Map<String, Typeface>
    ) {
        canvas.drawColor(android.graphics.Color.parseColor("#0A0C12"))
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
            .sortedByDescending { layerStateMap[it.trackType]?.order ?: it.trackType.defaultOrder }

        visualClips.forEach { clip ->
            val xform = KeyframeInterpolator.evaluate(clip, playheadMs)
            canvas.save()

            val cx = width / 2f + xform.posX * width
            val cy = height / 2f + xform.posY * height
            canvas.translate(cx, cy)
            canvas.rotate(xform.rotation)
            canvas.scale(xform.scale, xform.scale)

            applyMaskToCanvas(canvas, clip, width, height)

            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                alpha = (xform.opacity * 255).toInt().coerceIn(0, 255)
                xfermode = when (clip.blendMode) {
                    BlendModeOption.NORMAL -> null
                    BlendModeOption.SCREEN -> PorterDuffXfermode(PorterDuff.Mode.SCREEN)
                    BlendModeOption.OVERLAY -> PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
                    BlendModeOption.MULTIPLY -> PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
                    BlendModeOption.PLUS_ADD -> PorterDuffXfermode(PorterDuff.Mode.ADD)
                }
                if (clip.trackType == TrackType.VIDEO || hasAdj) {
                    val merged = if (hasAdj) ColorGradingEngine.mergeAdjustmentGrades(clip.colorGrade, activeAdj) else clip.colorGrade
                    colorFilter = ColorMatrixColorFilter(ColorGradingEngine.buildAndroidColorMatrix(merged))
                }
            }

            bitmapCache[clip.id]?.let { bmp ->
                canvas.drawBitmap(bmp, null, RectF(-width / 2f, -height / 2f, width / 2f, height / 2f), paint)
            }

            clip.comparisonGrid?.let { grid ->
                drawComparisonGrid(canvas, clip, grid, width, height, bitmapCache, fontCache)
            }

            clip.chartConfig?.let { chart ->
                drawComparisonChart(canvas, clip, chart, width, height, playheadMs, fontCache)
            }

            clip.investigativeConfig?.let { inv ->
                drawInvestigativeOverlay(canvas, clip, inv, width, height, playheadMs, fontCache)
            }

            clip.textConfig?.let {
                drawTextOrLowerThird(canvas, clip, width, height, playheadMs, xform.opacity, fontCache)
            }

            canvas.restore()
        }

        // Hollywood VFX Layer pass
        val activeVfx = project.clips.filter {
            it.trackType == TrackType.VFX &&
                !it.isHidden &&
                (layerStateMap[TrackType.VFX]?.isVisible != false) &&
                playheadMs in it.startMs..it.endMs
        }
        activeVfx.forEach { vfxClip ->
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

    private fun drawComparisonGrid(
        canvas: Canvas,
        clip: TimelineClip,
        grid: ComparisonGridConfig,
        width: Int,
        height: Int,
        bitmapCache: Map<String, Bitmap>,
        fontCache: Map<String, Typeface>
    ) {
        val slots = grid.slots.take(grid.slotCount.coerceIn(2, 4))
        if (slots.isEmpty()) return
        val cols = if (slots.size <= 2) slots.size else 2
        val rows = if (slots.size <= 2) 1 else 2
        val pad = width * 0.06f
        val cellW = (width - pad * (cols + 1)) / cols
        val cellH = (height * 0.72f - pad * (rows + 1)) / rows
        val startY = -height * 0.34f
        val tf = fontCache[StudioFontOption.HIND_SILIGURI.id] ?: Typeface.DEFAULT

        slots.forEachIndexed { idx, slot ->
            val r = idx / cols
            val c = idx % cols
            val left = -width / 2f + pad + c * (cellW + pad)
            val top = startY + pad + r * (cellH + pad)
            val rect = RectF(left, top, left + cellW, top + cellH)
            val path = buildGeometricAndroidPath(slot.shape, rect)

            canvas.save()
            canvas.clipPath(path)
            val bmp = bitmapCache["${clip.id}_${slot.id}"]
            if (bmp != null) {
                canvas.drawBitmap(bmp, null, rect, Paint(Paint.ANTI_ALIAS_FLAG))
            } else {
                canvas.drawRect(rect, Paint().apply { color = android.graphics.Color.parseColor("#1A2030") })
            }
            canvas.restore()

            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = width * 0.006f
                color = slot.borderColorHex.toInt()
            }
            canvas.drawPath(path, strokePaint)

            val capPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = tf
                textSize = width * 0.028f
                color = android.graphics.Color.WHITE
                textAlign = Paint.Align.CENTER
                setShadowLayer(6f, 0f, 2f, android.graphics.Color.BLACK)
            }
            canvas.drawText(slot.caption, rect.centerX(), rect.bottom + width * 0.036f, capPaint)
        }
    }

    private fun buildGeometricAndroidPath(shape: GeometricShape, rect: RectF): Path {
        val path = Path()
        val cx = rect.centerX()
        val cy = rect.centerY()
        val rx = rect.width() / 2f
        val ry = rect.height() / 2f
        when (shape) {
            GeometricShape.ROUNDED_RECT -> path.addRoundRect(rect, rx * 0.18f, ry * 0.18f, Path.Direction.CW)
            GeometricShape.CIRCLE -> path.addCircle(cx, cy, minOf(rx, ry), Path.Direction.CW)
            GeometricShape.DIAMOND -> {
                path.moveTo(cx, rect.top)
                path.lineTo(rect.right, cy)
                path.lineTo(cx, rect.bottom)
                path.lineTo(rect.left, cy)
                path.close()
            }
            GeometricShape.TRIANGLE -> {
                path.moveTo(cx, rect.top)
                path.lineTo(rect.right, rect.bottom)
                path.lineTo(rect.left, rect.bottom)
                path.close()
            }
            GeometricShape.HEXAGON -> {
                for (i in 0 until 6) {
                    val ang = Math.toRadians((60.0 * i) - 30.0)
                    val px = cx + (rx * 0.96f * cos(ang)).toFloat()
                    val py = cy + (ry * 0.96f * sin(ang)).toFloat()
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
            }
            GeometricShape.SHIELD -> {
                path.moveTo(rect.left + rx * 0.15f, rect.top)
                path.lineTo(rect.right - rx * 0.15f, rect.top)
                path.lineTo(rect.right, cy + ry * 0.2f)
                path.lineTo(cx, rect.bottom)
                path.lineTo(rect.left, cy + ry * 0.2f)
                path.close()
            }
            GeometricShape.STAR -> {
                for (i in 0 until 10) {
                    val radX = if (i % 2 == 0) rx else rx * 0.45f
                    val radY = if (i % 2 == 0) ry else ry * 0.45f
                    val ang = Math.toRadians((36.0 * i) - 90.0)
                    val px = cx + (radX * cos(ang)).toFloat()
                    val py = cy + (radY * sin(ang)).toFloat()
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
            }
            GeometricShape.DIAGONAL_SLASH -> {
                path.moveTo(rect.left + rx * 0.25f, rect.top)
                path.lineTo(rect.right, rect.top)
                path.lineTo(rect.right - rx * 0.25f, rect.bottom)
                path.lineTo(rect.left, rect.bottom)
                path.close()
            }
        }
        return path
    }

    private fun drawComparisonChart(
        canvas: Canvas,
        clip: TimelineClip,
        chart: ComparisonChartConfig,
        width: Int,
        height: Int,
        playheadMs: Long,
        fontCache: Map<String, Typeface>
    ) {
        val activeBars = chart.bars.take(chart.barCount.coerceIn(2, 60)).let {
            if (chart.sortDescending) it.sortedByDescending { b -> b.value } else it
        }
        if (activeBars.isEmpty()) return

        val progress = if (chart.animateGrowth && clip.durationMs > 0L) {
            ((playheadMs - clip.startMs).toFloat() / (clip.durationMs * 0.75f).coerceAtLeast(1f)).coerceIn(0.08f, 1f)
        } else 1f
        val maxVal = activeBars.maxOfOrNull { it.value }?.coerceAtLeast(1f) ?: 100f
        val tf = fontCache[StudioFontOption.HIND_SILIGURI.id] ?: Typeface.DEFAULT

        val boxRect = RectF(-width * 0.42f, -height * 0.36f, width * 0.42f, height * 0.36f)
        canvas.drawRoundRect(
            boxRect,
            width * 0.025f,
            width * 0.025f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = (0xD80E1320).toInt() }
        )

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tf
            textSize = width * 0.032f
            color = android.graphics.Color.WHITE
        }
        canvas.drawText(chart.title, boxRect.left + width * 0.03f, boxRect.top + width * 0.055f, titlePaint)

        val plotTop = boxRect.top + width * 0.085f
        val plotBottom = boxRect.bottom - width * 0.03f
        val plotLeft = boxRect.left + width * 0.03f
        val plotRight = boxRect.right - width * 0.03f

        if (chart.isHorizontal) {
            val rowH = (plotBottom - plotTop) / activeBars.size.toFloat()
            val barH = (rowH * 0.72f).coerceAtLeast(2f)
            val labelW = if (activeBars.size <= 24) (plotRight - plotLeft) * 0.24f else 0f
            val maxBarW = (plotRight - plotLeft - labelW - width * 0.04f).coerceAtLeast(20f)
            val lblPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = tf
                textSize = minOf(rowH * 0.65f, width * 0.024f)
                color = android.graphics.Color.LTGRAY
            }

            activeBars.forEachIndexed { i, bar ->
                val y = plotTop + i * rowH
                if (labelW > 0f) {
                    canvas.drawText(bar.label.take(10), plotLeft, y + barH * 0.85f, lblPaint)
                }
                val frac = (bar.value / maxVal) * progress
                val barStart = plotLeft + labelW
                val barRect = RectF(barStart, y, barStart + maxBarW * frac, y + barH)
                canvas.drawRoundRect(barRect, 3f, 3f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bar.colorHex.toInt() })
            }
        } else {
            val colW = (plotRight - plotLeft) / activeBars.size.toFloat()
            val barW = (colW * 0.74f).coerceAtLeast(2f)
            val maxBarH = (plotBottom - plotTop) * 0.88f
            activeBars.forEachIndexed { i, bar ->
                val x = plotLeft + i * colW
                val frac = (bar.value / maxVal) * progress
                val barH = maxBarH * frac
                val barRect = RectF(x, plotBottom - barH, x + barW, plotBottom)
                canvas.drawRoundRect(barRect, 3f, 3f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bar.colorHex.toInt() })
            }
        }
    }

    private fun drawInvestigativeOverlay(
        canvas: Canvas,
        clip: TimelineClip,
        inv: InvestigativeDocConfig,
        width: Int,
        height: Int,
        playheadMs: Long,
        fontCache: Map<String, Typeface>
    ) {
        val progress = if (clip.durationMs > 0L) ((playheadMs - clip.startMs).toFloat() / clip.durationMs).coerceIn(0f, 1f) else 1f
        val tf = fontCache[StudioFontOption.HIND_SILIGURI.id] ?: Typeface.DEFAULT
        val accent = inv.accentHex.toInt()

        when (inv.toolType) {
            InvestigativeToolType.NONE -> Unit
            InvestigativeToolType.EVIDENCE_HIGHLIGHTER, InvestigativeToolType.NEWSPAPER_LOUPE -> {
                val card = RectF(-width * 0.36f, -height * 0.18f, width * 0.36f, height * 0.18f)
                canvas.drawRoundRect(card, 18f, 18f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.parseColor("#F5F1E6") })
                val highlightW = (card.width() * 0.84f) * minOf(1f, progress * 1.4f)
                canvas.drawRect(
                    card.left + card.width() * 0.08f,
                    card.centerY() - height * 0.02f,
                    card.left + card.width() * 0.08f + highlightW,
                    card.centerY() + height * 0.05f,
                    Paint().apply { color = (0x88FFB300).toInt() }
                )
                val headPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = tf; textSize = width * 0.032f; color = android.graphics.Color.BLACK
                }
                canvas.drawText(inv.headline, card.left + card.width() * 0.08f, card.top + height * 0.08f, headPaint)
                val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = tf; textSize = width * 0.023f; color = android.graphics.Color.DKGRAY
                }
                canvas.drawText(inv.bodyText.take(44), card.left + card.width() * 0.08f, card.centerY() + height * 0.03f, bodyPaint)
                if (inv.toolType == InvestigativeToolType.NEWSPAPER_LOUPE) {
                    val lx = card.left + card.width() * (0.25f + 0.5f * progress)
                    val ly = card.centerY()
                    canvas.drawCircle(lx, ly, width * 0.085f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE; strokeWidth = width * 0.008f; color = accent
                    })
                }
            }
            InvestigativeToolType.REDACTION_CENSOR -> {
                val rect = RectF(-width * 0.28f, -height * 0.06f, width * 0.28f, height * 0.06f)
                canvas.drawRect(rect, Paint().apply { color = android.graphics.Color.BLACK })
            }
            InvestigativeToolType.MAP_RADAR_ROUTE -> {
                val box = RectF(-width * 0.4f, -height * 0.28f, width * 0.4f, height * 0.28f)
                canvas.drawRoundRect(box, 18f, 18f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = (0xDD0B121F).toInt() })
                val ax = -width * 0.22f; val ay = height * 0.08f
                val bx = width * 0.22f; val by = -height * 0.08f
                val routePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE; strokeWidth = width * 0.006f; color = accent
                    pathEffect = DashPathEffect(floatArrayOf(18f, 10f), 0f)
                }
                val curX = ax + (bx - ax) * minOf(1f, progress * 1.2f)
                val curY = ay + (by - ay) * minOf(1f, progress * 1.2f)
                canvas.drawLine(ax, ay, curX, curY, routePaint)
                canvas.drawCircle(ax, ay, width * 0.014f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent })
                canvas.drawCircle(curX, curY, width * 0.018f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.CYAN })
                val lblP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = tf; textSize = width * 0.025f; color = android.graphics.Color.WHITE }
                canvas.drawText(inv.pointALabel, ax, ay + width * 0.04f, lblP)
                canvas.drawText(inv.pointBLabel, bx - width * 0.06f, by - width * 0.03f, lblP)
            }
            InvestigativeToolType.STAT_YEAR_COUNTER -> {
                val currentVal = (inv.counterStart + (inv.counterEnd - inv.counterStart) * progress).toInt()
                val text = "${inv.counterPrefix}$currentVal${inv.counterSuffix}"
                val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = tf; textSize = width * 0.085f; color = accent; textAlign = Paint.Align.CENTER
                }
                canvas.drawText(text, 0f, 0f, p)
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
            VfxEffectType.NONE -> Unit
            VfxEffectType.FILM_GRAIN -> {
                val grainPaint = Paint().apply { color = android.graphics.Color.argb((intensity * 55).toInt(), 255, 255, 255) }
                for (i in 0 until 120) {
                    canvas.drawCircle(rand.nextFloat() * w, rand.nextFloat() * h, 1.5f, grainPaint)
                }
            }
            VfxEffectType.ANAMORPHIC_FLARE -> {
                val fy = h * (0.28f + 0.08f * sin(tSec))
                val flarePaint = Paint().apply {
                    shader = LinearGradient(0f, fy, w, fy, intArrayOf(0x0000E5FF, ((intensity * 170).toInt() shl 24) or 0x00E5FF, 0x0000E5FF), null, Shader.TileMode.CLAMP)
                }
                canvas.drawRect(0f, fy - h * 0.025f, w, fy + h * 0.025f, flarePaint)
            }
            VfxEffectType.CHROMATIC_ABERRATION -> {
                val p = Paint().apply {
                    style = Paint.Style.STROKE; strokeWidth = w * 0.015f * intensity
                    color = android.graphics.Color.argb(95, 255, 40, 90)
                }
                canvas.drawRect(4f, 4f, w - 4f, h - 4f, p)
            }
            VfxEffectType.GLITCH_TEAR -> {
                val bars = (intensity * 10).toInt().coerceAtLeast(2)
                for (i in 0 until bars) {
                    val y = rand.nextFloat() * h
                    val bh = 4f + rand.nextFloat() * 14f
                    val color = if (i % 2 == 0) 0x6600E5FF else 0x66FF4D6D
                    canvas.drawRect(0f, y, w, y + bh, Paint().apply { this.color = color })
                }
            }
            VfxEffectType.CINEMA_RAIN -> {
                val p = Paint().apply { color = android.graphics.Color.argb((intensity * 130).toInt(), 180, 225, 255); strokeWidth = 2.5f }
                for (i in 0 until 75) {
                    val rx = ((i * 97f + tSec * 140f) % w)
                    val ry = ((i * 53f + tSec * 750f) % h)
                    canvas.drawLine(rx, ry, rx - 10f, ry + 32f, p)
                }
            }
            VfxEffectType.EMBERS_SPARK -> {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.argb((intensity * 200).toInt(), 255, 165, 30) }
                for (i in 0 until 45) {
                    val ex = ((i * 113f + sin(tSec + i) * 40f) % w + w) % w
                    val ey = h - ((i * 71f + tSec * 180f) % h)
                    canvas.drawCircle(ex, ey, 3f + (i % 4), p)
                }
            }
            VfxEffectType.CAMERA_SHAKE -> Unit
            VfxEffectType.LIGHT_LEAK -> {
                val p = Paint().apply {
                    shader = RadialGradient(w * 0.1f, h * 0.2f, w * 0.65f, intArrayOf(((intensity * 140).toInt() shl 24) or 0xFF7A00, 0x00FF7A00), null, Shader.TileMode.CLAMP)
                }
                canvas.drawRect(0f, 0f, w, h, p)
            }
            VfxEffectType.LETTERBOX_239 -> {
                val barH = h * 0.12f * intensity
                val p = Paint().apply { color = android.graphics.Color.BLACK }
                canvas.drawRect(0f, 0f, w, barH, p)
                canvas.drawRect(0f, h - barH, w, h, p)
            }
        }
    }

    private fun applyMaskToCanvas(canvas: Canvas, clip: TimelineClip, width: Int, height: Int) {
        val w = width.toFloat()
        val h = height.toFloat()
        val s = clip.maskConfig.scale.coerceIn(0.2f, 1f)
        when (clip.maskConfig.shape) {
            MaskShape.NONE -> Unit
            MaskShape.CINEMA_BARS -> {
                val bar = h * (1f - s) * 0.35f
                canvas.clipRect(-w / 2f, -h / 2f + bar, w / 2f, h / 2f - bar)
            }
            MaskShape.LINEAR_TOP -> canvas.clipRect(-w / 2f, -h / 2f, w / 2f, -h / 2f + h * s)
            MaskShape.LINEAR_SPLIT -> canvas.clipRect(-w / 2f, -h / 2f, -w / 2f + w * s, h / 2f)
            MaskShape.RADIAL_SPOTLIGHT -> {
                val path = Path().apply { addCircle(0f, 0f, minOf(w, h) * 0.48f * s, Path.Direction.CW) }
                canvas.clipPath(path)
            }
            MaskShape.FREEHAND_DIAMOND -> {
                val rx = w * 0.45f * s
                val ry = h * 0.45f * s
                val path = Path().apply {
                    moveTo(0f, -ry); lineTo(rx, 0f); lineTo(0f, ry); lineTo(-rx, 0f); close()
                }
                canvas.clipPath(path)
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

        val textSizePx = cfg.fontSizeSp * scaleFactor
        val maxBoxW = (width * 0.84f).toInt().coerceAtLeast(100)

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tf
            textSize = textSizePx
            color = mainColor
            letterSpacing = cfg.letterSpacingSp * 0.04f
            if (cfg.shadowRadius > 0.2f) {
                val shCol = (cfg.shadowColorHex.toInt() and 0x00FFFFFF) or (alphaInt shl 24)
                setShadowLayer(cfg.shadowRadius * scaleFactor * 0.5f, 0f, 3f * scaleFactor, shCol)
            }
            if (cfg.useGradient) {
                val g1 = (cfg.gradientStartHex.toInt() and 0x00FFFFFF) or (alphaInt shl 24)
                val g2 = (cfg.gradientEndHex.toInt() and 0x00FFFFFF) or (alphaInt shl 24)
                shader = LinearGradient(-maxBoxW / 2f, 0f, maxBoxW / 2f, 0f, g1, g2, Shader.TileMode.CLAMP)
            }
        }

        val layout = StaticLayout.Builder.obtain(cfg.text, 0, cfg.text.length, textPaint, maxBoxW)
            .setAlignment(layoutAlign)
            .setLineSpacing(0f, cfg.lineHeightMult.coerceIn(0.85f, 2.2f))
            .build()

        val boxW = layout.width.toFloat() + 36f * scaleFactor
        val boxH = layout.height.toFloat() + (if (cfg.subtitle.isNotBlank()) 34f else 18f) * scaleFactor

        if (cfg.lowerThirdTemplate != LowerThirdTemplate.NONE || cfg.backgroundPill) {
            val pillBgColor = (cfg.pillColorHex.toInt() and 0x00FFFFFF) or ((minOf(alphaInt, (cfg.pillColorHex ushr 24).toInt())) shl 24)
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pillBgColor }
            val corner = cfg.pillCornerDp * scaleFactor
            val rect = RectF(-boxW / 2f, -boxH / 2f, boxW / 2f, boxH / 2f)
            canvas.drawRoundRect(rect, corner, corner, bgPaint)

            if (cfg.lowerThirdTemplate == LowerThirdTemplate.DOCUMENTARY_DATE_CARD ||
                cfg.lowerThirdTemplate == LowerThirdTemplate.NEWS_LOWER_THIRD
            ) {
                val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
                canvas.drawRoundRect(
                    RectF(-boxW / 2f, -boxH / 2f, -boxW / 2f + 6f * scaleFactor, boxH / 2f),
                    4f, 4f, barPaint
                )
            }
        }

        canvas.save()
        canvas.translate(-layout.width / 2f, -layout.height / 2f)

        // Optional Stroke Outline pass drawn behind main fill
        if (cfg.strokeWidthPx > 0.2f) {
            val strokePaint = TextPaint(textPaint).apply {
                shader = null
                clearShadowLayer()
                style = Paint.Style.STROKE
                strokeWidth = cfg.strokeWidthPx * scaleFactor * 0.9f
                strokeJoin = Paint.Join.ROUND
                color = (cfg.strokeColorHex.toInt() and 0x00FFFFFF) or (alphaInt shl 24)
            }
            StaticLayout.Builder.obtain(cfg.text, 0, cfg.text.length, strokePaint, maxBoxW)
                .setAlignment(layoutAlign)
                .setLineSpacing(0f, cfg.lineHeightMult.coerceIn(0.85f, 2.2f))
                .build()
                .draw(canvas)
        }

        layout.draw(canvas)
        canvas.restore()

        if (cfg.subtitle.isNotBlank()) {
            val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = tf
                textSize = (cfg.fontSizeSp * 0.54f) * scaleFactor
                color = accent
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(cfg.subtitle, 0f, boxH / 2f - 10f * scaleFactor, subPaint)
        }

        if (cfg.karaokeHighlight && clip.durationMs > 0L) {
            val progress = ((playheadMs - clip.startMs).toFloat() / clip.durationMs.toFloat()).coerceIn(0f, 1f)
            val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
            val startX = -boxW / 2f + 12f * scaleFactor
            val endX = startX + (boxW - 24f * scaleFactor) * progress
            canvas.drawRoundRect(
                RectF(startX, boxH / 2f - 5f * scaleFactor, endX, boxH / 2f - 2f * scaleFactor),
                3f, 3f, barPaint
            )
        }
    }

    private fun drainEncoder(
        codec: MediaCodec,
        muxer: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        endOfStream: Boolean,
        onFormatChanged: (MediaFormat) -> Unit,
        isMuxerStarted: () -> Boolean,
        getTrackIndex: () -> Int,
        presentationTimeUsOverride: Long?
    ) {
        val timeoutUs = if (endOfStream) 10_000L else 0L
        while (true) {
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
            when {
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> break
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> onFormatChanged(codec.outputFormat)
                outputIndex >= 0 -> {
                    val encodedData = codec.getOutputBuffer(outputIndex) ?: continue
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        bufferInfo.size = 0
                    }
                    if (bufferInfo.size != 0 && isMuxerStarted()) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        if (presentationTimeUsOverride != null) {
                            bufferInfo.presentationTimeUs = presentationTimeUsOverride
                        }
                        muxer.writeSampleData(getTrackIndex(), encodedData, bufferInfo)
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                }
            }
        }
    }

    private fun saveMp4ToGallery(context: Context, sourceFile: File, displayName: String): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/ChitraFlow Studio")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        resolver.openOutputStream(uri)?.use { out ->
            sourceFile.inputStream().use { input -> input.copyTo(out) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        return uri
    }

    fun resolveDrawableId(name: String): Int = when (name) {
        "img_studio_hero" -> R.drawable.img_studio_hero
        "img_tpl_documentary" -> R.drawable.img_tpl_documentary
        "img_tpl_motion" -> R.drawable.img_tpl_motion
        "img_tpl_street_cinema" -> R.drawable.img_tpl_street_cinema
        else -> R.drawable.img_tpl_documentary
    }
}
