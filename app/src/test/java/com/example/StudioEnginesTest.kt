package com.example

import com.example.domain.engine.AudioSynthEngine
import com.example.domain.engine.KeyframeInterpolator
import com.example.domain.engine.SpeedRampEngine
import com.example.domain.model.AudioConfig
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
    fun `auto investigative documentary builder populates VFX, charts, comparison grid, and track layers`() {
        val doc = PresetCatalog.buildAutoInvestigativeDocumentary("Test Doc", "Key Finding")
        assertTrue(doc.clips.any { it.vfxConfig?.effectType == VfxEffectType.FILM_GRAIN })
        assertTrue(doc.clips.any { it.chartConfig != null })
        assertTrue(doc.clips.any { it.comparisonGrid?.slotCount == 3 })
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
}
