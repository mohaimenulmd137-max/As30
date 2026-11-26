package com.example

import com.example.domain.engine.AudioSynthEngine
import com.example.domain.engine.KeyframeInterpolator
import com.example.domain.engine.SpeedRampEngine
import com.example.domain.model.AudioConfig
import com.example.domain.model.Keyframe
import com.example.domain.model.KeyframeEasing
import com.example.domain.model.PresetCatalog
import com.example.domain.model.StudioProject
import com.example.domain.model.TimelineClip
import com.example.domain.model.TrackType
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
    fun `auto ducking lowers music gain during active voiceover`() {
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
        val project = StudioProject(name = "Test", clips = listOf(music, voice))

        val normalGain = AudioSynthEngine.computeEffectiveAudioGain(music, project, 1_000L)
        val duckedGain = AudioSynthEngine.computeEffectiveAudioGain(music, project, 4_000L)
        assertEquals(1.0f, normalGain, 0.02f)
        assertEquals(0.25f, duckedGain, 0.02f)
    }

    @Test
    fun `speed ramp evaluates slow mo dip accurately`() {
        val heroRamp = PresetCatalog.speedRampPresets[1]
        val midSpeed = SpeedRampEngine.evaluateSpeedAt(heroRamp, 0.5f)
        assertEquals(0.3f, midSpeed, 0.05f)
    }
}
