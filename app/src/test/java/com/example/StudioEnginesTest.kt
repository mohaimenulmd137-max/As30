package com.example

import com.example.domain.engine.AudioSynthEngine
import com.example.domain.engine.KeyframeInterpolator
import com.example.domain.engine.SpeedRampEngine
import com.example.domain.model.AnimationCategory
import com.example.domain.model.AnimationDirection
import com.example.domain.model.AnimationEasingType
import com.example.domain.model.AnimationMediaScope
import com.example.domain.model.AnimationSlotConfig
import com.example.domain.model.AudioConfig
import com.example.domain.model.BorderShapeStyle
import com.example.domain.model.ClipAnimationProfile
import com.example.domain.model.GeometricShape
import com.example.domain.model.Keyframe
import com.example.domain.model.KeyframeEasing
import com.example.domain.model.PresetCatalog
import com.example.domain.model.StudioProject
import com.example.domain.model.TimelineClip
import com.example.domain.model.TrackType
import com.example.domain.model.VfxEffectType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioEnginesTest {

    @Test
    fun `catalog provides 50 plus animations and sfx presets`() {
        assertTrue(PresetCatalog.motionAnimations.size >= 54)
        assertTrue(PresetCatalog.sfxPresets.size >= 52)
    }

    @Test
    fun `keyframe interpolator smoothly interpolates position and opacity`() {
        val clip = TimelineClip(
            trackType = TrackType.TEXT,
            title = "Title",
            startMs = 0L,
            durationMs = 4000L,
            inAnimationId = "in_none",
            outAnimationId = "out_none",
            keyframes = listOf(
                Keyframe(timeOffsetMs = 0L, posX = 0f, opacity = 0f, easing = KeyframeEasing.LINEAR),
                Keyframe(timeOffsetMs = 2000L, posX = 0.4f, opacity = 1f, easing = KeyframeEasing.LINEAR)
            )
        )
        val mid = KeyframeInterpolator.evaluate(clip, 1000L)
        assertEquals(0.2f, mid.posX, 0.02f)
        assertEquals(0.5f, mid.opacity, 0.02f)
    }

    @Test
    fun `auto ducking smoothly ducks music during voiceover and swells back up`() {
        val music = TimelineClip(
            trackType = TrackType.MUSIC,
            title = "BGM",
            startMs = 0L,
            durationMs = 10_000L,
            audioConfig = AudioConfig(volume = 1.0f, fadeInMs = 0L, fadeOutMs = 0L, autoDuckingEnabled = true, duckingFloor = 0.25f)
        )
        val voice = TimelineClip(
            trackType = TrackType.VOICEOVER,
            title = "VO",
            startMs = 3_000L,
            durationMs = 3_000L
        )
        val project = StudioProject(name = "Test", masterVolume = 1.0f, clips = listOf(music, voice))

        val normalGain = AudioSynthEngine.computeEffectiveAudioGain(music, project, 1_000L)
        val attackMidGain = AudioSynthEngine.computeEffectiveAudioGain(music, project, 2_875L)
        val duckedGain = AudioSynthEngine.computeEffectiveAudioGain(music, project, 4_000L)
        val releaseMidGain = AudioSynthEngine.computeEffectiveAudioGain(music, project, 6_175L)

        assertEquals(1.0f, normalGain, 0.02f)
        assertTrue(attackMidGain in 0.3f..0.9f)
        assertEquals(0.25f, duckedGain, 0.02f)
        assertTrue(releaseMidGain in 0.3f..0.9f)
    }

    @Test
    fun `speed ramp evaluates slow mo dip accurately`() {
        val heroRamp = PresetCatalog.speedRampPresets[1]
        val midSpeed = SpeedRampEngine.evaluateSpeedAt(heroRamp, 0.5f)
        assertEquals(0.3f, midSpeed, 0.05f)
    }

    @Test
    fun `comparison chart generates up to 60 bars and geometric comparison grid builds 2 to 4 slots`() {
        val bars60 = PresetCatalog.generateDefaultBars(60)
        assertEquals(60, bars60.size)

        val clampedMax = PresetCatalog.generateDefaultBars(90)
        assertEquals(60, clampedMax.size)

        val grid4 = PresetCatalog.buildDefaultComparisonGrid(4)
        assertEquals(4, grid4.slots.size)
        assertEquals(GeometricShape.HEXAGON, grid4.slots[0].shape)
        assertEquals(GeometricShape.SHIELD, grid4.slots[3].shape)
    }

    @Test
    fun `auto investigative documentary builder populates VFX, charts, geometric border frame, and track layers`() {
        val doc = PresetCatalog.buildAutoInvestigativeDocumentary("Test Doc", "Key Finding")
        assertTrue(doc.clips.any { it.vfxConfig?.effectType == VfxEffectType.FILM_GRAIN })
        assertTrue(doc.clips.any { it.chartConfig != null })
        assertTrue(doc.clips.any { it.borderConfig.style == BorderShapeStyle.HEXAGON_FRAME && it.laneIndex == 1 })
        assertTrue(doc.clips.any { it.investigativeConfig != null })
        assertEquals(TrackType.entries.size, doc.trackLayers.size)
    }

    @Test
    fun `catalog provides 16 formatted text style presets and applyTextStylePreset preserves custom font`() {
        assertTrue(PresetCatalog.textStylePresets.size >= 16)
        assertTrue(PresetCatalog.textColorSwatches.size >= 12)

        val gold = PresetCatalog.textStylePresets.first { it.id == "style_hollywood_gold" }
        val base = com.example.domain.model.TextLayerConfig(
            text = "হলিউড টাইটেল",
            customFontPath = "/tmp/my_custom.ttf",
            customFontName = "MyCustomFont"
        )
        val styled = PresetCatalog.applyTextStylePreset(base, gold)
        assertEquals("style_hollywood_gold", styled.stylePresetId)
        assertTrue(styled.useGradient)
        assertEquals(gold.gradientStartHex, styled.gradientStartHex)
        assertEquals(gold.gradientEndHex, styled.gradientEndHex)
        assertEquals(gold.strokeWidthPx, styled.strokeWidthPx, 0.01f)
        assertEquals("/tmp/my_custom.ttf", styled.customFontPath)
    }

    @Test
    fun `transition engine evaluates dissolve, whip pan, zoom through, iris wipe, and film burn`() {
        val dissolveClip = TimelineClip(
            trackType = TrackType.VIDEO,
            title = "Dissolve",
            startMs = 1000L,
            durationMs = 4000L,
            transitionIn = com.example.domain.model.TransitionType.CROSS_DISSOLVE
        )
        val atStart = com.example.domain.engine.TransitionAndChromaEngine.evaluateTransition(dissolveClip, 1000L)
        val afterWin = com.example.domain.engine.TransitionAndChromaEngine.evaluateTransition(dissolveClip, 1900L)
        assertEquals(0f, atStart.alphaMult, 0.01f)
        assertEquals(1f, afterWin.alphaMult, 0.01f)

        val whipClip = dissolveClip.copy(transitionIn = com.example.domain.model.TransitionType.WHIP_PAN)
        val whipStart = com.example.domain.engine.TransitionAndChromaEngine.evaluateTransition(whipClip, 1000L)
        assertEquals(0.85f, whipStart.offsetXNorm, 0.02f)

        val irisClip = dissolveClip.copy(transitionIn = com.example.domain.model.TransitionType.IRIS_WIPE)
        val irisMid = com.example.domain.engine.TransitionAndChromaEngine.evaluateTransition(irisClip, 1325L)
        assertTrue(irisMid.irisRadiusFrac in 0.1f..0.95f)

        val burnClip = dissolveClip.copy(transitionIn = com.example.domain.model.TransitionType.FILM_BURN)
        val burnMid = com.example.domain.engine.TransitionAndChromaEngine.evaluateTransition(burnClip, 1325L)
        assertTrue(burnMid.isWarmFlash && burnMid.flashAlpha > 0.3f)
    }

    @Test
    fun `chroma key alpha math keys pure green to transparent and keeps red subject opaque`() {
        val pureGreenAlpha = com.example.domain.engine.TransitionAndChromaEngine.computeChromaAlpha(20, 240, 30, 0.55f)
        val redSubjectAlpha = com.example.domain.engine.TransitionAndChromaEngine.computeChromaAlpha(220, 40, 45, 0.55f)
        assertEquals(0, pureGreenAlpha)
        assertEquals(255, redSubjectAlpha)
    }

    @Test
    fun `animation library contains full Text, Image, Video, In, Out, and Loop presets`() {
        val textAnims = PresetCatalog.motionAnimations.filter { it.scope == AnimationMediaScope.TEXT }
        val imageAnims = PresetCatalog.motionAnimations.filter { it.scope == AnimationMediaScope.IMAGE }
        val videoAnims = PresetCatalog.motionAnimations.filter { it.scope == AnimationMediaScope.VIDEO }
        val loopAnims = PresetCatalog.motionAnimations.filter { it.category == AnimationCategory.LOOP }

        assertTrue(textAnims.size >= 13)
        assertTrue(imageAnims.size >= 11)
        assertTrue(videoAnims.size >= 9)
        assertTrue(loopAnims.size >= 12)
        assertTrue(PresetCatalog.motionAnimations.any { it.id == "text_typewriter" })
        assertTrue(PresetCatalog.motionAnimations.any { it.id == "img_pan_left" })
        assertTrue(PresetCatalog.motionAnimations.any { it.id == "vid_speed_ramp" })
        assertTrue(PresetCatalog.motionAnimations.any { it.id == "loop_pendulum" })
    }

    @Test
    fun `keyframe interpolator runs simultaneous In, Loop, and Out slots, respects direction, bezier, and bypass`() {
        val profile = ClipAnimationProfile(
            inSlot = AnimationSlotConfig(
                animationId = "text_typewriter",
                enabled = true,
                durationMs = 1000L,
                direction = AnimationDirection.RIGHT,
                easing = AnimationEasingType.CUBIC_BEZIER,
                bezierX1 = 0.2f,
                bezierY1 = 0.8f,
                bezierX2 = 0.4f,
                bezierY2 = 1.0f
            ),
            loopSlot = AnimationSlotConfig(
                animationId = "loop_float",
                enabled = true,
                durationMs = 1000L,
                intensity = 1.5f
            ),
            outSlot = AnimationSlotConfig(
                animationId = "out_fade",
                enabled = true,
                durationMs = 1000L
            ),
            skewX = 0.15f,
            anchorX = 0.25f
        )
        val clip = TimelineClip(
            trackType = TrackType.TEXT,
            title = "Simultaneous",
            startMs = 0L,
            durationMs = 5000L,
            animationProfile = profile
        )

        val midIn = KeyframeInterpolator.evaluate(clip, 500L)
        assertTrue(midIn.textRevealFraction in 0.1f..0.95f)
        assertEquals(0.15f, midIn.skewX, 0.01f)
        assertEquals(0.25f, midIn.anchorX, 0.01f)

        val midOut = KeyframeInterpolator.evaluate(clip, 4750L)
        assertTrue(midOut.opacity < 0.7f)

        val bypassed = KeyframeInterpolator.evaluate(clip, 500L, bypassAnimations = true)
        assertEquals(1f, bypassed.textRevealFraction, 0.001f)
        assertEquals(1f, bypassed.opacity, 0.001f)
    }

    @Test
    fun `audio gain enforces per-clip Mute, Solo, and 2x boost`() {
        val voice = TimelineClip(
            trackType = TrackType.VOICEOVER,
            title = "VO",
            startMs = 0L,
            durationMs = 5000L,
            audioConfig = AudioConfig(volume = 2.0f, fadeInMs = 0L, fadeOutMs = 0L, isSolo = true)
        )
        val videoAudio = TimelineClip(
            trackType = TrackType.VIDEO,
            title = "Main Video",
            startMs = 0L,
            durationMs = 5000L,
            audioConfig = AudioConfig(volume = 1.0f, fadeInMs = 0L, fadeOutMs = 0L, isSolo = false)
        )
        val mutedVoice = voice.copy(audioConfig = voice.audioConfig.copy(isMuted = true))

        val projectWithSolo = StudioProject(name = "Solo", masterVolume = 1.0f, clips = listOf(voice, videoAudio))
        assertEquals(2.0f, AudioSynthEngine.computeEffectiveAudioGain(voice, projectWithSolo, 1000L), 0.02f)
        assertEquals(0f, AudioSynthEngine.computeEffectiveAudioGain(videoAudio, projectWithSolo, 1000L), 0.001f)

        val projectWithMute = StudioProject(name = "Mute", masterVolume = 1.0f, clips = listOf(mutedVoice, videoAudio))
        assertEquals(0f, AudioSynthEngine.computeEffectiveAudioGain(mutedVoice, projectWithMute, 1000L), 0.001f)
    }

    @Test
    fun `transition engine supports dual Start and End transitions with custom durations and new cinema effects`() {
        val clip = TimelineClip(
            trackType = TrackType.VIDEO,
            title = "Dual Trans",
            startMs = 1000L,
            durationMs = 5000L,
            transitionIn = com.example.domain.model.TransitionType.SLIDE_WARP,
            transitionInDurationMs = 1000L,
            transitionOut = com.example.domain.model.TransitionType.SPIN_FLASH,
            transitionOutDurationMs = 1200L
        )
        // Start of clip -> SLIDE_WARP shifts vertically & dims alpha
        val startState = com.example.domain.engine.TransitionAndChromaEngine.evaluateTransition(clip, 1100L)
        assertTrue(kotlin.math.abs(startState.offsetYNorm) > 0.1f)
        assertTrue(startState.alphaMult in 0.05f..0.95f)

        // Middle of clip -> neutral
        val midState = com.example.domain.engine.TransitionAndChromaEngine.evaluateTransition(clip, 3000L)
        assertEquals(1f, midState.alphaMult, 0.01f)
        assertEquals(0f, midState.rotationDeg, 0.01f)

        // End window of clip (within last 1200ms -> 4800..6000) -> SPIN_FLASH rotates and flashes
        val endState = com.example.domain.engine.TransitionAndChromaEngine.evaluateTransition(clip, 5600L)
        assertTrue(kotlin.math.abs(endState.rotationDeg) > 1f)
        assertTrue(endState.alphaMult < 0.9f)

        // LUMA_DISSOLVE check
        val lumaClip = clip.copy(transitionIn = com.example.domain.model.TransitionType.LUMA_DISSOLVE, transitionOut = com.example.domain.model.TransitionType.NONE)
        val lumaMid = com.example.domain.engine.TransitionAndChromaEngine.evaluateTransition(lumaClip, 1500L)
        assertTrue(lumaMid.alphaMult in 0.1f..0.95f)
    }

    @Test
    fun `hi-fi sfx synth produces non-zero balanced organic pcm for all 6 sfx categories`() {
        com.example.domain.model.SfxCategory.entries.forEach { category ->
            val preset = PresetCatalog.sfxPresets.first { it.category == category }
            val pcm = AudioSynthEngine.generateSfxPcm(preset, 0.9f)
            assertTrue("Expected >= 1024 samples for $category", pcm.size >= 1024)
            val peak = pcm.maxOf { kotlin.math.abs(it.toInt()) }
            assertTrue("Expected audible peak for $category", peak > 500)
            assertTrue("Expected non-clipping peak for $category", peak <= Short.MAX_VALUE)
            val mean = pcm.map { it.toDouble() }.average()
            assertTrue("Expected low DC bias for $category, got $mean", kotlin.math.abs(mean) < 600.0)
        }
    }

    @Test
    fun `comparison chart starts from zero by default and supports all 7 graph visual styles`() {
        val zeroChart = com.example.domain.model.ComparisonChartConfig()
        assertEquals(0, zeroChart.barCount)
        assertTrue(zeroChart.bars.isEmpty())
        assertEquals(com.example.domain.model.ChartVisualStyle.HORIZONTAL_BAR, zeroChart.chartStyle)
        assertEquals(7, com.example.domain.model.ChartVisualStyle.entries.size)
        assertTrue(com.example.domain.model.ChartVisualStyle.entries.contains(com.example.domain.model.ChartVisualStyle.PIE_CIRCLE))
        assertTrue(com.example.domain.model.ChartVisualStyle.entries.contains(com.example.domain.model.ChartVisualStyle.DONUT_RING))
        assertTrue(com.example.domain.model.ChartVisualStyle.entries.contains(com.example.domain.model.ChartVisualStyle.LINE_GRAPH))
        assertTrue(com.example.domain.model.ChartVisualStyle.entries.contains(com.example.domain.model.ChartVisualStyle.AREA_WAVE))
        assertTrue(com.example.domain.model.ChartVisualStyle.entries.contains(com.example.domain.model.ChartVisualStyle.RADAR_WEB))
    }

    @Test
    fun `computeTotalDurationMs respects short clips and avoids forced 8s padding`() {
        val emptyDur = com.example.ui.editor.EditorViewModel.computeTotalDurationMs(emptyList())
        assertEquals(8_000L, emptyDur)

        val shortClip = TimelineClip(
            trackType = TrackType.VIDEO,
            title = "Short Reel",
            startMs = 0L,
            durationMs = 3_200L
        )
        assertEquals(3_200L, com.example.ui.editor.EditorViewModel.computeTotalDurationMs(listOf(shortClip)))
    }

    @Test
    fun `audio fingerprint stays identical on text edits and updates on audio config changes`() {
        val music = TimelineClip(
            trackType = TrackType.MUSIC,
            title = "BGM",
            startMs = 0L,
            durationMs = 5_000L,
            audioConfig = AudioConfig(volume = 0.8f)
        )
        val text = TimelineClip(
            trackType = TrackType.TEXT,
            title = "Title",
            startMs = 0L,
            durationMs = 3_000L
        )
        val base = StudioProject(name = "FP", totalDurationMs = 5_000L, clips = listOf(music, text))
        val textEdited = base.copy(clips = listOf(music, text.copy(title = "Changed Title", posX = 0.3f)))
        val audioEdited = base.copy(clips = listOf(music.copy(audioConfig = music.audioConfig.copy(isMuted = true)), text))

        val fpBase = com.example.domain.engine.AudioExportMixer.buildAudioFingerprint(base)
        val fpText = com.example.domain.engine.AudioExportMixer.buildAudioFingerprint(textEdited)
        val fpAudio = com.example.domain.engine.AudioExportMixer.buildAudioFingerprint(audioEdited)

        assertEquals(fpBase, fpText)
        assertTrue(fpBase != fpAudio)
    }
}
