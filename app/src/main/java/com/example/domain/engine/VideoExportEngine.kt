package com.example.domain.engine

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Camera
import android.graphics.Canvas
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
import com.example.domain.model.BorderConfig
import com.example.domain.model.BorderShapeStyle
import com.example.domain.model.ChartPaletteTheme
import com.example.domain.model.ChartVisualStyle
import com.example.domain.model.ComparisonChartConfig
import com.example.domain.model.ExportFps
import com.example.domain.model.InvestigativeDocConfig
import com.example.domain.model.InvestigativeToolType
import com.example.domain.model.LowerThirdTemplate
import com.example.domain.model.LumetriColorGrade
import com.example.domain.model.MapRadarStyle
import com.example.domain.model.MaskShape
import com.example.domain.model.PreviewResolution
import com.example.domain.model.StudioFontOption
import com.example.domain.model.StudioProject
import com.example.domain.model.StudioTextAlign
import com.example.domain.model.TimelineClip
import com.example.domain.model.TrackType
import com.example.domain.model.VfxConfig
import com.example.domain.model.VfxEffectType
import android.media.MediaScannerConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan
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
        fastMode: Boolean = true,
        onProgress: (Float, String) -> Unit
    ): ExportResult = withContext(Dispatchers.Default) {
        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        val tempFile = File(context.cacheDir, "chitraflow_export_${System.currentTimeMillis()}.mp4")

        try {
            val (width, height) = computeExportDimensions(project, resolution)
            val fps = fpsOption.fps
            val totalDurationMs = project.totalDurationMs.coerceIn(1000L, 600_000L)
            val totalFrames = ((totalDurationMs / 1000f) * fps).toInt().coerceAtLeast(fps)

            coroutineContext.ensureActive()
            onProgress(0.04f, "অডিও মিক্সিং (Voice + Auto-Duck BGM + SFX)...")
            val mixedPcm = AudioExportMixer.mixProjectAudioPcm(context, project)
            coroutineContext.ensureActive()
            val encodedAudio = AudioExportMixer.encodePcmToAacFrames(mixedPcm)

            onProgress(0.08f, "হার্ডওয়্যার এনকোডার প্রস্তুত হচ্ছে ($width×$height @ ${fps}fps)...")

            val bitrate = when (resolution) {
                PreviewResolution.P720 -> if (fastMode) 5_000_000 else 6_500_000
                PreviewResolution.P1080 -> if (fastMode) 9_500_000 else 12_500_000
                PreviewResolution.UHD4K -> if (fastMode) 18_000_000 else 24_000_000
            }

            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, if (fastMode) 2 else 1)
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
            val videoCache = VideoFrameSessionCache(context, project, width, height, fastMode)
            val fontCache = preloadTypefaces(context, project)

            val bufferInfo = MediaCodec.BufferInfo()
            val frameDurationUs = 1_000_000L / fps

            try {
                for (frameIdx in 0 until totalFrames) {
                    coroutineContext.ensureActive()
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
                            videoCache = videoCache,
                            fontCache = fontCache
                        )
                    } finally {
                        inputSurface.unlockCanvasAndPost(canvas)
                    }

                    drainEncoder(codec, activeMuxer, bufferInfo, endOfStream = false,
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
                        presentationTimeUsOverride = frameIdx * frameDurationUs
                    )

                    if (frameIdx % 4 == 0 || frameIdx == totalFrames - 1) {
                        val pct = 0.1f + 0.78f * (frameIdx.toFloat() / totalFrames.toFloat())
                        onProgress(pct, "রেন্ডারিং ফ্রেম ${frameIdx + 1}/$totalFrames...")
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

                if (!muxerStarted) {
                    throw IllegalStateException("Hardware video encoder could not initialize output track")
                }

                // Write all mixed AAC audio frames into the MP4 audio track
                if (encodedAudio != null && audioTrackIndex >= 0) {
                    onProgress(0.91f, "অডিও ট্র্যাক যুক্ত হচ্ছে...")
                    val audioBufInfo = MediaCodec.BufferInfo()
                    encodedAudio.frames.forEach { aFrame ->
                        val bb = ByteBuffer.wrap(aFrame.data)
                        audioBufInfo.set(0, aFrame.data.size, aFrame.presentationTimeUs, aFrame.flags)
                        activeMuxer.writeSampleData(audioTrackIndex, bb, audioBufInfo)
                    }
                }
            } finally {
                videoCache.release()
                bitmapCache.values.forEach { it.recycle() }
                runCatching { codec.stop() }
                runCatching { codec.release() }
                codec = null
                if (muxerStarted) runCatching { activeMuxer.stop() }
                runCatching { activeMuxer.release() }
                muxer = null
            }

            onProgress(0.95f, "গ্যালারিতে MP4 সেভ হচ্ছে...")
            val safeName = project.name.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "ChitraFlow_Video" }
            val fileName = "${safeName}_${resolution.label}_${System.currentTimeMillis()}.mp4"
            val (savedUri, savedLabel) = saveMp4ToGallery(context, tempFile, fileName)
            tempFile.delete()

            onProgress(1.0f, "এক্সপোর্ট সম্পন্ন!")
            ExportResult(
                success = true,
                savedLocationLabel = savedLabel,
                outputUri = savedUri
            )
        } catch (ce: CancellationException) {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { muxer?.release() }
            tempFile.delete()
            throw ce
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
        fun loadImageSource(key: String, uriStr: String?, drawableName: String?, chromaKey: Boolean, chromaStrength: Float) {
            val bmp: Bitmap? = when {
                uriStr != null -> runCatching {
                    context.contentResolver.openInputStream(Uri.parse(uriStr))?.use { BitmapFactory.decodeStream(it) }
                }.getOrNull()
                drawableName != null -> BitmapFactory.decodeResource(context.resources, resolveDrawableId(drawableName))
                else -> null
            }
            if (bmp != null) {
                val scaled = Bitmap.createScaledBitmap(bmp, targetW, targetH, true)
                if (scaled !== bmp) bmp.recycle()
                val finalBmp = if (chromaKey) {
                    val keyed = TransitionAndChromaEngine.applyGreenScreenChromaKey(scaled, chromaStrength)
                    scaled.recycle()
                    keyed
                } else {
                    scaled
                }
                map[key] = finalBmp
            }
        }
        project.clips.forEach { clip ->
            clip.mediaSource?.let { src ->
                if (!src.isVideo) {
                    loadImageSource(
                        key = clip.id,
                        uriStr = src.uriString,
                        drawableName = src.drawableName,
                        chromaKey = clip.colorGrade.chromaKeyEnabled,
                        chromaStrength = clip.colorGrade.chromaKeyStrength
                    )
                }
            }
        }
        return map
    }

    private class VideoFrameSessionCache(
        private val context: Context,
        project: StudioProject,
        private val targetW: Int,
        private val targetH: Int,
        private val fastMode: Boolean
    ) {
        private val retrievers = mutableMapOf<String, MediaMetadataRetriever>()
        private val sourceDurationsMs = mutableMapOf<String, Long>()
        private val lastBucket = mutableMapOf<String, Long>()
        private val cachedFrames = mutableMapOf<String, Bitmap>()

        init {
            project.clips.forEach { clip ->
                val src = clip.mediaSource
                if (src != null && src.isVideo && src.uriString != null) {
                    openRetriever(clip.id, src.uriString)
                }
            }
        }

        private fun openRetriever(key: String, uriString: String) {
            runCatching {
                val mmr = MediaMetadataRetriever()
                mmr.setDataSource(context, Uri.parse(uriString))
                retrievers[key] = mmr
                val durMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                if (durMs > 0L) sourceDurationsMs[key] = durMs
            }
        }

        fun getFrame(
            key: String,
            remappedTimeMs: Long,
            chromaKeyEnabled: Boolean,
            chromaKeyStrength: Float
        ): Bitmap? {
            val mmr = retrievers[key] ?: return null
            val stepMs = if (fastMode) 66L else 33L
            val srcDur = sourceDurationsMs[key]
            val clampedTime = if (srcDur != null && srcDur > 0L) (remappedTimeMs % srcDur).coerceAtLeast(0L) else remappedTimeMs.coerceAtLeast(0L)
            val bucketMs = (clampedTime / stepMs) * stepMs
            if (lastBucket[key] == bucketMs && cachedFrames[key] != null) {
                return cachedFrames[key]
            }
            val raw = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    mmr.getScaledFrameAtTime(bucketMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST, targetW, targetH)
                        ?: mmr.getScaledFrameAtTime(bucketMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, targetW, targetH)
                } else {
                    mmr.getFrameAtTime(bucketMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST)
                        ?: mmr.getFrameAtTime(bucketMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }
            }.getOrNull() ?: return cachedFrames[key]

            val scaled = if (raw.width == targetW && raw.height == targetH) {
                raw
            } else {
                Bitmap.createScaledBitmap(raw, targetW, targetH, true).also {
                    if (it !== raw) raw.recycle()
                }
            }
            val processed = if (chromaKeyEnabled) {
                val keyed = TransitionAndChromaEngine.applyGreenScreenChromaKey(scaled, chromaKeyStrength)
                scaled.recycle()
                keyed
            } else {
                scaled
            }
            cachedFrames[key]?.recycle()
            cachedFrames[key] = processed
            lastBucket[key] = bucketMs
            return processed
        }

        fun release() {
            cachedFrames.values.forEach { runCatching { it.recycle() } }
            cachedFrames.clear()
            retrievers.values.forEach { runCatching { it.release() } }
            retrievers.clear()
        }
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
        videoCache: VideoFrameSessionCache,
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

            if (kotlin.math.abs(xform.rotationX) > 0.2f || kotlin.math.abs(xform.rotationY) > 0.2f) {
                val cam = Camera()
                val mat = Matrix()
                cam.save()
                cam.setLocation(0f, 0f, -18f)
                cam.rotateX(xform.rotationX)
                cam.rotateY(xform.rotationY)
                cam.getMatrix(mat)
                cam.restore()
                canvas.concat(mat)
            }

            canvas.rotate(xform.rotation + trans.rotationDeg + if (applyShake) shakeRotDeg else 0f)
            val effScale = xform.scale * trans.scaleMult
            canvas.scale(effScale, effScale)
            if (kotlin.math.abs(xform.skewX) > 0.2f || kotlin.math.abs(xform.skewY) > 0.2f) {
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
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                alpha = (effOpacity * 255).toInt().coerceIn(0, 255)
                xfermode = when (clip.blendMode) {
                    BlendModeOption.NORMAL -> null
                    BlendModeOption.SCREEN -> PorterDuffXfermode(PorterDuff.Mode.SCREEN)
                    BlendModeOption.OVERLAY -> PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
                    BlendModeOption.MULTIPLY -> PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
                    BlendModeOption.PLUS_ADD -> PorterDuffXfermode(PorterDuff.Mode.ADD)
                }
                if (clip.trackType == TrackType.VIDEO || hasAdj) {
                    colorFilter = ColorMatrixColorFilter(ColorGradingEngine.buildAndroidColorMatrix(mergedGrade))
                }
            }

            val remappedLocalMs = SpeedRampEngine.computeRemappedVideoTimeMs(
                clip.speedRamp,
                (playheadMs - clip.startMs).coerceAtLeast(0L),
                clip.durationMs
            )
            val mediaBmp = if (clip.mediaSource?.isVideo == true) {
                videoCache.getFrame(
                    key = clip.id,
                    remappedTimeMs = remappedLocalMs,
                    chromaKeyEnabled = mergedGrade.chromaKeyEnabled,
                    chromaKeyStrength = mergedGrade.chromaKeyStrength
                )
            } else {
                bitmapCache[clip.id]
            }

            // Clip inner content to geometric border shape when an enclosed geometric border is selected
            canvas.save()
            clipBorderShapeIfEnclosed(canvas, clip.borderConfig, width, height)

            mediaBmp?.let { bmp ->
                val effBlur = maxOf(
                    if (clip.trackType == TrackType.VIDEO) mergedGrade.blurRadius else 0f,
                    xform.blurRadius
                )
                val destRect = RectF(-width / 2f, -height / 2f, width / 2f, height / 2f)
                if (effBlur > 1.2f) {
                    val downFactor = (1f / (1f + effBlur * 0.45f)).coerceIn(0.06f, 0.7f)
                    val bw = (bmp.width * downFactor).toInt().coerceAtLeast(8)
                    val bh = (bmp.height * downFactor).toInt().coerceAtLeast(8)
                    val small = Bitmap.createScaledBitmap(bmp, bw, bh, true)
                    canvas.drawBitmap(small, null, destRect, paint)
                    small.recycle()
                } else {
                    canvas.drawBitmap(bmp, null, destRect, paint)
                }
            }

            clip.chartConfig?.let { chart ->
                drawComparisonChart(canvas, clip, chart, width, height, playheadMs, fontCache)
            }

            clip.investigativeConfig?.let { inv ->
                drawInvestigativeOverlay(canvas, clip, inv, width, height, playheadMs, fontCache)
            }

            clip.textConfig?.let {
                drawTextOrLowerThird(canvas, clip, width, height, playheadMs, effOpacity, xform, fontCache)
            }

            canvas.restore() // restore border inner clip

            // Draw geometric border frame on top of clip
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
                for (g in 0 until bars) {
                    val gy = -height / 2f + gRand.nextFloat() * height
                    val gh = 4f + gRand.nextFloat() * 14f
                    val col = if (g % 2 == 0) 0x7700E5FF else 0x77FF4D6D
                    canvas.drawRect(-width / 2f, gy, width / 2f, gy + gh, Paint().apply { color = col })
                }
            }

            val totalFlash = maxOf(trans.flashAlpha, xform.flashAlpha)
            if (totalFlash > 0.02f) {
                val flashCol = if (trans.isWarmFlash) {
                    android.graphics.Color.argb((totalFlash * 185).toInt().coerceIn(0, 255), 255, 130, 30)
                } else {
                    android.graphics.Color.argb((totalFlash * 175).toInt().coerceIn(0, 255), 255, 255, 255)
                }
                canvas.drawRect(-width / 2f, -height / 2f, width / 2f, height / 2f, Paint().apply { color = flashCol })
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
                path.moveTo(cx, rect.top)
                path.lineTo(rect.right, cy)
                path.lineTo(cx, rect.bottom)
                path.lineTo(rect.left, cy)
                path.close()
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
            BorderShapeStyle.CIRCLE_PORTRAIT -> {
                path.addCircle(cx, cy, minOf(rx, ry) * 0.94f, Path.Direction.CW)
            }
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
            BorderShapeStyle.CIRCLE_PORTRAIT -> {
                val path = buildBorderPolygonPath(border.style, rect, scaleFactor)
                canvas.drawPath(path, strokePaint)
            }
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
                // Corners
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

    private fun drawComparisonChart(
        canvas: Canvas,
        clip: TimelineClip,
        chart: ComparisonChartConfig,
        width: Int,
        height: Int,
        playheadMs: Long,
        fontCache: Map<String, Typeface>
    ) {
        val activeBars = chart.bars.take(chart.barCount.coerceIn(0, 100)).let {
            if (chart.sortDescending) it.sortedByDescending { b -> b.value } else it
        }
        val resolvedStyle = if (chart.chartStyle == ChartVisualStyle.HORIZONTAL_BAR && !chart.isHorizontal) {
            ChartVisualStyle.VERTICAL_BAR
        } else {
            chart.chartStyle
        }

        val progress = if (chart.animateGrowth && clip.durationMs > 0L) {
            ((playheadMs - clip.startMs).toFloat() / (clip.durationMs * 0.45f).coerceAtLeast(600f)).coerceIn(0.08f, 1f)
        } else 1f
        val tf = fontCache[StudioFontOption.HIND_SILIGURI.id] ?: Typeface.DEFAULT
        val paletteCols = chart.paletteTheme.colors

        val boxRect = RectF(-width * 0.42f, -height * 0.36f, width * 0.42f, height * 0.36f)
        canvas.drawRoundRect(
            boxRect,
            width * 0.025f,
            width * 0.025f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = (0xE80A0F1B).toInt() }
        )
        canvas.drawRoundRect(
            boxRect,
            width * 0.025f,
            width * 0.025f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f
                color = (0x8800E5FF).toInt()
            }
        )

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tf
            textSize = width * 0.032f
            color = (0xFFFFB300).toInt()
        }
        canvas.drawText(chart.title, boxRect.left + width * 0.03f, boxRect.top + width * 0.055f, titlePaint)

        val countPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tf
            textSize = width * 0.020f
            color = (0xFF00E5FF).toInt()
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("${activeBars.size} · ${resolvedStyle.labelBn}", boxRect.right - width * 0.03f, boxRect.top + width * 0.052f, countPaint)

        if (activeBars.isEmpty()) {
            val emptyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = tf
                textSize = width * 0.024f
                color = (0xFF94A3B8).toInt()
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("০টি ডেটা · স্টুডিও থেকে ডেটা যোগ করুন", boxRect.centerX(), boxRect.centerY(), emptyPaint)
            return
        }

        val maxVal = activeBars.maxOfOrNull { it.value }?.coerceAtLeast(1f) ?: 100f
        val plotTop = boxRect.top + width * 0.085f
        val plotBottom = boxRect.bottom - width * 0.03f
        val plotLeft = boxRect.left + width * 0.03f
        val plotRight = boxRect.right - width * 0.03f

        fun resolveBarColor(idx: Int, barColorHex: Long): Int =
            if (chart.paletteTheme == ChartPaletteTheme.DEFAULT_MIX) barColorHex.toInt()
            else paletteCols[idx % paletteCols.size].toInt()

        when (resolvedStyle) {
            ChartVisualStyle.HORIZONTAL_BAR -> {
                val rowH = (plotBottom - plotTop) / activeBars.size.toFloat()
                val barH = (rowH * 0.72f).coerceAtLeast(2f)
                val labelW = if (activeBars.size <= 24) (plotRight - plotLeft) * 0.26f else 0f
                val valW = if (activeBars.size <= 18) width * 0.08f else 0f
                val maxBarW = (plotRight - plotLeft - labelW - valW - width * 0.02f).coerceAtLeast(20f)
                val lblPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = tf
                    textSize = minOf(rowH * 0.65f, width * 0.023f)
                    color = android.graphics.Color.WHITE
                }
                val trackBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = (0xFF151C2C).toInt() }

                activeBars.forEachIndexed { i, bar ->
                    val rawCol = resolveBarColor(i, bar.colorHex)
                    val y = plotTop + i * rowH
                    if (labelW > 0f) {
                        val txt = if (activeBars.size <= 14) "#${i + 1} ${bar.label.take(9)}" else bar.label.take(10)
                        canvas.drawText(txt, plotLeft, y + barH * 0.85f, lblPaint)
                    }
                    val barStart = plotLeft + labelW
                    canvas.drawRoundRect(RectF(barStart, y, barStart + maxBarW, y + barH), 4f, 4f, trackBgPaint)

                    val frac = ((bar.value / maxVal) * progress).coerceIn(0.03f, 1f)
                    val endX = barStart + maxBarW * frac
                    val barRect = RectF(barStart, y, endX, y + barH)
                    val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        shader = LinearGradient(
                            barStart, y, endX, y,
                            (rawCol and 0x00FFFFFF) or (0xB8 shl 24),
                            rawCol or (0xFF shl 24),
                            Shader.TileMode.CLAMP
                        )
                    }
                    canvas.drawRoundRect(barRect, 4f, 4f, barPaint)
                    if (valW > 0f) {
                        val valPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                            typeface = tf
                            textSize = minOf(rowH * 0.62f, width * 0.020f)
                            color = rawCol or (0xFF shl 24)
                        }
                        canvas.drawText("${bar.value.toInt()}${chart.unitSuffix}", endX + width * 0.012f, y + barH * 0.84f, valPaint)
                    }
                }
            }

            ChartVisualStyle.VERTICAL_BAR -> {
                val colW = (plotRight - plotLeft) / activeBars.size.toFloat()
                val barW = (colW * 0.74f).coerceAtLeast(2f)
                val maxBarH = (plotBottom - plotTop) * 0.86f
                val lblPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = tf
                    textSize = width * 0.017f
                    color = (0xFFCBD5E1).toInt()
                    textAlign = Paint.Align.CENTER
                }
                activeBars.forEachIndexed { i, bar ->
                    val rawCol = resolveBarColor(i, bar.colorHex)
                    val x = plotLeft + i * colW
                    val frac = ((bar.value / maxVal) * progress).coerceIn(0.04f, 1f)
                    val barH = maxBarH * frac
                    val bottomLine = plotBottom - if (activeBars.size <= 12) width * 0.024f else 0f
                    val barRect = RectF(x, bottomLine - barH, x + barW, bottomLine)
                    val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        shader = LinearGradient(
                            x, bottomLine - barH, x, bottomLine,
                            rawCol or (0xFF shl 24),
                            (rawCol and 0x00FFFFFF) or (0xAA shl 24),
                            Shader.TileMode.CLAMP
                        )
                    }
                    canvas.drawRoundRect(barRect, 4f, 4f, barPaint)
                    if (activeBars.size <= 12) {
                        canvas.drawText(bar.label.take(6), x + barW / 2f, plotBottom, lblPaint)
                    }
                }
            }

            ChartVisualStyle.PIE_CIRCLE,
            ChartVisualStyle.DONUT_RING -> {
                val isDonut = resolvedStyle == ChartVisualStyle.DONUT_RING
                val sumValues = activeBars.sumOf { it.value.toDouble().coerceAtLeast(0.01) }.toFloat().coerceAtLeast(1f)
                val availW = (plotRight - plotLeft) * 0.54f
                val availH = plotBottom - plotTop
                val diameter = minOf(availW, availH) * 0.88f
                val cx = plotLeft + availW * 0.5f
                val cy = (plotTop + plotBottom) * 0.5f
                val ringStroke = diameter * 0.24f
                val oval = if (isDonut) {
                    val r = (diameter - ringStroke) * 0.5f
                    RectF(cx - r, cy - r, cx + r, cy + r)
                } else {
                    val r = diameter * 0.5f
                    RectF(cx - r, cy - r, cx + r, cy + r)
                }

                var startAngle = -90f
                val maxSweepTotal = 360f * progress.coerceIn(0.04f, 1f)
                var sweptSoFar = 0f

                activeBars.forEachIndexed { idx, bar ->
                    val rawCol = resolveBarColor(idx, bar.colorHex) or (0xFF shl 24)
                    val sliceFull = (bar.value.coerceAtLeast(0.01f) / sumValues) * 360f
                    val sweep = minOf(sliceFull, (maxSweepTotal - sweptSoFar).coerceAtLeast(0f))
                    if (sweep > 0.4f) {
                        if (isDonut) {
                            val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = rawCol
                                style = Paint.Style.STROKE
                                strokeWidth = ringStroke
                                strokeCap = Paint.Cap.BUTT
                            }
                            canvas.drawArc(oval, startAngle, (sweep - 1.2f).coerceAtLeast(0.5f), false, ringPaint)
                        } else {
                            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = rawCol }
                            canvas.drawArc(oval, startAngle, sweep, true, fillPaint)
                            val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = (0xFF0A0F1A).toInt()
                                style = Paint.Style.STROKE
                                strokeWidth = 2.5f
                            }
                            canvas.drawArc(oval, startAngle, sweep, true, dividerPaint)
                        }
                    }
                    startAngle += sliceFull
                    sweptSoFar += sliceFull
                }

                if (isDonut) {
                    val centerValPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        typeface = tf
                        textSize = diameter * 0.14f
                        color = android.graphics.Color.WHITE
                        textAlign = Paint.Align.CENTER
                    }
                    canvas.drawText("${sumValues.toInt()}${chart.unitSuffix}", cx, cy + diameter * 0.05f, centerValPaint)
                }

                // Legend on the right side
                val legendX = plotLeft + availW + width * 0.02f
                val legendRight = plotRight
                val legendRows = minOf(activeBars.size, 8)
                val rowSpacing = (plotBottom - plotTop) / legendRows.coerceAtLeast(1)
                val legendPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = tf
                    textSize = minOf(rowSpacing * 0.48f, width * 0.021f)
                    color = android.graphics.Color.WHITE
                }
                val pctPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = tf
                    textSize = legendPaint.textSize
                    textAlign = Paint.Align.RIGHT
                }
                activeBars.take(8).forEachIndexed { idx, bar ->
                    val rawCol = resolveBarColor(idx, bar.colorHex) or (0xFF shl 24)
                    val rowY = plotTop + (idx + 0.55f) * rowSpacing
                    canvas.drawCircle(legendX + width * 0.01f, rowY - legendPaint.textSize * 0.3f, width * 0.007f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = rawCol })
                    canvas.drawText(bar.label.take(12), legendX + width * 0.025f, rowY, legendPaint)
                    val pct = ((bar.value / sumValues) * 100f).toInt()
                    pctPaint.color = rawCol
                    canvas.drawText("$pct%", legendRight, rowY, pctPaint)
                }
            }

            ChartVisualStyle.LINE_GRAPH,
            ChartVisualStyle.AREA_WAVE -> {
                val isWave = resolvedStyle == ChartVisualStyle.AREA_WAVE
                val primaryCol = paletteCols.first().toInt() or (0xFF shl 24)
                val chartBottom = plotBottom - width * 0.026f
                val chartH = (chartBottom - plotTop).coerceAtLeast(20f)
                val chartW = (plotRight - plotLeft).coerceAtLeast(20f)

                val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = (0x1AFFFFFF)
                    strokeWidth = 1.5f
                }
                for (g in 0..3) {
                    val gy = plotTop + chartH * (g / 3f)
                    canvas.drawLine(plotLeft, gy, plotRight, gy, gridPaint)
                }

                val pts = activeBars.mapIndexed { idx, bar ->
                    val px = if (activeBars.size == 1) plotLeft + chartW * 0.5f
                    else plotLeft + (idx.toFloat() / (activeBars.size - 1)) * chartW
                    val normY = (bar.value / maxVal).coerceIn(0.03f, 1f)
                    val py = chartBottom - normY * chartH * 0.88f
                    Pair(px, py)
                }
                val maxDrawX = plotLeft + chartW * progress.coerceIn(0.04f, 1f)
                val visiblePts = pts.filter { it.first <= maxDrawX + 2f }.ifEmpty { listOf(pts.first()) }

                val path = Path()
                if (visiblePts.size == 1) {
                    path.moveTo(plotLeft, visiblePts[0].second)
                    path.lineTo(visiblePts[0].first, visiblePts[0].second)
                } else {
                    path.moveTo(visiblePts[0].first, visiblePts[0].second)
                    for (i in 1 until visiblePts.size) {
                        val prev = visiblePts[i - 1]
                        val cur = visiblePts[i]
                        if (isWave) {
                            val midX = (prev.first + cur.first) * 0.5f
                            path.cubicTo(midX, prev.second, midX, cur.second, cur.first, cur.second)
                        } else {
                            path.lineTo(cur.first, cur.second)
                        }
                    }
                }

                if (isWave) {
                    val fillPath = Path(path).apply {
                        lineTo(visiblePts.last().first, chartBottom)
                        lineTo(visiblePts.first().first, chartBottom)
                        close()
                    }
                    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        shader = LinearGradient(
                            0f, plotTop, 0f, chartBottom,
                            (primaryCol and 0x00FFFFFF) or (0x80 shl 24),
                            (primaryCol and 0x00FFFFFF) or (0x0A shl 24),
                            Shader.TileMode.CLAMP
                        )
                    }
                    canvas.drawPath(fillPath, fillPaint)
                }

                val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 9f
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    color = (primaryCol and 0x00FFFFFF) or (0x4D shl 24)
                }
                canvas.drawPath(path, glowPaint)

                val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 4f
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    color = primaryCol
                }
                canvas.drawPath(path, strokePaint)

                visiblePts.forEachIndexed { idx, pt ->
                    val dotCol = resolveBarColor(idx, activeBars[idx].colorHex) or (0xFF shl 24)
                    canvas.drawCircle(pt.first, pt.second, 7f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = dotCol })
                    canvas.drawCircle(pt.first, pt.second, 3f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE })
                }

                val lblPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = tf
                    textSize = width * 0.017f
                    color = (0xFFCBD5E1).toInt()
                    textAlign = Paint.Align.CENTER
                }
                val step = (activeBars.size / 6).coerceAtLeast(1)
                activeBars.forEachIndexed { idx, bar ->
                    if (idx % step == 0 || idx == activeBars.lastIndex) {
                        canvas.drawText(bar.label.take(6), pts[idx].first, plotBottom, lblPaint)
                    }
                }
            }

            ChartVisualStyle.RADAR_WEB -> {
                val primaryCol = paletteCols.first().toInt() or (0xFF shl 24)
                val cx = (plotLeft + plotRight) * 0.5f
                val cy = (plotTop + plotBottom) * 0.5f
                val radius = minOf(plotRight - plotLeft, plotBottom - plotTop) * 0.40f
                val spokes = activeBars.size.coerceAtLeast(3)

                val webPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 1.5f
                    color = (0x26FFFFFF)
                }
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
                    canvas.drawPath(ringPath, webPaint)
                }
                for (i in 0 until spokes) {
                    val ang = (-PI / 2.0) + (2.0 * PI * i / spokes)
                    val px = cx + (cos(ang) * radius).toFloat()
                    val py = cy + (sin(ang) * radius).toFloat()
                    canvas.drawLine(cx, cy, px, py, webPaint)
                }

                val polyPath = Path()
                val radarPts = (0 until spokes).map { i ->
                    val bar = activeBars[i % activeBars.size]
                    val norm = ((bar.value / maxVal) * progress).coerceIn(0.08f, 1f)
                    val r = radius * norm
                    val ang = (-PI / 2.0) + (2.0 * PI * i / spokes)
                    Pair(cx + (cos(ang) * r).toFloat(), cy + (sin(ang) * r).toFloat())
                }
                radarPts.forEachIndexed { i, pt ->
                    if (i == 0) polyPath.moveTo(pt.first, pt.second) else polyPath.lineTo(pt.first, pt.second)
                }
                polyPath.close()

                canvas.drawPath(polyPath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = (primaryCol and 0x00FFFFFF) or (0x4D shl 24)
                })
                canvas.drawPath(polyPath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = primaryCol
                    style = Paint.Style.STROKE
                    strokeWidth = 3.5f
                })

                val lblPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = tf
                    textSize = width * 0.018f
                    color = android.graphics.Color.WHITE
                    textAlign = Paint.Align.CENTER
                }
                activeBars.forEachIndexed { idx, bar ->
                    val dotCol = resolveBarColor(idx, bar.colorHex) or (0xFF shl 24)
                    val pt = radarPts[idx % radarPts.size]
                    canvas.drawCircle(pt.first, pt.second, 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = dotCol })
                    if (activeBars.size <= 10) {
                        val ang = (-PI / 2.0) + (2.0 * PI * idx / spokes)
                        val lx = cx + (cos(ang) * (radius * 1.15f)).toFloat()
                        val ly = cy + (sin(ang) * (radius * 1.15f)).toFloat()
                        canvas.drawText(bar.label.take(7), lx, ly, lblPaint)
                    }
                }
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
                val radarStyle = inv.mapStyle
                val gridCol = radarStyle.gridHex.toInt()
                val routeCol = radarStyle.routeHex.toInt()
                val markerCol = radarStyle.markerHex.toInt()
                val box = RectF(-width * 0.42f, -height * 0.30f, width * 0.42f, height * 0.30f)
                canvas.drawRoundRect(box, 20f, 20f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = radarStyle.bgHex.toInt() })
                canvas.drawRoundRect(box, 20f, 20f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE; strokeWidth = 2.5f; color = (gridCol and 0x00FFFFFF) or (0x88 shl 24)
                })

                val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    strokeWidth = 1.2f
                    color = (gridCol and 0x00FFFFFF) or (0x24 shl 24)
                }
                for (c in 1 until 9) {
                    val gx = box.left + box.width() * c / 9f
                    canvas.drawLine(gx, box.top, gx, box.bottom, gridPaint)
                }
                for (r in 1 until 6) {
                    val gy = box.top + box.height() * r / 6f
                    canvas.drawLine(box.left, gy, box.right, gy, gridPaint)
                }

                val ax = box.left + box.width() * inv.pointAX.coerceIn(0.06f, 0.94f)
                val ay = box.top + box.height() * inv.pointAY.coerceIn(0.12f, 0.90f)
                val bx = box.left + box.width() * inv.pointBX.coerceIn(0.06f, 0.94f)
                val by = box.top + box.height() * inv.pointBY.coerceIn(0.12f, 0.90f)
                val ctrlX = if (inv.useWaypointC) {
                    box.left + box.width() * inv.pointCX.coerceIn(0.06f, 0.94f)
                } else {
                    (ax + bx) / 2f
                }
                val ctrlY = if (inv.useWaypointC) {
                    box.top + box.height() * inv.pointCY.coerceIn(0.12f, 0.90f)
                } else {
                    maxOf(box.top + box.height() * 0.08f, minOf(ay, by) - box.height() * 0.18f)
                }

                val fullPath = Path().apply { moveTo(ax, ay); quadTo(ctrlX, ctrlY, bx, by) }
                canvas.drawPath(fullPath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE; strokeWidth = width * 0.004f
                    color = (routeCol and 0x00FFFFFF) or (0x44 shl 24)
                    pathEffect = DashPathEffect(floatArrayOf(12f, 10f), 0f)
                })

                val animT = minOf(1f, progress * 1.15f).coerceAtLeast(0.02f)
                val activePath = Path()
                var curX = ax; var curY = ay
                for (s in 0..28) {
                    val st = (s / 28f) * animT
                    val omt = 1f - st
                    val px = omt * omt * ax + 2f * omt * st * ctrlX + st * st * bx
                    val py = omt * omt * ay + 2f * omt * st * ctrlY + st * st * by
                    if (s == 0) activePath.moveTo(px, py) else activePath.lineTo(px, py)
                    if (s == 28) { curX = px; curY = py }
                }
                canvas.drawPath(activePath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE; strokeWidth = width * 0.013f; strokeCap = Paint.Cap.ROUND
                    color = (routeCol and 0x00FFFFFF) or (0x48 shl 24)
                })
                canvas.drawPath(activePath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE; strokeWidth = width * 0.0055f; strokeCap = Paint.Cap.ROUND
                    color = routeCol
                })

                val pulseR = width * (0.018f + 0.022f * ((progress * 5f) % 1f))
                canvas.drawCircle(ax, ay, pulseR, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = (markerCol and 0x00FFFFFF) or (0x44 shl 24) })
                canvas.drawCircle(ax, ay, width * 0.011f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = markerCol })
                canvas.drawCircle(bx, by, pulseR, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = (routeCol and 0x00FFFFFF) or (0x44 shl 24) })
                canvas.drawCircle(bx, by, width * 0.011f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = routeCol })
                if (inv.useWaypointC) {
                    canvas.drawCircle(ctrlX, ctrlY, width * 0.009f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE })
                }
                canvas.drawCircle(curX, curY, width * 0.012f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE })

                val lblP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = tf; textSize = width * 0.022f; color = android.graphics.Color.WHITE }
                canvas.drawText(inv.pointALabel, ax - width * 0.02f, ay + width * 0.035f, lblP)
                canvas.drawText(inv.pointBLabel, bx - width * 0.03f, by - width * 0.022f, lblP)
                val routeTitle = if (inv.useWaypointC && inv.pointCLabel.isNotBlank()) {
                    "${inv.headline} · ${inv.pointALabel} ➔ ${inv.pointCLabel} ➔ ${inv.pointBLabel}"
                } else {
                    "${inv.headline} · ${inv.pointALabel} ➔ ${inv.pointBLabel}"
                }
                canvas.drawText(routeTitle, box.left + width * 0.025f, box.top + width * 0.042f, lblP)
                if (inv.distanceLabel.isNotBlank()) {
                    val distP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = tf; textSize = width * 0.021f; color = routeCol; textAlign = Paint.Align.RIGHT }
                    canvas.drawText(inv.distanceLabel, box.right - width * 0.025f, box.bottom - width * 0.024f, distP)
                }
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
                val lightPaint = Paint().apply { color = android.graphics.Color.argb((intensity * 52).toInt(), 255, 255, 255) }
                val darkPaint = Paint().apply { color = android.graphics.Color.argb((intensity * 58).toInt(), 12, 12, 16) }
                for (i in 0 until 180) {
                    canvas.drawCircle(rand.nextFloat() * w, rand.nextFloat() * h, 1.4f + (i % 2) * 0.6f, if (i % 2 == 0) lightPaint else darkPaint)
                }
                if (rand.nextFloat() < 0.35f) {
                    val sx = rand.nextFloat() * w
                    canvas.drawLine(sx, 0f, sx + 3f, h, Paint().apply { color = android.graphics.Color.argb((intensity * 38).toInt(), 255, 255, 255); strokeWidth = 1.5f })
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
                canvas.drawRect(4f, 4f, w - 4f, h - 4f, Paint().apply {
                    style = Paint.Style.STROKE; strokeWidth = w * 0.014f * intensity; color = android.graphics.Color.argb(105, 255, 35, 80)
                })
                canvas.drawRect(4f + shift, 4f, w - 4f + shift, h - 4f, Paint().apply {
                    style = Paint.Style.STROKE; strokeWidth = w * 0.012f * intensity; color = android.graphics.Color.argb(95, 0, 229, 255)
                })
            }
            VfxEffectType.GLITCH_TEAR -> {
                val bars = (intensity * 10).toInt().coerceAtLeast(3)
                for (i in 0 until bars) {
                    val y = rand.nextFloat() * h
                    val bh = 4f + rand.nextFloat() * 16f
                    val color = if (i % 2 == 0) 0x6600E5FF else 0x66FF4D6D
                    val xOff = (rand.nextFloat() - 0.5f) * w * 0.08f * intensity
                    canvas.drawRect(xOff, y, xOff + w * (0.65f + rand.nextFloat() * 0.35f), y + bh, Paint().apply { this.color = color })
                }
            }
            VfxEffectType.CINEMA_RAIN -> {
                val mistPaint = Paint().apply {
                    shader = LinearGradient(0f, 0f, 0f, h, 0x00000000, android.graphics.Color.argb((intensity * 38).toInt(), 120, 185, 255), Shader.TileMode.CLAMP)
                }
                canvas.drawRect(0f, 0f, w, h, mistPaint)
                for (i in 0 until 85) {
                    val isNear = i % 3 == 0
                    val rx = ((i * 97f + tSec * (if (isNear) 190f else 120f)) % w)
                    val ry = ((i * 53f + tSec * (if (isNear) 880f else 620f)) % h)
                    val len = if (isNear) 42f else 24f
                    val p = Paint().apply {
                        color = android.graphics.Color.argb((intensity * (if (isNear) 150 else 85)).toInt(), 190, 228, 255)
                        strokeWidth = if (isNear) 3.2f else 1.8f
                    }
                    canvas.drawLine(rx, ry, rx - len * 0.28f, ry + len, p)
                }
            }
            VfxEffectType.EMBERS_SPARK -> {
                val glowP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.argb((intensity * 75).toInt(), 255, 110, 0) }
                val coreP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.argb((intensity * 220).toInt(), 255, 205, 50) }
                for (i in 0 until 48) {
                    val ex = ((i * 113f + sin(tSec * 1.8f + i) * 45f) % w + w) % w
                    val ey = h - ((i * 71f + tSec * 195f) % h)
                    val r = 3f + (i % 4) * 1.4f
                    canvas.drawCircle(ex, ey, r * 2.5f, glowP)
                    canvas.drawCircle(ex, ey, r, coreP)
                }
            }
            VfxEffectType.CAMERA_SHAKE -> Unit
            VfxEffectType.LIGHT_LEAK -> {
                val cx = w * (0.14f + 0.12f * sin(tSec))
                val cy = h * (0.18f + 0.08f * cos(tSec * 0.7f))
                val p = Paint().apply {
                    shader = RadialGradient(cx, cy, maxOf(w, h) * 0.72f, intArrayOf(((intensity * 150).toInt() shl 24) or 0xFF8C00, ((intensity * 65).toInt() shl 24) or 0xFF2E63, 0x00FF7A00), null, Shader.TileMode.CLAMP)
                }
                canvas.drawRect(0f, 0f, w, h, p)
            }
            VfxEffectType.LETTERBOX_239 -> {
                val barH = h * 0.12f * intensity
                val p = Paint().apply { color = android.graphics.Color.BLACK }
                canvas.drawRect(0f, 0f, w, barH, p)
                canvas.drawRect(0f, h - barH, w, h, p)
            }
            VfxEffectType.VOLUMETRIC_GOD_RAYS -> {
                val ox = w * 0.12f
                val oy = -h * 0.1f
                for (idx in 0 until 5) {
                    val sweep = 0.18f + idx * 0.16f + 0.04f * sin(tSec * 0.9f + idx)
                    val rayPath = Path().apply {
                        moveTo(ox, oy)
                        lineTo(w * sweep, h)
                        lineTo(w * (sweep + 0.12f), h)
                        close()
                    }
                    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        shader = LinearGradient(ox, 0f, w * sweep, h, ((intensity * 78).toInt() shl 24) or 0xFFE082, 0x00FFE082, Shader.TileMode.CLAMP)
                    }
                    canvas.drawPath(rayPath, p)
                }
            }
            VfxEffectType.GOLD_DUST_BOKEH -> {
                val bRand = Random(99)
                val count = (18 * intensity).toInt().coerceAtLeast(6)
                for (idx in 0 until count) {
                    val bx = ((bRand.nextFloat() + 0.05f * sin(tSec + idx * 1.3f)) % 1f) * w
                    val by = ((bRand.nextFloat() + 0.04f * cos(tSec * 0.8f + idx)) % 1f) * h
                    val r = (w * 0.025f) + (idx % 5) * (w * 0.012f)
                    val colHex = if (idx % 2 == 0) 0xFFB300 else 0x00E5FF
                    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        shader = RadialGradient(bx, by, r, intArrayOf(((intensity * 72).toInt() shl 24) or colHex, 0x00000000), null, Shader.TileMode.CLAMP)
                    }
                    canvas.drawCircle(bx, by, r, p)
                }
            }
            VfxEffectType.DREAMY_HALATION -> {
                val p = Paint().apply {
                    shader = RadialGradient(w * 0.5f, h * 0.45f, maxOf(w, h) * 0.7f, intArrayOf(((intensity * 95).toInt() shl 24) or 0xFFE4B5, ((intensity * 50).toInt() shl 24) or 0xFF9E80, 0x00000000), null, Shader.TileMode.CLAMP)
                }
                canvas.drawRect(0f, 0f, w, h, p)
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

        // Progressive Text Reveal (Typewriter, Char Reveal, Word Pop/Cascade, Line-by-Line Reveal)
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
        canvas.translate(-layout.width / 2f, -layout.height / 2f + if (xform.splitCharWave > 0.05f) sin(playheadMs * 0.02f) * 4f * xform.splitCharWave else 0f)

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
            StaticLayout.Builder.obtain(drawStr, 0, drawStr.length, strokePaint, maxBoxW)
                .setAlignment(layoutAlign)
                .setLineSpacing(0f, cfg.lineHeightMult.coerceIn(0.85f, 2.2f))
                .build()
                .draw(canvas)
        }

        layout.draw(canvas)
        canvas.restore()

        if (xform.underlineProgress > 0.02f) {
            val ulPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
            val startX = -boxW / 2f + 12f * scaleFactor
            val endX = startX + (boxW - 24f * scaleFactor) * xform.underlineProgress
            canvas.drawRoundRect(
                RectF(startX, layout.height / 2f + 4f * scaleFactor, endX, layout.height / 2f + 8f * scaleFactor),
                3f, 3f, ulPaint
            )
        }

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
        var tryAgainRetries = 0
        val maxEndOfStreamRetries = 80
        while (true) {
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
            when {
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream || tryAgainRetries >= maxEndOfStreamRetries) break
                    tryAgainRetries++
                }
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    tryAgainRetries = 0
                    onFormatChanged(codec.outputFormat)
                }
                outputIndex >= 0 -> {
                    tryAgainRetries = 0
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

    private fun saveMp4ToGallery(context: Context, sourceFile: File, displayName: String): Pair<Uri?, String> {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/ChitraFlow Studio")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val mediaUri = runCatching { resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) }.getOrNull()
        if (mediaUri != null) {
            val copied = runCatching {
                resolver.openOutputStream(mediaUri)?.use { out ->
                    sourceFile.inputStream().use { input -> input.copyTo(out) }
                    out.flush()
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(mediaUri, values, null, null)
                }
                true
            }.getOrDefault(false)
            if (copied) {
                return mediaUri to "Movies/ChitraFlow Studio/$displayName"
            } else {
                runCatching { resolver.delete(mediaUri, null, null) }
            }
        }

        // Scoped fallback if MediaStore insertion was unavailable
        val fallbackDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir, "ChitraFlow Studio").apply {
            mkdirs()
        }
        val destFile = File(fallbackDir, displayName)
        sourceFile.inputStream().use { input ->
            destFile.outputStream().use { out -> input.copyTo(out) }
        }
        runCatching {
            MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), arrayOf("video/mp4"), null)
        }
        return Uri.fromFile(destFile) to destFile.absolutePath
    }

    fun resolveDrawableId(name: String): Int = when (name) {
        "img_studio_hero" -> R.drawable.img_studio_hero
        "img_tpl_documentary" -> R.drawable.img_tpl_documentary
        "img_tpl_motion" -> R.drawable.img_tpl_motion
        "img_tpl_street_cinema" -> R.drawable.img_tpl_street_cinema
        else -> R.drawable.img_tpl_documentary
    }
}
