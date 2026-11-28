package com.example.domain.engine.export

import android.media.MediaFormat
import android.net.Uri
import com.example.domain.model.ExportFps
import com.example.domain.model.PreviewResolution
import com.example.domain.model.StudioProject

enum class ExportCodec(val label: String, val mimeType: String, val shortLabel: String) {
    H264("H.264 / AVC", MediaFormat.MIMETYPE_VIDEO_AVC, "H.264"),
    H265("H.265 / HEVC", MediaFormat.MIMETYPE_VIDEO_HEVC, "H.265")
}

enum class ExportQuality(
    val labelBn: String,
    val labelEn: String,
    val bitrateScale: Float,
    val iFrameIntervalSec: Int
) {
    LOW("লো (দ্রুত/কম সাইজ)", "Low", 0.58f, 2),
    MEDIUM("মিডিয়াম (ব্যালান্সড)", "Medium", 0.85f, 2),
    HIGH("হাই (ক্রিস্টাল ক্লিয়ার)", "High", 1.15f, 1),
    ULTRA("আল্ট্রা (মাস্টার)", "Ultra", 1.55f, 1)
}

enum class ExportMode(val labelBn: String, val labelEn: String) {
    SMART_AUTO("স্মার্ট অটো", "Smart Auto"),
    FAST_EXPORT("দ্রুত এক্সপোর্ট", "Fast Export"),
    HIGH_QUALITY("হাই কোয়ালিটি", "High Quality")
}

enum class ExportThermalLevel(val labelBn: String, val labelEn: String) {
    NORMAL("স্বাভাবিক", "Normal"),
    MODERATE("হালকা গরম", "Warm"),
    SEVERE("অতিরিক্ত গরম · থ্রটল", "Hot · Pacing"),
    CRITICAL("সতর্কতা · কুলডাউন", "Overheat Guard")
}

enum class MemoryTier(val labelBn: String) {
    CRITICAL("অত্যন্ত কম RAM"),
    LOW("লো-RAM সেফ"),
    BALANCED("ব্যালান্সড RAM"),
    HIGH("হাই-RAM অপ্টিমাইজড")
}

enum class ExportErrorCategory(val titleBn: String, val titleEn: String) {
    OUT_OF_MEMORY("অপর্যাপ্ত মেমোরি (RAM)", "Out of Memory"),
    ENCODER_INIT_FAILED("এনকোডার চালু করা যায়নি", "Encoder Initialization Failed"),
    UNSUPPORTED_CODEC("অসমর্থিত ভিডিও কোডেক", "Unsupported Codec"),
    UNSUPPORTED_RESOLUTION("অসমর্থিত রেজুলেশন", "Unsupported Resolution"),
    DECODER_FAILURE("মিডিয়া ডিকোডিং ব্যর্থ", "Decoder Failure"),
    STORAGE_FULL("স্টোরেজ জায়গা পর্যাপ্ত নয়", "Storage Full"),
    FILE_ACCESS_DENIED("ফাইল বা গ্যালারি এক্সেস ব্যর্থ", "File Access Denied"),
    AUDIO_PROCESSING_FAILED("অডিও প্রসেসিং ব্যর্থ", "Audio Processing Failed"),
    VIDEO_RENDERING_FAILED("ভিডিও ফ্রেম রেন্ডার ব্যর্থ", "Video Rendering Failed"),
    MUXING_FAILED("MP4 ফাইল তৈরি (Muxing) ব্যর্থ", "Muxing Failed"),
    CANCELLED("এক্সপোর্ট বাতিল করা হয়েছে", "Export Cancelled"),
    THERMAL_RESTRICTED("ডিভাইস অতিরিক্ত গরম হওয়ায় বিরতি", "Thermal Restriction"),
    UNKNOWN("অপ্রত্যাশিত এক্সপোর্ট ত্রুটি", "Export Error")
}

data class ExportConfiguration(
    val resolution: PreviewResolution = PreviewResolution.P1080,
    val fps: ExportFps = ExportFps.FPS30,
    val codec: ExportCodec = ExportCodec.H264,
    val quality: ExportQuality = ExportQuality.HIGH,
    val mode: ExportMode = ExportMode.SMART_AUTO
)

data class ResolvedEncoderChoice(
    val codec: ExportCodec,
    val encoderName: String?,
    val isHardwareAccelerated: Boolean,
    val supportsSurfaceInput: Boolean
)

data class EncoderCapabilityReport(
    val supportedResolutions: Set<PreviewResolution>,
    val supportedFpsByResolution: Map<PreviewResolution, Set<ExportFps>>,
    val supportedCodecs: Set<ExportCodec>,
    val hasHardwareH264: Boolean,
    val hasHardwareH265: Boolean,
    val preferredEncoderH264: ResolvedEncoderChoice?,
    val preferredEncoderH265: ResolvedEncoderChoice?,
    val glesVersion: String,
    val totalRamMb: Long,
    val availableRamMb: Long,
    val heapLimitMb: Long,
    val isLowRamDevice: Boolean
) {
    fun supports(resolution: PreviewResolution, fps: ExportFps, codec: ExportCodec): Boolean {
        if (!supportedCodecs.contains(codec)) return false
        if (!supportedResolutions.contains(resolution)) return false
        val fpsSet = supportedFpsByResolution[resolution] ?: return false
        return fpsSet.contains(fps)
    }
}

data class AdaptiveMemoryProfile(
    val tier: MemoryTier,
    val availableSystemRamMb: Long,
    val heapUsedMb: Long,
    val heapLimitMb: Long,
    val heapHeadroomMb: Long,
    val maxBitmapCacheBytes: Long,
    val maxOpenVideoRetrievers: Int,
    val maxDecodeEdgePx: Int,
    val preferHardwareCanvas: Boolean,
    val chunkedAudioBufferSamples: Int,
    val thermalYieldMs: Long
)

data class ExportProgressSnapshot(
    val exportId: String,
    val progress: Float,
    val statusText: String,
    val elapsedMs: Long,
    val estimatedRemainingMs: Long,
    val resolutionLabel: String,
    val fps: Int,
    val codecLabel: String,
    val encoderInfo: String,
    val isHardwareEncoder: Boolean,
    val outputBytesWritten: Long,
    val estimatedTotalBytes: Long,
    val currentFrame: Int,
    val totalFrames: Int,
    val isPaused: Boolean,
    val thermalLevel: ExportThermalLevel,
    val memoryTier: MemoryTier,
    val availableRamMb: Long
)

data class ExportRecoveryRecord(
    val exportId: String,
    val projectId: String,
    val projectName: String,
    val resolutionName: String,
    val fps: Int,
    val codecName: String,
    val qualityName: String,
    val modeName: String,
    val targetUriString: String?,
    val tempFilePath: String?,
    val lastCompletedFrame: Int,
    val totalFrames: Int,
    val timestampMs: Long,
    val errorCategoryName: String?,
    val failureReason: String?
)

data class ExportJobRequest(
    val project: StudioProject,
    val config: ExportConfiguration
)
