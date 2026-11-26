package com.example.domain.model

import androidx.compose.ui.text.font.FontFamily
import com.example.R
import com.example.ui.theme.HindSiliguriFamily
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.NotoSansBengaliFamily
import com.example.ui.theme.NotoSerifBengaliFamily
import com.example.ui.theme.PlayfairDisplayFamily
import com.example.ui.theme.SpaceGroteskFamily
import java.util.UUID

enum class AspectRatioPreset(val label: String, val ratio: Float, val width4k: Int, val height4k: Int) {
    PORTRAIT_9_16("9:16", 9f / 16f, 2160, 3840),
    LANDSCAPE_16_9("16:9", 16f / 9f, 3840, 2160),
    SQUARE_1_1("1:1", 1f, 2160, 2160)
}

enum class PreviewResolution(val label: String, val shortEdge: Int) {
    P720("720p", 720),
    P1080("1080p", 1080),
    UHD4K("4K", 2160)
}

enum class ExportFps(val fps: Int, val label: String) {
    FPS30(30, "30 fps"),
    FPS60(60, "60 fps")
}

enum class TrackType(val labelBn: String, val labelEn: String, val order: Int) {
    TEXT("টেক্সট", "Text", 0),
    GRAPHICS("গ্রাফিক্স", "Graphics", 1),
    ADJUSTMENT("অ্যাডজাস্টমেন্ট", "Adjust", 2),
    VIDEO("ভিডিও / ছবি", "Video", 3),
    VOICEOVER("ভয়েস / SFX", "Voice", 4),
    MUSIC("মিউজিক", "Music", 5)
}

enum class KeyframeEasing(val label: String) {
    LINEAR("Linear"),
    EASE_IN_OUT("Smooth"),
    SPRING("Spring"),
    HOLD("Hold")
}

data class Keyframe(
    val id: String = UUID.randomUUID().toString(),
    val timeOffsetMs: Long,
    val posX: Float = 0f,       // Normalized -0.5..0.5 relative to canvas center
    val posY: Float = 0f,       // Normalized -0.5..0.5 relative to canvas center
    val scale: Float = 1f,      // 0.2..3.0
    val rotation: Float = 0f,   // -360..360 deg
    val opacity: Float = 1f,    // 0..1
    val easing: KeyframeEasing = KeyframeEasing.EASE_IN_OUT
)

enum class AnimationCategory(val labelBn: String, val labelEn: String) {
    IN("ইন (In)", "In"),
    OUT("আউট (Out)", "Out"),
    LOOP("লুপ (Loop)", "Loop")
}

data class MotionAnimationSpec(
    val id: String,
    val name: String,
    val nameBn: String,
    val category: AnimationCategory
)

enum class SfxCategory(val label: String) {
    WHOOSH("Whoosh"),
    POP("Pop & UI"),
    IMPACT("Impact"),
    TYPING("Typing"),
    CINEMA("Cinema"),
    GLITCH("Glitch")
}

enum class SynthWaveform { SINE, TRIANGLE, SQUARE, NOISE_BURST, SWEEP_DOWN, SWEEP_UP }

data class SfxPreset(
    val id: String,
    val name: String,
    val nameBn: String,
    val category: SfxCategory,
    val durationMs: Int,
    val freqStartHz: Float,
    val freqEndHz: Float,
    val waveform: SynthWaveform,
    val noiseMix: Float = 0.1f
)

data class LumetriColorGrade(
    val brightness: Float = 0f,     // -0.6..0.6
    val contrast: Float = 1f,       // 0.4..2.0
    val saturation: Float = 1f,     // 0.0..2.2
    val temperature: Float = 0f,    // -0.6..0.6
    val glow: Float = 0f,           // 0..1
    val blurRadius: Float = 0f,     // 0..20
    val vignette: Float = 0.12f,    // 0..1
    val masterCurve: List<Float> = listOf(0f, 0.25f, 0.5f, 0.75f, 1f),
    val redCurve: List<Float> = listOf(0f, 0.25f, 0.5f, 0.75f, 1f),
    val greenCurve: List<Float> = listOf(0f, 0.25f, 0.5f, 0.75f, 1f),
    val blueCurve: List<Float> = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
)

data class SpeedPoint(
    val normalizedTime: Float, // 0f..1f across clip
    val speed: Float           // 0.25f..4.0f
)

data class SpeedRampProfile(
    val presetName: String = "Normal (1.0x)",
    val points: List<SpeedPoint> = listOf(
        SpeedPoint(0f, 1f),
        SpeedPoint(0.5f, 1f),
        SpeedPoint(1f, 1f)
    )
)

enum class MaskShape(val label: String, val labelBn: String) {
    NONE("None", "মাস্ক নেই"),
    LINEAR_TOP("Linear Top", "লিনিয়ার টপ"),
    LINEAR_SPLIT("Split Wipe", "স্প্লিট"),
    RADIAL_SPOTLIGHT("Radial Circle", "রেডিয়াল ফোকাস"),
    CINEMA_BARS("2.39:1 Bars", "সিনেমা বার"),
    FREEHAND_DIAMOND("Diamond Focus", "ডায়মন্ড মাস্ক")
}

enum class BlendModeOption(val label: String) {
    NORMAL("Normal"),
    SCREEN("Screen"),
    OVERLAY("Overlay"),
    MULTIPLY("Multiply"),
    PLUS_ADD("Add / Glow")
}

data class MaskConfig(
    val shape: MaskShape = MaskShape.NONE,
    val feather: Float = 0.15f,
    val scale: Float = 0.75f,
    val inverted: Boolean = false
)

enum class StudioFontOption(
    val id: String,
    val displayName: String,
    val previewSample: String,
    val family: FontFamily,
    val fontResId: Int
) {
    HIND_SILIGURI(
        "hind_siliguri",
        "হিন্দ শিলিগুড়ি · Pro Sans",
        "বাংলা ডকুমেন্টারি",
        HindSiliguriFamily,
        R.font.hind_siliguri
    ),
    NOTO_SANS_BENGALI(
        "noto_sans_bengali",
        "নোটো স্যান্স বাংলা · Clean",
        "যুক্তবর্ণ নিখুঁত",
        NotoSansBengaliFamily,
        R.font.noto_sans_bengali
    ),
    NOTO_SERIF_BENGALI(
        "noto_serif_bengali",
        "নোটো সেরিফ বাংলা · Editorial",
        "ঐতিহাসিক শিরোনাম",
        NotoSerifBengaliFamily,
        R.font.noto_serif_bengali
    ),
    SPACE_GROTESK(
        "space_grotesk",
        "Space Grotesk · Motion",
        "MODERN STUDIO",
        SpaceGroteskFamily,
        R.font.space_grotesk
    ),
    PLAYFAIR_DISPLAY(
        "playfair_display",
        "Playfair Display · Cinema",
        "Cinema Title",
        PlayfairDisplayFamily,
        R.font.playfair_display
    ),
    JETBRAINS_MONO(
        "jetbrains_mono",
        "JetBrains Mono · Tech",
        "TIMECODE 24FPS",
        JetBrainsMonoFamily,
        R.font.jetbrains_mono
    )
}

enum class LowerThirdTemplate(val label: String, val labelBn: String) {
    NONE("Standard Text", "সাধারণ টেক্সট"),
    DOCUMENTARY_DATE_CARD("Historical Date Card", "ঐতিহাসিক সাল কার্ড"),
    NEWS_LOWER_THIRD("Documentary Lower Third", "ডকুমেন্টারি লোয়ার থার্ড"),
    CINEMA_CHAPTER("Chapter Slate", "চ্যাপ্টার টাইটেল"),
    MINIMAL_CAPTION("Glass Pill Caption", "গ্লাস ক্যাপশন")
}

data class TextLayerConfig(
    val text: String = "নতুন ডকুমেন্টারি টাইটেল",
    val subtitle: String = "১৭৫৭ · ঐতিহাসিক আর্কাইভ",
    val fontId: String = StudioFontOption.HIND_SILIGURI.id,
    val fontSizeSp: Float = 26f,
    val textColorHex: Long = 0xFFF3F6FC,
    val accentColorHex: Long = 0xFFFFB300,
    val karaokeHighlight: Boolean = false,
    val backgroundPill: Boolean = false,
    val lowerThirdTemplate: LowerThirdTemplate = LowerThirdTemplate.NONE
)

data class AudioConfig(
    val volume: Float = 1.0f,
    val fadeInMs: Long = 300L,
    val fadeOutMs: Long = 400L,
    val autoDuckingEnabled: Boolean = true,
    val duckingFloor: Float = 0.25f,
    val noiseReductionEnabled: Boolean = false,
    val audioPath: String? = null,
    val waveformSeed: Int = 7
)

data class MediaSource(
    val uriString: String? = null,
    val drawableName: String? = null,
    val isVideo: Boolean = false,
    val label: String = "Media Clip"
)

data class TimelineClip(
    val id: String = UUID.randomUUID().toString(),
    val trackType: TrackType,
    val title: String,
    val startMs: Long,
    val durationMs: Long,
    val posX: Float = 0f,
    val posY: Float = 0f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val opacity: Float = 1f,
    val keyframes: List<Keyframe> = emptyList(),
    val inAnimationId: String = "in_fade",
    val outAnimationId: String = "out_fade",
    val loopAnimationId: String = "loop_none",
    val lockedSfxId: String? = null,
    val colorGrade: LumetriColorGrade = LumetriColorGrade(),
    val speedRamp: SpeedRampProfile = SpeedRampProfile(),
    val maskConfig: MaskConfig = MaskConfig(),
    val blendMode: BlendModeOption = BlendModeOption.NORMAL,
    val textConfig: TextLayerConfig? = null,
    val audioConfig: AudioConfig = AudioConfig(),
    val mediaSource: MediaSource? = null
) {
    val endMs: Long get() = startMs + durationMs
}

data class StudioProject(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val aspectRatio: AspectRatioPreset = AspectRatioPreset.PORTRAIT_9_16,
    val previewResolution: PreviewResolution = PreviewResolution.P1080,
    val thumbnailDrawable: String = "img_tpl_documentary",
    val totalDurationMs: Long = 15_000L,
    val lastEditedAt: Long = System.currentTimeMillis(),
    val clips: List<TimelineClip> = emptyList()
)
