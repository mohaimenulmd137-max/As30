package com.example.domain.engine.export

import android.app.ActivityManager
import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.opengl.EGL14
import android.os.Build
import com.example.domain.model.AspectRatioPreset
import com.example.domain.model.ExportFps
import com.example.domain.model.PreviewResolution
import com.example.domain.model.StudioProject
import com.example.domain.model.TrackType

object DeviceCapabilityAnalyzer {

    fun computeExportDimensions(aspectRatio: AspectRatioPreset, resolution: PreviewResolution): Pair<Int, Int> {
        val shortEdge = resolution.shortEdge
        val (w, h) = when (aspectRatio) {
            AspectRatioPreset.PORTRAIT_9_16 -> shortEdge to (shortEdge * 16 / 9)
            AspectRatioPreset.LANDSCAPE_16_9 -> (shortEdge * 16 / 9) to shortEdge
            AspectRatioPreset.SQUARE_1_1 -> shortEdge to shortEdge
        }
        return ((w / 16) * 16).coerceAtLeast(16) to ((h / 16) * 16).coerceAtLeast(16)
    }

    fun inspectDevice(context: Context, aspectRatio: AspectRatioPreset = AspectRatioPreset.LANDSCAPE_16_9): EncoderCapabilityReport {
        val am = runCatching { context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager }.getOrNull()
        val memInfo = ActivityManager.MemoryInfo()
        runCatching { am?.getMemoryInfo(memInfo) }

        val totalRamMb = (memInfo.totalMem / (1024L * 1024L)).takeIf { it > 0L } ?: 2048L
        val availRamMb = (memInfo.availMem / (1024L * 1024L)).takeIf { it > 0L } ?: 1024L
        val heapLimitMb = (Runtime.getRuntime().maxMemory() / (1024L * 1024L)).coerceAtLeast(64L)
        val isLowRam = (am?.isLowRamDevice == true) || totalRamMb <= 3072L || heapLimitMb <= 256L

        val codecInfos = runCatching {
            MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.toList()
        }.getOrElse { emptyList() }

        val h264Candidates = findEncodersForMime(codecInfos, ExportCodec.H264)
        val h265Candidates = findEncodersForMime(codecInfos, ExportCodec.H265)

        val prefH264 = selectBestEncoder(h264Candidates, ExportCodec.H264)
        val prefH265 = selectBestEncoder(h265Candidates, ExportCodec.H265)

        val supportedCodecs = buildSet {
            if (prefH264 != null) add(ExportCodec.H264)
            if (prefH265 != null) add(ExportCodec.H265)
            // Fallback guarantee if running inside headless/Robolectric unit test where MediaCodecList is empty
            if (isEmpty() && codecInfos.isEmpty()) add(ExportCodec.H264)
        }

        val activeEncoders = h264Candidates + h265Candidates
        val supportedResolutions = mutableSetOf<PreviewResolution>()
        val supportedFpsByRes = mutableMapOf<PreviewResolution, Set<ExportFps>>()

        PreviewResolution.entries.forEach { res ->
            val (w, h) = computeExportDimensions(aspectRatio, res)
            val compatible = if (activeEncoders.isEmpty()) {
                res != PreviewResolution.UHD4K || !isLowRam
            } else {
                activeEncoders.any { isSizeSupported(it, res.mimeHint(), w, h) }
            }
            if (compatible) {
                supportedResolutions.add(res)
                val fpsSet = ExportFps.entries.filter { fps ->
                    if (activeEncoders.isEmpty()) {
                        fps.fps <= 30 || !isLowRam
                    } else {
                        activeEncoders.any { isSizeAndRateSupported(it, res.mimeHint(), w, h, fps.fps) }
                    }
                }.toSet().ifEmpty { setOf(ExportFps.FPS30) }
                supportedFpsByRes[res] = fpsSet
            }
        }

        if (supportedResolutions.isEmpty()) {
            supportedResolutions.add(PreviewResolution.P720)
            supportedFpsByRes[PreviewResolution.P720] = setOf(ExportFps.FPS30)
        }

        return EncoderCapabilityReport(
            supportedResolutions = supportedResolutions,
            supportedFpsByResolution = supportedFpsByRes,
            supportedCodecs = supportedCodecs,
            hasHardwareH264 = h264Candidates.any { isHardwareCodec(it) },
            hasHardwareH265 = h265Candidates.any { isHardwareCodec(it) },
            preferredEncoderH264 = prefH264,
            preferredEncoderH265 = prefH265,
            glesVersion = queryGlesVersionSafe(),
            totalRamMb = totalRamMb,
            availableRamMb = availRamMb,
            heapLimitMb = heapLimitMb,
            isLowRamDevice = isLowRam
        )
    }

    fun resolveSmartAutoConfig(
        report: EncoderCapabilityReport,
        project: StudioProject,
        requestedBase: ExportConfiguration = ExportConfiguration()
    ): ExportConfiguration {
        val durationSec = project.totalDurationMs / 1000L
        val hasHeavyOverlays = project.clips.count {
            it.trackType == TrackType.VIDEO || it.mapConfig != null || it.chartConfig != null || it.vfxConfig != null
        } >= 4

        val autoCodec = when {
            durationSec >= 1800L && report.hasHardwareH265 -> ExportCodec.H265 // H.265 saves ~35% disk on multi-hour 5-6 GB exports
            report.supportedCodecs.contains(ExportCodec.H264) -> ExportCodec.H264
            else -> report.supportedCodecs.firstOrNull() ?: ExportCodec.H264
        }

        val desiredRes = when {
            report.isLowRamDevice || report.availableRamMb < 450L -> PreviewResolution.P720
            durationSec >= 3600L && report.availableRamMb < 1100L -> PreviewResolution.P1080
            project.previewResolution == PreviewResolution.UHD4K &&
                report.supportedResolutions.contains(PreviewResolution.UHD4K) &&
                report.totalRamMb >= 4096L -> PreviewResolution.UHD4K
            project.previewResolution == PreviewResolution.QHD1440 &&
                report.supportedResolutions.contains(PreviewResolution.QHD1440) -> PreviewResolution.QHD1440
            report.supportedResolutions.contains(PreviewResolution.P1080) -> PreviewResolution.P1080
            else -> PreviewResolution.P720
        }
        val finalRes = clampResolutionToReport(desiredRes, report)

        val allowedFps = report.supportedFpsByResolution[finalRes].orEmpty()
        val desiredFps = when {
            report.isLowRamDevice || hasHeavyOverlays -> ExportFps.FPS30
            requestedBase.fps in allowedFps -> requestedBase.fps
            else -> ExportFps.FPS30
        }
        val finalFps = if (desiredFps in allowedFps) desiredFps else (allowedFps.firstOrNull() ?: ExportFps.FPS30)

        val finalQuality = when {
            report.isLowRamDevice -> ExportQuality.MEDIUM
            durationSec >= 3600L -> ExportQuality.MEDIUM
            else -> ExportQuality.HIGH
        }

        return ExportConfiguration(
            resolution = finalRes,
            fps = finalFps,
            codec = autoCodec,
            quality = finalQuality,
            mode = ExportMode.SMART_AUTO
        )
    }

    fun clampResolutionToReport(desired: PreviewResolution, report: EncoderCapabilityReport): PreviewResolution {
        if (desired in report.supportedResolutions) return desired
        val fallbackOrder = listOf(
            PreviewResolution.P1080,
            PreviewResolution.QHD1440,
            PreviewResolution.P720,
            PreviewResolution.UHD4K
        )
        return fallbackOrder.firstOrNull { it in report.supportedResolutions } ?: PreviewResolution.P720
    }

    fun resolveEncoderChain(report: EncoderCapabilityReport, preferredCodec: ExportCodec): List<ResolvedEncoderChoice> {
        val codecInfos = runCatching { MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.toList() }.getOrElse { emptyList() }
        val primary = findEncodersForMime(codecInfos, preferredCodec)
        val secondary = if (preferredCodec != ExportCodec.H264) findEncodersForMime(codecInfos, ExportCodec.H264) else emptyList()

        val chain = mutableListOf<ResolvedEncoderChoice>()
        fun appendChoices(list: List<MediaCodecInfo>, codec: ExportCodec) {
            list.sortedByDescending { isHardwareCodec(it) }.forEach { info ->
                chain.add(
                    ResolvedEncoderChoice(
                        codec = codec,
                        encoderName = info.name,
                        isHardwareAccelerated = isHardwareCodec(info),
                        supportsSurfaceInput = supportsSurfaceColorFormat(info, codec.mimeType)
                    )
                )
            }
        }
        appendChoices(primary, preferredCodec)
        appendChoices(secondary, ExportCodec.H264)

        if (chain.isEmpty()) {
            chain.add(
                ResolvedEncoderChoice(
                    codec = ExportCodec.H264,
                    encoderName = null,
                    isHardwareAccelerated = false,
                    supportsSurfaceInput = true
                )
            )
        }
        return chain
    }

    fun computeTargetBitrate(
        resolution: PreviewResolution,
        fps: ExportFps,
        codec: ExportCodec,
        quality: ExportQuality,
        mode: ExportMode
    ): Int {
        val base30Fps = when (resolution) {
            PreviewResolution.P720 -> 5_500_000f
            PreviewResolution.P1080 -> 10_500_000f
            PreviewResolution.QHD1440 -> 16_500_000f
            PreviewResolution.UHD4K -> 26_000_000f
        }
        val fpsScale = when {
            fps.fps >= 50 -> 1.38f
            fps.fps <= 25 -> 0.88f
            else -> 1.0f
        }
        val codecScale = if (codec == ExportCodec.H265) 0.72f else 1.0f
        val modeScale = when (mode) {
            ExportMode.SMART_AUTO -> 1.0f
            ExportMode.FAST_EXPORT -> 0.85f
            ExportMode.HIGH_QUALITY -> 1.18f
        }
        return (base30Fps * fpsScale * codecScale * quality.bitrateScale * modeScale)
            .toInt()
            .coerceIn(1_800_000, 48_000_000)
    }

    private fun PreviewResolution.mimeHint(): String = ExportCodec.H264.mimeType

    private fun findEncodersForMime(infos: List<MediaCodecInfo>, codec: ExportCodec): List<MediaCodecInfo> {
        return infos.filter { info ->
            info.isEncoder && info.supportedTypes.any { it.equals(codec.mimeType, ignoreCase = true) }
        }
    }

    private fun selectBestEncoder(infos: List<MediaCodecInfo>, codec: ExportCodec): ResolvedEncoderChoice? {
        val sorted = infos.sortedByDescending {
            (if (isHardwareCodec(it)) 10 else 0) + (if (supportsSurfaceColorFormat(it, codec.mimeType)) 5 else 0)
        }
        val best = sorted.firstOrNull() ?: return null
        return ResolvedEncoderChoice(
            codec = codec,
            encoderName = best.name,
            isHardwareAccelerated = isHardwareCodec(best),
            supportsSurfaceInput = supportsSurfaceColorFormat(best, codec.mimeType)
        )
    }

    private fun isHardwareCodec(info: MediaCodecInfo): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return info.isHardwareAccelerated
        }
        val lower = info.name.lowercase()
        return !lower.startsWith("omx.google.") &&
            !lower.startsWith("c2.android.") &&
            !lower.contains(".sw.")
    }

    private fun supportsSurfaceColorFormat(info: MediaCodecInfo, mimeType: String): Boolean {
        return runCatching {
            val caps = info.getCapabilitiesForType(mimeType)
            caps.colorFormats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        }.getOrDefault(true)
    }

    private fun isSizeSupported(info: MediaCodecInfo, mimeFallback: String, width: Int, height: Int): Boolean {
        return runCatching {
            val mime = info.supportedTypes.firstOrNull { it.startsWith("video/") } ?: mimeFallback
            val vc = info.getCapabilitiesForType(mime).videoCapabilities ?: return true
            vc.isSizeSupported(width, height) || vc.isSizeSupported(height, width)
        }.getOrDefault(width <= 1920)
    }

    private fun isSizeAndRateSupported(info: MediaCodecInfo, mimeFallback: String, width: Int, height: Int, fps: Int): Boolean {
        return runCatching {
            val mime = info.supportedTypes.firstOrNull { it.startsWith("video/") } ?: mimeFallback
            val vc = info.getCapabilitiesForType(mime).videoCapabilities ?: return fps <= 30
            vc.areSizeAndRateSupported(width, height, fps.toDouble()) ||
                vc.areSizeAndRateSupported(height, width, fps.toDouble())
        }.getOrDefault(fps <= 30)
    }

    private fun queryGlesVersionSafe(): String {
        return runCatching {
            val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == EGL14.EGL_NO_DISPLAY) return "OpenGL ES 2.0"
            val version = IntArray(2)
            if (EGL14.eglInitialize(display, version, 0, version, 1)) {
                val label = "EGL ${version[0]}.${version[1]} / GLES 3.0"
                EGL14.eglTerminate(display)
                label
            } else {
                "OpenGL ES 2.0"
            }
        }.getOrDefault("OpenGL ES 2.0")
    }
}
