package com.example.domain.engine

import android.content.Context
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.view.Surface
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.domain.engine.export.AdaptiveMemoryManager
import com.example.domain.engine.export.DeviceCapabilityAnalyzer
import com.example.domain.engine.export.ExportCodec
import com.example.domain.engine.export.ExportConfiguration
import com.example.domain.engine.export.ExportErrorCategory
import com.example.domain.engine.export.ExportFrameRenderer
import com.example.domain.engine.export.ExportMode
import com.example.domain.engine.export.ExportProgressSnapshot
import com.example.domain.engine.export.ExportQuality
import com.example.domain.engine.export.ExportRecoveryRecord
import com.example.domain.engine.export.ExportRecoveryStore
import com.example.domain.engine.export.ExportStorageManager
import com.example.domain.engine.export.ExportThermalLevel
import com.example.domain.engine.export.MemorySafeMediaFrameProvider
import com.example.domain.engine.export.ResolvedEncoderChoice
import com.example.domain.engine.export.StreamingAudioExportSession
import com.example.domain.engine.export.StreamingExportTarget
import com.example.domain.engine.export.ThermalMonitor
import com.example.domain.model.AspectRatioPreset
import com.example.domain.model.ExportFps
import com.example.domain.model.PreviewResolution
import com.example.domain.model.StudioFontOption
import com.example.domain.model.StudioProject
import com.example.domain.model.TimelineClip
import com.example.domain.model.TrackType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

data class ExportResult(
    val success: Boolean,
    val savedLocationLabel: String,
    val outputUri: Uri? = null,
    val errorMessage: String? = null,
    val errorCategory: ExportErrorCategory? = null,
    val fileSizeBytes: Long = 0L,
    val encoderUsed: String = "",
    val elapsedMs: Long = 0L,
    val peakMemoryMb: Long = 0L,
    val verifiedIntegrity: Boolean = false
)

object VideoExportEngine {

    suspend fun exportStandaloneMapToMp4(
        context: Context,
        mapConfig: com.example.domain.map.UniversalMapConfig,
        aspectRatio: AspectRatioPreset = AspectRatioPreset.LANDSCAPE_16_9,
        resolution: PreviewResolution = PreviewResolution.P1080,
        fpsOption: ExportFps = ExportFps.FPS30,
        onProgress: (Float, String) -> Unit
    ): ExportResult {
        val dur = mapConfig.durationMs.coerceIn(2_000L, 60_000L)
        val mapClip = TimelineClip(
            trackType = TrackType.GRAPHICS,
            title = mapConfig.title.ifBlank { "Map Animation" },
            startMs = 0L,
            durationMs = dur,
            sourceDurationMs = dur,
            mapConfig = mapConfig
        )
        val soloProject = StudioProject(
            name = mapConfig.title.ifBlank { "ChitraFlow_Map" },
            aspectRatio = aspectRatio,
            previewResolution = resolution,
            totalDurationMs = dur,
            clips = listOf(mapClip)
        )
        return exportProjectToMp4(
            context = context,
            project = soloProject,
            resolution = resolution,
            fpsOption = fpsOption,
            fastMode = false,
            onProgress = onProgress
        )
    }

    suspend fun exportProjectToMp4(
        context: Context,
        project: StudioProject,
        resolution: PreviewResolution,
        fpsOption: ExportFps,
        fastMode: Boolean = true,
        onProgress: (Float, String) -> Unit
    ): ExportResult {
        val cfg = ExportConfiguration(
            resolution = resolution,
            fps = fpsOption,
            codec = ExportCodec.H264,
            quality = if (fastMode) ExportQuality.MEDIUM else ExportQuality.HIGH,
            mode = if (fastMode) ExportMode.FAST_EXPORT else ExportMode.HIGH_QUALITY
        )
        return exportProjectStreaming(
            context = context,
            project = project,
            requestedConfig = cfg,
            pauseCheck = { false },
            onSnapshot = { snap -> onProgress(snap.progress, snap.statusText) }
        )
    }

    suspend fun exportProjectStreaming(
        context: Context,
        project: StudioProject,
        requestedConfig: ExportConfiguration,
        pauseCheck: () -> Boolean = { false },
        onSnapshot: (ExportProgressSnapshot) -> Unit
    ): ExportResult = withContext(Dispatchers.Default) {
        val exportId = UUID.randomUUID().toString()
        val startWallMs = SystemClock.elapsedRealtime()
        var totalPausedMs = 0L

        ExportStorageManager.cleanupStaleTempFiles(context)

        val capabilityReport = DeviceCapabilityAnalyzer.inspectDevice(context, project.aspectRatio)
        val effectiveConfig = if (requestedConfig.mode == ExportMode.SMART_AUTO) {
            DeviceCapabilityAnalyzer.resolveSmartAutoConfig(capabilityReport, project, requestedConfig)
        } else {
            val safeRes = DeviceCapabilityAnalyzer.clampResolutionToReport(requestedConfig.resolution, capabilityReport)
            val allowedFps = capabilityReport.supportedFpsByResolution[safeRes].orEmpty()
            val safeFps = if (requestedConfig.fps in allowedFps) requestedConfig.fps else (allowedFps.firstOrNull() ?: ExportFps.FPS30)
            val safeCodec = if (requestedConfig.codec in capabilityReport.supportedCodecs) requestedConfig.codec else ExportCodec.H264
            requestedConfig.copy(resolution = safeRes, fps = safeFps, codec = safeCodec)
        }

        val (width, height) = DeviceCapabilityAnalyzer.computeExportDimensions(project.aspectRatio, effectiveConfig.resolution)
        val fps = effectiveConfig.fps.fps
        val totalDurationMs = project.totalDurationMs.coerceIn(1_000L, MediaProbeHelper.MAX_TIMELINE_DURATION_MS)
        val totalFrames = ((totalDurationMs / 1000.0) * fps).toInt().coerceAtLeast(fps)
        val bitrate = DeviceCapabilityAnalyzer.computeTargetBitrate(
            resolution = effectiveConfig.resolution,
            fps = effectiveConfig.fps,
            codec = effectiveConfig.codec,
            quality = effectiveConfig.quality,
            mode = effectiveConfig.mode
        )
        val estimatedTotalBytes = ExportStorageManager.estimateOutputBytes(totalDurationMs, bitrate)

        // 1. Pre-flight Storage Check (prevents partial 5GB crash mid-export)
        val storageCheck = ExportStorageManager.checkStorageForExport(context, totalDurationMs, bitrate)
        if (!storageCheck.hasEnoughSpace) {
            return@withContext ExportResult(
                success = false,
                savedLocationLabel = "",
                errorMessage = storageCheck.messageBn,
                errorCategory = ExportErrorCategory.STORAGE_FULL
            )
        }

        val memManager = AdaptiveMemoryManager(context, width, height, effectiveConfig.resolution)
        val thermalMonitor = ThermalMonitor(context)
        memManager.register()
        thermalMonitor.start()

        var streamingTarget: StreamingExportTarget? = null
        var videoCodec: MediaCodec? = null
        var inputSurface: Surface? = null
        var audioSession: StreamingAudioExportSession? = null
        var mediaProvider: MemorySafeMediaFrameProvider? = null
        var muxerStarted = false

        try {
            coroutineContext.ensureActive()

            val initialThermal = thermalMonitor.pollCurrentLevel()
            var memProfile = memManager.evaluateProfile(initialThermal)

            onSnapshot(
                ExportProgressSnapshot(
                    exportId = exportId,
                    progress = 0.02f,
                    statusText = "ডিভাইস ও স্টোরেজ যাচাই হচ্ছে ($width×$height · ${effectiveConfig.codec.shortLabel})...",
                    elapsedMs = 0L,
                    estimatedRemainingMs = 0L,
                    resolutionLabel = effectiveConfig.resolution.label,
                    fps = fps,
                    codecLabel = effectiveConfig.codec.shortLabel,
                    encoderInfo = "Preparing...",
                    isHardwareEncoder = capabilityReport.hasHardwareH264 || capabilityReport.hasHardwareH265,
                    outputBytesWritten = 0L,
                    estimatedTotalBytes = estimatedTotalBytes,
                    currentFrame = 0,
                    totalFrames = totalFrames,
                    isPaused = false,
                    thermalLevel = initialThermal,
                    memoryTier = memProfile.tier,
                    availableRamMb = memProfile.availableSystemRamMb
                )
            )

            // 2. Open zero-copy streaming destination in Gallery / MediaStore
            val safeName = project.name.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "ChitraFlow_Video" }
            val fileName = "${safeName}_${effectiveConfig.resolution.label}_${System.currentTimeMillis()}.mp4"
            streamingTarget = try {
                ExportStorageManager.openStreamingTarget(context, fileName)
            } catch (sec: SecurityException) {
                throw ExportStageException(ExportErrorCategory.FILE_ACCESS_DENIED, "গ্যালারি বা স্টোরেজ অনুমতি পাওয়া যায়নি", sec)
            } catch (t: Throwable) {
                throw ExportStageException(ExportErrorCategory.FILE_ACCESS_DENIED, t.message ?: "আউটপুট ফাইল খোলা যায়নি", t)
            }

            // 3. Save active recovery record
            ExportRecoveryStore.saveRecord(
                context,
                ExportRecoveryRecord(
                    exportId = exportId,
                    projectId = project.id,
                    projectName = project.name,
                    resolutionName = effectiveConfig.resolution.name,
                    fps = fps,
                    codecName = effectiveConfig.codec.name,
                    qualityName = effectiveConfig.quality.name,
                    modeName = effectiveConfig.mode.name,
                    targetUriString = streamingTarget.mediaUri?.toString(),
                    tempFilePath = streamingTarget.file?.absolutePath,
                    lastCompletedFrame = 0,
                    totalFrames = totalFrames,
                    timestampMs = System.currentTimeMillis(),
                    errorCategoryName = null,
                    failureReason = null
                )
            )

            // 4. Initialize Hardware Encoder with automatic Software fallback chain
            val encoderChain = DeviceCapabilityAnalyzer.resolveEncoderChain(capabilityReport, effectiveConfig.codec)
            val (openedCodec, openedSurface, chosenEncoder) = openEncoderFromChain(
                chain = encoderChain,
                width = width,
                height = height,
                fps = fps,
                bitrate = bitrate,
                iFrameInterval = effectiveConfig.quality.iFrameIntervalSec
            )
            videoCodec = openedCodec
            inputSurface = openedSurface

            val encoderLabel = "${if (chosenEncoder.isHardwareAccelerated) "HW" else "SW"} · ${chosenEncoder.encoderName ?: chosenEncoder.codec.shortLabel}"

            // 5. Prepare constant-RAM streaming audio mixer + AAC encoder
            audioSession = StreamingAudioExportSession(
                context = context,
                project = project,
                chunkSamples = memProfile.chunkedAudioBufferSamples
            ).also { session ->
                if (!session.prepare()) {
                    throw ExportStageException(ExportErrorCategory.AUDIO_PROCESSING_FAILED, "অডিও এনকোডার প্রস্তুত করা যায়নি")
                }
            }

            // 6. Initialize bounded media frame provider, fonts & reusable renderer
            val fastRender = effectiveConfig.mode == ExportMode.FAST_EXPORT
            mediaProvider = MemorySafeMediaFrameProvider(
                context = context,
                project = project,
                targetW = width,
                targetH = height,
                fastMode = fastRender,
                memoryProfile = memProfile,
                drawableResolver = ::resolveDrawableId
            )
            val fontCache = preloadTypefaces(context, project)
            val frameRenderer = ExportFrameRenderer()

            val activeMuxer = streamingTarget.muxer
            var videoTrackIndex = -1
            var audioTrackIndex = -1
            val bufferInfo = MediaCodec.BufferInfo()
            val frameDurationUs = 1_000_000L / fps
            var hardwareCanvasSupported = memProfile.preferHardwareCanvas
            var muxedVideoFrameCount = 0L

            fun nextVideoPtsUs(): Long {
                val pts = muxedVideoFrameCount * frameDurationUs
                muxedVideoFrameCount++
                return pts
            }

            fun startMuxerIfReady(videoOutputFormat: MediaFormat) {
                if (muxerStarted) return
                videoTrackIndex = activeMuxer.addTrack(videoOutputFormat)
                val audioFmt = audioSession?.audioOutputFormat
                if (audioFmt != null) {
                    audioTrackIndex = activeMuxer.addTrack(audioFmt)
                }
                activeMuxer.start()
                muxerStarted = true
            }

            // 7. Streaming Frame-by-Frame Render + Interleaved A/V Muxing Loop
            var frameIdx = 0
            while (frameIdx < totalFrames) {
                coroutineContext.ensureActive()

                // Real Pause / Resume Gate (suspends cleanly without closing encoder/muxer)
                if (pauseCheck()) {
                    val pauseEnteredAt = SystemClock.elapsedRealtime()
                    val currentElapsed = pauseEnteredAt - startWallMs - totalPausedMs
                    onSnapshot(
                        ExportProgressSnapshot(
                            exportId = exportId,
                            progress = 0.05f + 0.90f * (frameIdx.toFloat() / totalFrames.toFloat()),
                            statusText = "এক্সপোর্ট বিরতি (Paused) · ${frameIdx}/$totalFrames ফ্রেম",
                            elapsedMs = currentElapsed,
                            estimatedRemainingMs = 0L,
                            resolutionLabel = effectiveConfig.resolution.label,
                            fps = fps,
                            codecLabel = chosenEncoder.codec.shortLabel,
                            encoderInfo = encoderLabel,
                            isHardwareEncoder = chosenEncoder.isHardwareAccelerated,
                            outputBytesWritten = estimateWrittenBytes(streamingTarget, frameIdx, totalFrames, estimatedTotalBytes),
                            estimatedTotalBytes = estimatedTotalBytes,
                            currentFrame = frameIdx,
                            totalFrames = totalFrames,
                            isPaused = true,
                            thermalLevel = thermalMonitor.pollCurrentLevel(),
                            memoryTier = memProfile.tier,
                            availableRamMb = memProfile.availableSystemRamMb
                        )
                    )
                    while (pauseCheck()) {
                        coroutineContext.ensureActive()
                        delay(180L)
                    }
                    totalPausedMs += (SystemClock.elapsedRealtime() - pauseEnteredAt)
                }

                // Adaptive Memory & Thermal Check every 15 frames
                if (frameIdx % 15 == 0) {
                    val thermalNow = thermalMonitor.pollCurrentLevel()
                    memProfile = memManager.evaluateProfile(thermalNow)
                    mediaProvider.updateMemoryProfile(memProfile)

                    if (thermalNow == ExportThermalLevel.CRITICAL) {
                        // Safe cooldown window before escalating error
                        delay(800L)
                        if (thermalMonitor.pollCurrentLevel() == ExportThermalLevel.CRITICAL) {
                            throw ExportStageException(
                                ExportErrorCategory.THERMAL_RESTRICTED,
                                "ডিভাইস অতিরিক্ত গরম হয়ে যাওয়ায় সুরক্ষার জন্য এক্সপোর্ট বিরতি দেওয়া হয়েছে।"
                            )
                        }
                    } else if (memProfile.thermalYieldMs > 0L) {
                        delay(memProfile.thermalYieldMs)
                    }
                }

                // Mid-export storage space safety check every 90 frames
                if (frameIdx > 0 && frameIdx % 90 == 0) {
                    if (!ExportStorageManager.hasMinimumRuntimeHeadroom(context)) {
                        throw ExportStageException(
                            ExportErrorCategory.STORAGE_FULL,
                            "রেন্ডার চলাকালীন স্টোরেজ জায়গা শেষ হয়ে গেছে।"
                        )
                    }
                    ExportRecoveryStore.updateProgressFrame(context, exportId, frameIdx)
                }

                val playheadMs = ((frameIdx.toDouble() / fps.toDouble()) * 1000.0).toLong()
                val canvas = if (hardwareCanvasSupported) {
                    try {
                        openedSurface.lockHardwareCanvas()
                    } catch (_: Throwable) {
                        hardwareCanvasSupported = false
                        openedSurface.lockCanvas(null)
                    }
                } else {
                    openedSurface.lockCanvas(null)
                }

                try {
                    frameRenderer.renderProjectFrame(
                        canvas = canvas,
                        width = width,
                        height = height,
                        project = project,
                        playheadMs = playheadMs,
                        mediaProvider = mediaProvider,
                        fontCache = fontCache
                    )
                } catch (oom: OutOfMemoryError) {
                    mediaProvider.release()
                    System.gc()
                    throw ExportStageException(
                        ExportErrorCategory.OUT_OF_MEMORY,
                        "ফ্রেম প্রসেসিংয়ে অতিরিক্ত মেমোরি শেষ হয়ে গেছে। কম রেজুলেশন ব্যবহার করুন।",
                        oom
                    )
                } finally {
                    openedSurface.unlockCanvasAndPost(canvas)
                }

                val ptsUs = frameIdx * frameDurationUs
                drainVideoEncoder(
                    codec = openedCodec,
                    muxer = activeMuxer,
                    bufferInfo = bufferInfo,
                    endOfStream = false,
                    onFormatChanged = { fmt -> startMuxerIfReady(fmt) },
                    isMuxerStarted = { muxerStarted },
                    getTrackIndex = { videoTrackIndex },
                    nextMonotonicPtsUs = ::nextVideoPtsUs
                )

                // Pump matching audio chunk synchronously so audio/video stay tightly interleaved
                audioSession.pumpAudioUpToPtsUs(
                    targetPtsUs = ptsUs + frameDurationUs,
                    muxer = activeMuxer,
                    audioTrackIndex = audioTrackIndex,
                    muxerStarted = muxerStarted
                )

                if (frameIdx % 4 == 0 || frameIdx == totalFrames - 1) {
                    val activeElapsedMs = (SystemClock.elapsedRealtime() - startWallMs - totalPausedMs).coerceAtLeast(1L)
                    val doneFrac = ((frameIdx + 1).toFloat() / totalFrames.toFloat()).coerceIn(0.001f, 1f)
                    val estRemainingMs = if (frameIdx >= 4) {
                        ((activeElapsedMs / doneFrac) * (1f - doneFrac)).toLong().coerceAtLeast(0L)
                    } else 0L
                    val thermalNow = thermalMonitor.pollCurrentLevel()
                    onSnapshot(
                        ExportProgressSnapshot(
                            exportId = exportId,
                            progress = 0.04f + 0.90f * doneFrac,
                            statusText = "রেন্ডারিং ${frameIdx + 1}/$totalFrames (${effectiveConfig.resolution.label} · ${chosenEncoder.codec.shortLabel})",
                            elapsedMs = activeElapsedMs,
                            estimatedRemainingMs = estRemainingMs,
                            resolutionLabel = effectiveConfig.resolution.label,
                            fps = fps,
                            codecLabel = chosenEncoder.codec.shortLabel,
                            encoderInfo = encoderLabel,
                            isHardwareEncoder = chosenEncoder.isHardwareAccelerated,
                            outputBytesWritten = estimateWrittenBytes(streamingTarget, frameIdx + 1, totalFrames, estimatedTotalBytes),
                            estimatedTotalBytes = estimatedTotalBytes,
                            currentFrame = frameIdx + 1,
                            totalFrames = totalFrames,
                            isPaused = false,
                            thermalLevel = thermalNow,
                            memoryTier = memProfile.tier,
                            availableRamMb = memProfile.availableSystemRamMb
                        )
                    )
                }
                frameIdx++
            }

            // 8. Signal Video & Audio EOS and flush remaining interleaved frames
            openedCodec.signalEndOfInputStream()
            drainVideoEncoder(
                codec = openedCodec,
                muxer = activeMuxer,
                bufferInfo = bufferInfo,
                endOfStream = true,
                onFormatChanged = { fmt -> startMuxerIfReady(fmt) },
                isMuxerStarted = { muxerStarted },
                getTrackIndex = { videoTrackIndex },
                nextMonotonicPtsUs = ::nextVideoPtsUs
            )

            if (!muxerStarted) {
                throw ExportStageException(
                    ExportErrorCategory.MUXING_FAILED,
                    "ভিডিও ট্র্যাক এনকোডারে শুরু করা যায়নি।"
                )
            }

            audioSession.finishAndDrain(
                muxer = activeMuxer,
                audioTrackIndex = audioTrackIndex,
                muxerStarted = muxerStarted
            )

            // Release codecs and stop muxer cleanly before publishing MediaStore entry
            mediaProvider.release()
            mediaProvider = null
            audioSession.release()
            audioSession = null
            runCatching { openedSurface.release() }
            inputSurface = null
            runCatching { openedCodec.stop() }
            runCatching { openedCodec.release() }
            videoCodec = null

            runCatching { activeMuxer.stop() }.getOrElse { t ->
                throw ExportStageException(ExportErrorCategory.MUXING_FAILED, "MP4 ফাইল ফাইনাল করতে সমস্যা হয়েছে", t)
            }
            runCatching { activeMuxer.release() }

            // 9. Publish MediaStore entry & verify final MP4 integrity
            val (finalUri, finalSize) = streamingTarget.finalizeSuccess(context)
            val integrityOk = ExportStorageManager.verifyFinalMp4Integrity(context, finalUri, finalSize)
            if (!integrityOk) {
                streamingTarget.abortAndCleanup(context)
                throw ExportStageException(
                    ExportErrorCategory.MUXING_FAILED,
                    "তৈরি হওয়া MP4 ফাইল যাচাইয়ে ব্যর্থ হয়েছে — অসম্পূর্ণ ফাইল মুছে ফেলা হয়েছে।"
                )
            }

            ExportRecoveryStore.clearRecord(context)

            val totalElapsed = (SystemClock.elapsedRealtime() - startWallMs - totalPausedMs).coerceAtLeast(1L)
            onSnapshot(
                ExportProgressSnapshot(
                    exportId = exportId,
                    progress = 1f,
                    statusText = "এক্সপোর্ট সম্পন্ন!",
                    elapsedMs = totalElapsed,
                    estimatedRemainingMs = 0L,
                    resolutionLabel = effectiveConfig.resolution.label,
                    fps = fps,
                    codecLabel = chosenEncoder.codec.shortLabel,
                    encoderInfo = encoderLabel,
                    isHardwareEncoder = chosenEncoder.isHardwareAccelerated,
                    outputBytesWritten = finalSize,
                    estimatedTotalBytes = finalSize.coerceAtLeast(1L),
                    currentFrame = totalFrames,
                    totalFrames = totalFrames,
                    isPaused = false,
                    thermalLevel = thermalMonitor.pollCurrentLevel(),
                    memoryTier = memProfile.tier,
                    availableRamMb = memProfile.availableSystemRamMb
                )
            )

            ExportResult(
                success = true,
                savedLocationLabel = streamingTarget.savedLocationLabel,
                outputUri = finalUri,
                fileSizeBytes = finalSize,
                encoderUsed = encoderLabel,
                elapsedMs = totalElapsed,
                peakMemoryMb = memManager.peakHeapUsedMb,
                verifiedIntegrity = true
            )
        } catch (ce: CancellationException) {
            releaseExportResources(mediaProvider, audioSession, inputSurface, videoCodec, streamingTarget, muxerStarted, abortOutput = true, context = context)
            ExportRecoveryStore.clearRecord(context)
            throw ce
        } catch (t: Throwable) {
            releaseExportResources(mediaProvider, audioSession, inputSurface, videoCodec, streamingTarget, muxerStarted, abortOutput = true, context = context)
            val (category, msg) = classifyThrowable(t)
            ExportRecoveryStore.markFailed(context, exportId, category, msg)
            ExportResult(
                success = false,
                savedLocationLabel = "",
                errorMessage = msg,
                errorCategory = category,
                elapsedMs = (SystemClock.elapsedRealtime() - startWallMs - totalPausedMs).coerceAtLeast(0L),
                peakMemoryMb = memManager.peakHeapUsedMb,
                verifiedIntegrity = false
            )
        } finally {
            thermalMonitor.stop()
            memManager.unregister()
        }
    }

    private fun openEncoderFromChain(
        chain: List<ResolvedEncoderChoice>,
        width: Int,
        height: Int,
        fps: Int,
        bitrate: Int,
        iFrameInterval: Int
    ): Triple<MediaCodec, Surface, ResolvedEncoderChoice> {
        var lastError: Throwable? = null
        for (candidate in chain) {
            var codec: MediaCodec? = null
            var surface: Surface? = null
            try {
                val format = MediaFormat.createVideoFormat(candidate.codec.mimeType, width, height).apply {
                    setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                    setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                    setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iFrameInterval)
                }
                codec = if (candidate.encoderName != null) {
                    MediaCodec.createByCodecName(candidate.encoderName)
                } else {
                    MediaCodec.createEncoderByType(candidate.codec.mimeType)
                }
                codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                surface = codec.createInputSurface()
                codec.start()
                return Triple(codec, surface, candidate)
            } catch (t: Throwable) {
                lastError = t
                runCatching { surface?.release() }
                runCatching { codec?.stop() }
                runCatching { codec?.release() }
            }
        }
        throw ExportStageException(
            ExportErrorCategory.ENCODER_INIT_FAILED,
            lastError?.message ?: "হার্ডওয়্যার বা সফটওয়্যার ভিডিও এনকোডার চালু করা যায়নি",
            lastError
        )
    }

    private fun drainVideoEncoder(
        codec: MediaCodec,
        muxer: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        endOfStream: Boolean,
        onFormatChanged: (MediaFormat) -> Unit,
        isMuxerStarted: () -> Boolean,
        getTrackIndex: () -> Int,
        nextMonotonicPtsUs: () -> Long
    ) {
        val timeoutUs = if (endOfStream) 10_000L else 0L
        var tryAgainRetries = 0
        val maxEndOfStreamRetries = 100
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
                    val encodedData = codec.getOutputBuffer(outputIndex)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        bufferInfo.size = 0
                    }
                    if (encodedData != null && bufferInfo.size != 0 && isMuxerStarted()) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        bufferInfo.presentationTimeUs = nextMonotonicPtsUs()
                        muxer.writeSampleData(getTrackIndex(), encodedData, bufferInfo)
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                }
            }
        }
    }

    private fun estimateWrittenBytes(
        target: StreamingExportTarget,
        doneFrames: Int,
        totalFrames: Int,
        estTotal: Long
    ): Long {
        val actual = target.currentWrittenBytes()
        if (actual > 0L) return actual
        val frac = (doneFrames.toDouble() / totalFrames.coerceAtLeast(1).toDouble()).coerceIn(0.0, 1.0)
        return (estTotal * frac).toLong()
    }

    private fun releaseExportResources(
        mediaProvider: MemorySafeMediaFrameProvider?,
        audioSession: StreamingAudioExportSession?,
        inputSurface: Surface?,
        videoCodec: MediaCodec?,
        streamingTarget: StreamingExportTarget?,
        muxerStarted: Boolean,
        abortOutput: Boolean,
        context: Context
    ) {
        runCatching { mediaProvider?.release() }
        runCatching { audioSession?.release() }
        runCatching { inputSurface?.release() }
        runCatching { videoCodec?.stop() }
        runCatching { videoCodec?.release() }
        if (streamingTarget != null) {
            if (muxerStarted) runCatching { streamingTarget.muxer.stop() }
            runCatching { streamingTarget.muxer.release() }
            if (abortOutput) {
                streamingTarget.abortAndCleanup(context)
            }
        }
    }

    private fun classifyThrowable(t: Throwable): Pair<ExportErrorCategory, String> {
        if (t is ExportStageException) return t.category to t.userMessage
        if (t is OutOfMemoryError) return ExportErrorCategory.OUT_OF_MEMORY to "মেমোরি (RAM) সীমা অতিক্রম করেছে। কম রেজুলেশন বা Fast Mode ব্যবহার করুন।"
        if (t is IOException) {
            val msg = t.message.orEmpty()
            return if (msg.contains("ENOSPC", ignoreCase = true) || msg.contains("space", ignoreCase = true)) {
                ExportErrorCategory.STORAGE_FULL to "ডিভাইসের স্টোরেজ পূর্ণ হয়ে গেছে।"
            } else {
                ExportErrorCategory.FILE_ACCESS_DENIED to (t.message ?: "ফাইল লেখায় সমস্যা হয়েছে")
            }
        }
        if (t is SecurityException) {
            return ExportErrorCategory.FILE_ACCESS_DENIED to "স্টোরেজ বা মিডিয়া ফাইল পড়ার অনুমতি পাওয়া যায়নি।"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && t is MediaCodec.CodecException) {
            return ExportErrorCategory.ENCODER_INIT_FAILED to "ভিডিও এনকোডার হার্ডওয়্যার ত্রুটি: ${t.diagnosticInfo}"
        }
        return ExportErrorCategory.UNKNOWN to (t.message ?: "ভিডিও এক্সপোর্টে সমস্যা হয়েছে")
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

    fun resolveDrawableId(name: String): Int = when (name) {
        "img_studio_hero" -> R.drawable.img_studio_hero
        "img_tpl_documentary" -> R.drawable.img_tpl_documentary
        "img_tpl_motion" -> R.drawable.img_tpl_motion
        "img_tpl_street_cinema" -> R.drawable.img_tpl_street_cinema
        else -> R.drawable.img_tpl_documentary
    }

    private class ExportStageException(
        val category: ExportErrorCategory,
        val userMessage: String,
        cause: Throwable? = null
    ) : RuntimeException(userMessage, cause)
}
