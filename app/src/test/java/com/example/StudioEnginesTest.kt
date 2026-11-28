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

    @Test
    fun `computeTotalDurationMs supports multi-minute and multi-hour clips without 10-minute truncation`() {
        val fiveMinClip = TimelineClip(
            trackType = TrackType.VIDEO,
            title = "5m Documentary",
            startMs = 0L,
            durationMs = 300_000L,
            sourceDurationMs = 300_000L
        )
        assertEquals(300_000L, com.example.ui.editor.EditorViewModel.computeTotalDurationMs(listOf(fiveMinClip)))

        val twentyMinClip = TimelineClip(
            trackType = TrackType.VIDEO,
            title = "20m Lecture",
            startMs = 0L,
            durationMs = 1_200_000L,
            sourceDurationMs = 1_200_000L
        )
        assertEquals(1_200_000L, com.example.ui.editor.EditorViewModel.computeTotalDurationMs(listOf(twentyMinClip)))
    }

    @Test
    fun `TimelineClip computeSourcePositionMs respects trimStartMs, playbackSpeed, and bounds`() {
        val trimmedClip = TimelineClip(
            trackType = TrackType.VIDEO,
            title = "Trimmed",
            startMs = 2_000L,
            durationMs = 10_000L,
            sourceDurationMs = 60_000L,
            trimStartMs = 15_000L,
            playbackSpeed = 1.5f
        )
        assertEquals(15_000L, trimmedClip.computeSourcePositionMs(2_000L))
        // 4_000ms timeline elapsed at 1.5x => +6_000ms source => 21_000ms
        assertEquals(21_000L, trimmedClip.computeSourcePositionMs(6_000L))
        assertEquals(30_000L, trimmedClip.trimEndMs)
    }

    @Test
    fun `applyTrimToClip updates trimStartMs on left-trim and clamps video duration to remaining source`() {
        val videoClip = TimelineClip(
            trackType = TrackType.VIDEO,
            title = "Video 30s",
            startMs = 0L,
            durationMs = 30_000L,
            sourceDurationMs = 30_000L,
            trimStartMs = 0L,
            playbackSpeed = 1.0f
        )
        // Drag left handle right by 5s (endMs stays 30_000L)
        val leftTrimmed = com.example.ui.editor.EditorViewModel.applyTrimToClip(
            clip = videoClip,
            newStartMs = 5_000L,
            newDurationMs = 25_000L
        )
        assertEquals(5_000L, leftTrimmed.startMs)
        assertEquals(25_000L, leftTrimmed.durationMs)
        assertEquals(5_000L, leftTrimmed.trimStartMs)

        // Try extending right handle past remaining 25s source -> clamped to 25_000L
        val overExtended = com.example.ui.editor.EditorViewModel.applyTrimToClip(
            clip = leftTrimmed,
            newStartMs = 5_000L,
            newDurationMs = 90_000L
        )
        assertEquals(25_000L, overExtended.durationMs)

        // Music clips can loop past sourceDurationMs
        val musicClip = videoClip.copy(trackType = TrackType.MUSIC, trimStartMs = 0L, durationMs = 30_000L)
        val extendedMusic = com.example.ui.editor.EditorViewModel.applyTrimToClip(
            clip = musicClip,
            newStartMs = 0L,
            newDurationMs = 90_000L
        )
        assertEquals(90_000L, extendedMusic.durationMs)
    }

    @Test
    fun `splitClipAt advances trimStartMs on second half so playback never restarts at zero`() {
        val clip = TimelineClip(
            trackType = TrackType.VIDEO,
            title = "Long Clip",
            startMs = 1_000L,
            durationMs = 20_000L,
            sourceDurationMs = 60_000L,
            trimStartMs = 10_000L,
            playbackSpeed = 1.0f
        )
        val pair = com.example.ui.editor.EditorViewModel.splitClipAt(clip, 7_000L)!!
        val (first, second) = pair
        assertEquals(1_000L, first.startMs)
        assertEquals(6_000L, first.durationMs)
        assertEquals(10_000L, first.trimStartMs)

        assertEquals(7_000L, second.startMs)
        assertEquals(14_000L, second.durationMs)
        assertEquals(16_000L, second.trimStartMs)
        assertEquals(16_000L, second.computeSourcePositionMs(7_000L))
    }

    @Test
    fun `computeSpeedAdjustedClip rescales timeline duration proportionally`() {
        val clip = TimelineClip(
            trackType = TrackType.VIDEO,
            title = "Speed Test",
            startMs = 0L,
            durationMs = 20_000L,
            sourceDurationMs = 20_000L,
            trimStartMs = 0L,
            playbackSpeed = 1.0f
        )
        val doubleSpeed = com.example.ui.editor.EditorViewModel.computeSpeedAdjustedClip(clip, 2.0f)
        assertEquals(2.0f, doubleSpeed.playbackSpeed, 0.001f)
        assertEquals(10_000L, doubleSpeed.durationMs)

        val halfSpeed = com.example.ui.editor.EditorViewModel.computeSpeedAdjustedClip(doubleSpeed, 0.5f)
        assertEquals(0.5f, halfSpeed.playbackSpeed, 0.001f)
        assertEquals(40_000L, halfSpeed.durationMs)
    }

    @Test
    fun `map preset catalog covers all 6 categories with regions routes markers and keyframes`() {
        val categoriesPresent = com.example.domain.map.MapPresetCatalog.presets.map { it.category }.toSet()
        assertEquals(com.example.domain.map.MapPresetCategory.entries.toSet(), categoriesPresent)
        assertTrue(com.example.domain.map.MapPresetCatalog.presets.all { it.config.keyframes.isNotEmpty() })
    }

    @Test
    fun `world cartography camera flight dips zoom mid-arc and interpolates coordinates`() {
        val config = com.example.domain.map.UniversalMapConfig(
            keyframes = listOf(
                com.example.domain.map.MapCameraKeyframe("k1", "Dhaka", 0f, 23.81f, 90.41f, 4.0f, 0f, 0f, com.example.domain.map.MapCameraEasing.LINEAR),
                com.example.domain.map.MapCameraKeyframe("k2", "London", 1f, 51.51f, -0.13f, 4.0f, 10f, 20f, com.example.domain.map.MapCameraEasing.CINEMA_FLIGHT)
            )
        )
        val start = com.example.domain.map.WorldCartographyData.evaluateCameraAt(config, 0f)
        val mid = com.example.domain.map.WorldCartographyData.evaluateCameraAt(config, 0.5f)
        val end = com.example.domain.map.WorldCartographyData.evaluateCameraAt(config, 1f)

        assertEquals(23.81f, start.centerLat, 0.01f)
        assertEquals(51.51f, end.centerLat, 0.01f)
        assertTrue("CINEMA_FLIGHT should dip zoom mid-flight", mid.zoom < 4.0f)
        assertTrue(mid.centerLat in 23.8f..51.6f)
    }

    @Test
    fun `projectLatLon and unprojectScreenPoint roundtrip accurately in both 2D and 3D Globe modes`() {
        val camera = com.example.domain.map.EvaluatedMapCamera(
            centerLat = 23.81f,
            centerLon = 90.41f,
            zoom = 2.5f,
            rotationDeg = 12f,
            tiltDeg = 15f
        )
        val w = 1280f
        val h = 720f
        val dhakaLat = 23.8103f
        val dhakaLon = 90.4125f

        // 2D World Map round-trip
        val p2d = com.example.domain.map.WorldCartographyData.projectLatLon(
            dhakaLat, dhakaLon, camera, com.example.domain.map.MapProjectionMode.WORLD_2D, w, h
        )
        assertTrue(p2d.visible)
        val back2d = com.example.domain.map.WorldCartographyData.unprojectScreenPoint(
            p2d.x, p2d.y, camera, com.example.domain.map.MapProjectionMode.WORLD_2D, w, h
        )!!
        assertEquals(dhakaLat, back2d.lat, 0.2f)
        assertEquals(dhakaLon, back2d.lon, 0.2f)

        // 3D Globe round-trip + backface culling
        val p3d = com.example.domain.map.WorldCartographyData.projectLatLon(
            dhakaLat, dhakaLon, camera, com.example.domain.map.MapProjectionMode.GLOBE_3D, w, h
        )
        assertTrue(p3d.visible)
        val back3d = com.example.domain.map.WorldCartographyData.unprojectScreenPoint(
            p3d.x, p3d.y, camera, com.example.domain.map.MapProjectionMode.GLOBE_3D, w, h
        )!!
        assertEquals(dhakaLat, back3d.lat, 0.35f)
        assertEquals(dhakaLon, back3d.lon, 0.35f)

        // Antipode behind globe must be hidden
        val antipode = com.example.domain.map.WorldCartographyData.projectLatLon(
            -23.81f, -89.59f, camera, com.example.domain.map.MapProjectionMode.GLOBE_3D, w, h
        )
        assertTrue(!antipode.visible)

        // Geodesic interpolation passes smoothly between Dhaka and London
        val arc = com.example.domain.map.WorldCartographyData.interpolateGeodesic(
            com.example.domain.map.MapLatLon(23.81f, 90.41f),
            com.example.domain.map.MapLatLon(51.51f, -0.13f),
            steps = 16
        )
        assertEquals(17, arc.size)
        assertTrue(arc[8].lat > 37f)
    }

    @Test
    fun `export dimensions and target bitrates scale cleanly across 720p to 4K and H264 vs H265`() {
        val (w720, h720) = com.example.domain.engine.export.DeviceCapabilityAnalyzer.computeExportDimensions(
            com.example.domain.model.AspectRatioPreset.LANDSCAPE_16_9,
            com.example.domain.model.PreviewResolution.P720
        )
        assertEquals(1280, w720)
        assertEquals(720, h720)

        val (w1440, h1440) = com.example.domain.engine.export.DeviceCapabilityAnalyzer.computeExportDimensions(
            com.example.domain.model.AspectRatioPreset.LANDSCAPE_16_9,
            com.example.domain.model.PreviewResolution.QHD1440
        )
        assertEquals(2560, w1440)
        assertEquals(1440, h1440)

        val (w4k, h4k) = com.example.domain.engine.export.DeviceCapabilityAnalyzer.computeExportDimensions(
            com.example.domain.model.AspectRatioPreset.PORTRAIT_9_16,
            com.example.domain.model.PreviewResolution.UHD4K
        )
        assertEquals(2160, w4k)
        assertEquals(3840, h4k)

        val h264Rate = com.example.domain.engine.export.DeviceCapabilityAnalyzer.computeTargetBitrate(
            com.example.domain.model.PreviewResolution.P1080,
            com.example.domain.model.ExportFps.FPS30,
            com.example.domain.engine.export.ExportCodec.H264,
            com.example.domain.engine.export.ExportQuality.HIGH,
            com.example.domain.engine.export.ExportMode.SMART_AUTO
        )
        val h265Rate = com.example.domain.engine.export.DeviceCapabilityAnalyzer.computeTargetBitrate(
            com.example.domain.model.PreviewResolution.P1080,
            com.example.domain.model.ExportFps.FPS30,
            com.example.domain.engine.export.ExportCodec.H265,
            com.example.domain.engine.export.ExportQuality.HIGH,
            com.example.domain.engine.export.ExportMode.SMART_AUTO
        )
        assertTrue("H265 should target smaller bitrate (~72%) than H264", h265Rate < h264Rate)
    }

    @Test
    fun `smart auto export configuration adapts safely to low-ram and multi-hour 5-6 GB scenarios`() {
        val lowRamReport = com.example.domain.engine.export.EncoderCapabilityReport(
            supportedResolutions = setOf(com.example.domain.model.PreviewResolution.P720, com.example.domain.model.PreviewResolution.P1080),
            supportedFpsByResolution = mapOf(
                com.example.domain.model.PreviewResolution.P720 to setOf(com.example.domain.model.ExportFps.FPS24, com.example.domain.model.ExportFps.FPS30),
                com.example.domain.model.PreviewResolution.P1080 to setOf(com.example.domain.model.ExportFps.FPS30)
            ),
            supportedCodecs = setOf(com.example.domain.engine.export.ExportCodec.H264),
            hasHardwareH264 = true,
            hasHardwareH265 = false,
            preferredEncoderH264 = null,
            preferredEncoderH265 = null,
            glesVersion = "GLES 3.0",
            totalRamMb = 2048L,
            availableRamMb = 400L,
            heapLimitMb = 256L,
            isLowRamDevice = true
        )
        val longProj = StudioProject(name = "2h Documentary", totalDurationMs = 7_200_000L, previewResolution = com.example.domain.model.PreviewResolution.UHD4K)
        val lowCfg = com.example.domain.engine.export.DeviceCapabilityAnalyzer.resolveSmartAutoConfig(lowRamReport, longProj)
        assertEquals(com.example.domain.model.PreviewResolution.P720, lowCfg.resolution)
        assertEquals(com.example.domain.model.ExportFps.FPS30, lowCfg.fps)
        assertEquals(com.example.domain.engine.export.ExportQuality.MEDIUM, lowCfg.quality)

        val highRamHevcReport = lowRamReport.copy(
            supportedResolutions = setOf(com.example.domain.model.PreviewResolution.P720, com.example.domain.model.PreviewResolution.P1080, com.example.domain.model.PreviewResolution.UHD4K),
            supportedCodecs = setOf(com.example.domain.engine.export.ExportCodec.H264, com.example.domain.engine.export.ExportCodec.H265),
            hasHardwareH265 = true,
            totalRamMb = 8192L,
            availableRamMb = 4200L,
            heapLimitMb = 512L,
            isLowRamDevice = false
        )
        val highCfg = com.example.domain.engine.export.DeviceCapabilityAnalyzer.resolveSmartAutoConfig(highRamHevcReport, longProj)
        assertEquals(com.example.domain.engine.export.ExportCodec.H265, highCfg.codec)
    }

    @Test
    fun `storage estimator accurately predicts 5-6+ GB multi-hour exports and calculates power-of-two bitmap subsampling`() {
        // 1.5 hours at ~9.5 Mbps ~ 6.4+ GB
        val est5PlusGb = com.example.domain.engine.export.ExportStorageManager.estimateOutputBytes(
            durationMs = 5_400_000L,
            videoBitrateBps = 9_500_000
        )
        assertTrue("Expected > 5 GB estimate, got $est5PlusGb", est5PlusGb > 5L * 1024L * 1024L * 1024L)
        assertTrue(com.example.domain.engine.export.ExportStorageManager.formatBytesReadable(est5PlusGb).endsWith("GB"))

        // 48MP photo downsampled to 1920x1080 should use inSampleSize >= 4 to protect 256MB heap
        val sample48Mp = com.example.domain.engine.export.MemorySafeMediaFrameProvider.calculateInSampleSize(
            rawW = 8000,
            rawH = 6000,
            reqW = 1920,
            reqH = 1080
        )
        assertTrue("Expected sample size >= 4, got $sample48Mp", sample48Mp >= 4)
        assertEquals(0, sample48Mp and (sample48Mp - 1)) // Power of 2
    }
}


