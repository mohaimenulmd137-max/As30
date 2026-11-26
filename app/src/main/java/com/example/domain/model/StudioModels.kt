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

enum class TrackType(val labelBn: String, val labelEn: String, val defaultOrder: Int) {
    TEXT("টেক্সট", "Text", 0),
    GRAPHICS("গ্রাফিক্স/চার্ট", "Charts & GFX", 1),
    VFX("ভিএফএক্স", "VFX", 2),
    ADJUSTMENT("অ্যাডজাস্ট", "Adjust", 3),
    VIDEO("ভিডিও / ছবি", "Video", 4),
    VOICEOVER("ভয়েসওভার", "Voice", 5),
    MUSIC("মিউজিক/SFX", "Music", 6)
}

data class TrackLayerState(
    val trackType: TrackType,
    val order: Int = trackType.defaultOrder,
    val isVisible: Boolean = true,
    val isLocked: Boolean = false
)

enum class KeyframeEasing(val label: String) {
    LINEAR("Linear"),
    EASE_IN_OUT("Smooth"),
    SPRING("Spring"),
    HOLD("Hold")
}

data class Keyframe(
    val id: String = UUID.randomUUID().toString(),
    val timeOffsetMs: Long,
    val posX: Float = 0f,
    val posY: Float = 0f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val opacity: Float = 1f,
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
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val temperature: Float = 0f,
    val glow: Float = 0f,
    val blurRadius: Float = 0f,
    val vignette: Float = 0.12f,
    val chromaKeyEnabled: Boolean = false,
    val chromaKeyStrength: Float = 0.45f,
    val masterCurve: List<Float> = listOf(0f, 0.25f, 0.5f, 0.75f, 1f),
    val redCurve: List<Float> = listOf(0f, 0.25f, 0.5f, 0.75f, 1f),
    val greenCurve: List<Float> = listOf(0f, 0.25f, 0.5f, 0.75f, 1f),
    val blueCurve: List<Float> = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
)

enum class VfxEffectType(val labelBn: String, val labelEn: String) {
    NONE("VFX নেই", "None"),
    FILM_GRAIN("৩৫মিমি ফিল্ম গ্রেইন", "35mm Film Grain"),
    ANAMORPHIC_FLARE("অ্যানামরফিক ফ্লেয়ার", "Anamorphic Flare"),
    CHROMATIC_ABERRATION("RGB ক্রোম্যাটিক স্প্লিট", "Chromatic RGB"),
    GLITCH_TEAR("সাইবার গ্লিচ টিয়ার", "Glitch Tear"),
    CINEMA_RAIN("সিনেমাটিক বৃষ্টি", "Cinema Rain"),
    EMBERS_SPARK("ফায়ার এমবার পার্টিকেল", "Fire Embers"),
    CAMERA_SHAKE("হলিউড ক্যামেরা শেক", "Action Shake"),
    LIGHT_LEAK("ভিন্টেজ লাইট লিক", "Light Leak"),
    LETTERBOX_239("২.৩৯:১ হলিউড বার", "2.39:1 Cinema Bars")
}

data class VfxConfig(
    val effectType: VfxEffectType = VfxEffectType.NONE,
    val intensity: Float = 0.65f,
    val speed: Float = 1.0f
)

enum class TransitionType(val labelBn: String, val labelEn: String) {
    NONE("নেই", "None"),
    CROSS_DISSOLVE("ক্রস ডিজলভ", "Cross Dissolve"),
    WHIP_PAN("হুইপ প্যান", "Whip Pan"),
    ZOOM_THROUGH("জুম থ্রু", "Zoom Through"),
    GLITCH_CUT("গ্লিচ কাট", "Glitch Cut"),
    FILM_BURN("ফিল্ম বার্ন", "Film Burn"),
    IRIS_WIPE("আইরিস ওয়াইপ", "Iris Wipe")
}

data class SpeedPoint(
    val normalizedTime: Float,
    val speed: Float
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

enum class GeometricShape(val labelBn: String, val labelEn: String) {
    ROUNDED_RECT("রাউন্ডেড", "Rounded"),
    CIRCLE("বৃত্ত", "Circle"),
    HEXAGON("ষড়ভুজ", "Hexagon"),
    DIAMOND("ডায়মন্ড", "Diamond"),
    TRIANGLE("ত্রিভুজ", "Triangle"),
    SHIELD("ঢাল", "Shield"),
    STAR("তারকা", "Star"),
    DIAGONAL_SLASH("তির্যক কাট", "Slash")
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

// 60-Bar Customizable Comparison Chart
data class ChartBarItem(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val value: Float,
    val colorHex: Long
)

data class ComparisonChartConfig(
    val title: String = "তুলনামূলক ডেটা চার্ট",
    val unitSuffix: String = "%",
    val barCount: Int = 8, // 2..60 bars customizable
    val isHorizontal: Boolean = true,
    val sortDescending: Boolean = true,
    val animateGrowth: Boolean = true,
    val bars: List<ChartBarItem> = emptyList()
)

// Multi-Photo / Multi-Video Comparison Collage (2-4 Slots) with Geometric Shapes
data class ComparisonSlot(
    val id: String = UUID.randomUUID().toString(),
    val caption: String,
    val shape: GeometricShape = GeometricShape.ROUNDED_RECT,
    val borderColorHex: Long = 0xFF00E5FF,
    val mediaSource: MediaSource? = null
)

data class ComparisonGridConfig(
    val title: String = "তুলনামূলক বিশ্লেষণ",
    val slotCount: Int = 3, // 2, 3, or 4 slots
    val spacingDp: Float = 8f,
    val slots: List<ComparisonSlot> = emptyList()
)

// Investigative Documentary Desktop-Grade Tools
enum class InvestigativeToolType(val labelBn: String, val labelEn: String) {
    NONE("সাধারণ", "Standard"),
    EVIDENCE_HIGHLIGHTER("নথি মার্কার হাইলাইট", "Evidence Highlighter"),
    NEWSPAPER_LOUPE("সংবাদপত্র + ম্যাগনিফায়ার", "Cutout & Loupe"),
    REDACTION_CENSOR("গোপনীয় ব্লার / সেন্সর", "Redaction Censor"),
    MAP_RADAR_ROUTE("ম্যাপ রাডার ও রুট", "Map Route & Radar"),
    STAT_YEAR_COUNTER("সাল / সংখ্যা কাউন্টার", "Stat / Year Counter")
}

data class InvestigativeDocConfig(
    val toolType: InvestigativeToolType = InvestigativeToolType.NONE,
    val headline: String = "গোপন নথি · ১৯৭১",
    val bodyText: String = "তদন্তে পাওয়া মূল তথ্য এখানে হাইলাইট হবে",
    val pointALabel: String = "ঢাকা",
    val pointBLabel: String = "কলকাতা",
    val counterStart: Int = 1971,
    val counterEnd: Int = 2026,
    val counterPrefix: String = "",
    val counterSuffix: String = "",
    val accentHex: Long = 0xFFFFB300
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

enum class StudioTextAlign(val labelBn: String, val labelEn: String) {
    LEFT("বামে", "Left"),
    CENTER("মাঝে", "Center"),
    RIGHT("ডানে", "Right")
}

data class TextStylePreset(
    val id: String,
    val nameBn: String,
    val nameEn: String,
    val fontId: String,
    val textColorHex: Long,
    val accentColorHex: Long,
    val useGradient: Boolean = false,
    val gradientStartHex: Long = 0xFF00E5FF,
    val gradientEndHex: Long = 0xFFFFB300,
    val strokeColorHex: Long = 0xFF000000,
    val strokeWidthPx: Float = 0f,
    val shadowRadius: Float = 6f,
    val shadowColorHex: Long = 0xCC000000,
    val letterSpacingSp: Float = 0f,
    val backgroundPill: Boolean = false,
    val pillColorHex: Long = 0xDC0D111C,
    val lowerThirdTemplate: LowerThirdTemplate = LowerThirdTemplate.NONE
)

data class TextLayerConfig(
    val text: String = "নতুন ডকুমেন্টারি টাইটেল",
    val subtitle: String = "১৭৫৭ · ঐতিহাসিক আর্কাইভ",
    val fontId: String = StudioFontOption.HIND_SILIGURI.id,
    val fontSizeSp: Float = 26f,
    val textColorHex: Long = 0xFFF3F6FC,
    val accentColorHex: Long = 0xFFFFB300,
    val karaokeHighlight: Boolean = false,
    val backgroundPill: Boolean = false,
    val lowerThirdTemplate: LowerThirdTemplate = LowerThirdTemplate.NONE,
    val stylePresetId: String = "style_clean",
    val useGradient: Boolean = false,
    val gradientStartHex: Long = 0xFF00E5FF,
    val gradientEndHex: Long = 0xFFFFB300,
    val strokeColorHex: Long = 0xFF000000,
    val strokeWidthPx: Float = 0f,
    val shadowRadius: Float = 6f,
    val shadowColorHex: Long = 0xCC000000,
    val letterSpacingSp: Float = 0f,
    val lineHeightMult: Float = 1.22f,
    val textAlign: StudioTextAlign = StudioTextAlign.CENTER,
    val pillColorHex: Long = 0xDC0D111C,
    val pillCornerDp: Float = 10f,
    val customFontPath: String? = null,
    val customFontName: String? = null
)

data class AudioConfig(
    val volume: Float = 1.0f,
    val fadeInMs: Long = 300L,
    val fadeOutMs: Long = 400L,
    val autoDuckingEnabled: Boolean = true,
    val duckingFloor: Float = 0.22f,
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
    val isLocked: Boolean = false,
    val isHidden: Boolean = false,
    val posX: Float = 0f,
    val posY: Float = 0f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val opacity: Float = 1f,
    val keyframes: List<Keyframe> = emptyList(),
    val inAnimationId: String = "in_fade",
    val outAnimationId: String = "out_fade",
    val loopAnimationId: String = "loop_none",
    val transitionIn: TransitionType = TransitionType.NONE,
    val lockedSfxId: String? = null,
    val colorGrade: LumetriColorGrade = LumetriColorGrade(),
    val vfxConfig: VfxConfig? = null,
    val speedRamp: SpeedRampProfile = SpeedRampProfile(),
    val maskConfig: MaskConfig = MaskConfig(),
    val blendMode: BlendModeOption = BlendModeOption.NORMAL,
    val textConfig: TextLayerConfig? = null,
    val chartConfig: ComparisonChartConfig? = null,
    val comparisonGrid: ComparisonGridConfig? = null,
    val investigativeConfig: InvestigativeDocConfig? = null,
    val audioConfig: AudioConfig = AudioConfig(),
    val mediaSource: MediaSource? = null
) {
    val endMs: Long get() = startMs + durationMs
}

data class StudioProject(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val aspectRatio: AspectRatioPreset = AspectRatioPreset.LANDSCAPE_16_9,
    val previewResolution: PreviewResolution = PreviewResolution.P1080,
    val thumbnailDrawable: String = "img_tpl_documentary",
    val totalDurationMs: Long = 15_000L,
    val lastEditedAt: Long = System.currentTimeMillis(),
    val masterVolume: Float = 1.0f,
    val videoTrackPreviewOn: Boolean = true,
    val editModeOn: Boolean = true,
    val showSafeZoneGuides: Boolean = true,
    val trackLayers: List<TrackLayerState> = TrackType.entries.map { TrackLayerState(it) },
    val clips: List<TimelineClip> = emptyList()
)
