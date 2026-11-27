package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.ProjectSerializer
import com.example.domain.engine.AudioExportMixer
import com.example.domain.model.AnimationDirection
import com.example.domain.model.AnimationEasingType
import com.example.domain.model.AnimationSlotConfig
import com.example.domain.model.AudioConfig
import com.example.domain.model.BorderConfig
import com.example.domain.model.BorderShapeStyle
import com.example.domain.model.ClipAnimationProfile
import com.example.domain.model.PresetCatalog
import com.example.domain.model.StudioProject
import com.example.domain.model.StudioTextAlign
import com.example.domain.model.TextLayerConfig
import com.example.domain.model.TimelineClip
import com.example.domain.model.TrackType
import com.example.domain.model.UserAnimationPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("ChitraFlow Studio", appName)
    }

    @Test
    fun `project serializer roundtrips VFX, 60-bar charts, comparison grids, and investigative doc tools`() {
        val doc = PresetCatalog.buildAutoInvestigativeDocumentary("Roundtrip Test", "Investigation Details")
        val entity = ProjectSerializer.toEntity(doc)
        val restored = ProjectSerializer.fromEntity(entity)

        assertEquals(doc.name, restored.name)
        assertEquals(doc.clips.size, restored.clips.size)
        assertEquals(doc.trackLayers.size, restored.trackLayers.size)
        assertEquals(doc.showSafeZoneGuides, restored.showSafeZoneGuides)
        assertEquals(doc.masterVolume, restored.masterVolume, 0.001f)
    }

    @Test
    fun `project serializer preserves formatted text gradient, stroke, alignment, and custom font`() {
        val styledClip = TimelineClip(
            trackType = TrackType.TEXT,
            title = "স্টাইলড টেক্সট",
            startMs = 0L,
            durationMs = 4000L,
            textConfig = TextLayerConfig(
                text = "বাংলা ফর্মেটেড শিরোনাম",
                stylePresetId = "style_cyber_electric",
                useGradient = true,
                gradientStartHex = 0xFF00E5FF,
                gradientEndHex = 0xFFA77BFF,
                strokeColorHex = 0xFF090D16,
                strokeWidthPx = 4.5f,
                shadowRadius = 18f,
                shadowColorHex = 0xCC7C3AED,
                letterSpacingSp = 2.0f,
                lineHeightMult = 1.4f,
                textAlign = StudioTextAlign.LEFT,
                backgroundPill = true,
                pillColorHex = 0xDC112233,
                pillCornerDp = 16f,
                customFontPath = "/data/user/0/app/files/custom_fonts/test.ttf",
                customFontName = "SolaimanLipi"
            )
        )
        val proj = StudioProject(name = "Text Format Test", clips = listOf(styledClip))
        val restored = ProjectSerializer.fromEntity(ProjectSerializer.toEntity(proj))
        val restoredText = restored.clips.first().textConfig!!

        assertEquals("style_cyber_electric", restoredText.stylePresetId)
        assertTrue(restoredText.useGradient)
        assertEquals(0xFF00E5FF, restoredText.gradientStartHex)
        assertEquals(0xFFA77BFF, restoredText.gradientEndHex)
        assertEquals(4.5f, restoredText.strokeWidthPx, 0.01f)
        assertEquals(18f, restoredText.shadowRadius, 0.01f)
        assertEquals(2.0f, restoredText.letterSpacingSp, 0.01f)
        assertEquals(1.4f, restoredText.lineHeightMult, 0.01f)
        assertEquals(StudioTextAlign.LEFT, restoredText.textAlign)
        assertEquals(16f, restoredText.pillCornerDp, 0.01f)
        assertEquals("/data/user/0/app/files/custom_fonts/test.ttf", restoredText.customFontPath)
        assertEquals("SolaimanLipi", restoredText.customFontName)
    }

    @Test
    fun `audio export mixer mixes music ambient pad and locked sfx into non-silent pcm`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val musicClip = TimelineClip(
            trackType = TrackType.MUSIC,
            title = "BGM",
            startMs = 0L,
            durationMs = 2000L,
            lockedSfxId = "sfx_whoosh_0",
            audioConfig = AudioConfig(volume = 1.0f, fadeInMs = 100L, fadeOutMs = 100L)
        )
        val proj = StudioProject(name = "Audio Mix Test", totalDurationMs = 2000L, clips = listOf(musicClip))
        val pcm = AudioExportMixer.mixProjectAudioPcm(context, proj)
        assertTrue(pcm.size >= AudioExportMixer.SAMPLE_RATE * 2)
        assertTrue(pcm.any { it.toInt() != 0 })
    }

    @Test
    fun `green screen chroma key removes green background and keeps subject opaque`() {
        val bmp = android.graphics.Bitmap.createBitmap(4, 4, android.graphics.Bitmap.Config.ARGB_8888)
        // Left half bright green screen, right half crimson subject
        for (y in 0 until 4) {
            for (x in 0 until 4) {
                val c = if (x < 2) android.graphics.Color.rgb(10, 235, 20) else android.graphics.Color.rgb(210, 40, 55)
                bmp.setPixel(x, y, c)
            }
        }
        val keyed = com.example.domain.engine.TransitionAndChromaEngine.applyGreenScreenChromaKey(bmp, 0.55f)
        assertEquals(0, android.graphics.Color.alpha(keyed.getPixel(0, 0)))
        assertEquals(255, android.graphics.Color.alpha(keyed.getPixel(3, 2)))
        bmp.recycle()
        keyed.recycle()
    }

    @Test
    fun `project serializer roundtrips laneIndex, borderConfig, animationProfile, audio Mute Solo, and user animation presets`() {
        val profile = ClipAnimationProfile(
            inSlot = AnimationSlotConfig("text_word_pop", enabled = true, durationMs = 1200L, direction = AnimationDirection.TOP, easing = AnimationEasingType.CUBIC_BEZIER, bezierX1 = 0.2f, bezierY1 = 0.9f),
            loopSlot = AnimationSlotConfig("loop_glow", enabled = true, durationMs = 2200L, repeatCount = 3),
            outSlot = AnimationSlotConfig("out_blur_out", enabled = true, durationMs = 800L),
            blurRadius = 6f,
            shadowStrength = 1.1f,
            skewX = -0.2f,
            skewY = 0.1f,
            perspective = 25f,
            anchorX = 0.3f,
            anchorY = 0.7f
        )
        val clip = TimelineClip(
            trackType = TrackType.TEXT,
            laneIndex = 2,
            title = "SubLayer Text",
            startMs = 500L,
            durationMs = 5000L,
            animationProfile = profile,
            borderConfig = BorderConfig(
                style = BorderShapeStyle.DOUBLE_FRAME,
                thicknessDp = 6f,
                colorHex = 0xFFFFB300,
                secondaryColorHex = 0xFF00E5FF,
                glowRadius = 14f,
                cornerRadiusDp = 18f,
                insetPercent = 0.05f
            ),
            audioConfig = AudioConfig(volume = 1.6f, isMuted = true, isSolo = true)
        )
        val proj = StudioProject(name = "Phase12 Roundtrip", clips = listOf(clip))
        val restored = ProjectSerializer.fromEntity(ProjectSerializer.toEntity(proj)).clips.first()

        assertEquals(2, restored.laneIndex)
        assertEquals(BorderShapeStyle.DOUBLE_FRAME, restored.borderConfig.style)
        assertEquals(14f, restored.borderConfig.glowRadius, 0.01f)
        assertEquals("text_word_pop", restored.animationProfile.inSlot.animationId)
        assertEquals(AnimationDirection.TOP, restored.animationProfile.inSlot.direction)
        assertTrue(restored.animationProfile.loopSlot.enabled)
        assertEquals(25f, restored.animationProfile.perspective, 0.01f)
        assertTrue(restored.audioConfig.isMuted)
        assertTrue(restored.audioConfig.isSolo)

        val presets = listOf(UserAnimationPreset(name = "My Gold Reveal", profile = profile))
        val restoredPresets = ProjectSerializer.userPresetsFromJsonString(ProjectSerializer.userPresetsToJsonString(presets))
        assertEquals(1, restoredPresets.size)
        assertEquals("My Gold Reveal", restoredPresets.first().name)
        assertEquals("loop_glow", restoredPresets.first().profile.loopSlot.animationId)
    }

    @Test
    fun `audio export mixer silences muted music track when no unmuted sources exist`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mutedMusic = TimelineClip(
            trackType = TrackType.MUSIC,
            title = "Muted BGM",
            startMs = 0L,
            durationMs = 1000L,
            audioConfig = AudioConfig(volume = 1.0f, isMuted = true)
        )
        val proj = StudioProject(name = "Muted Audio Test", totalDurationMs = 1000L, clips = listOf(mutedMusic))
        val pcm = AudioExportMixer.mixProjectAudioPcm(context, proj)
        assertTrue(pcm.all { it.toInt() == 0 })
    }

    @Test
    fun `project serializer roundtrips dual transitions, ChartPaletteTheme, MapRadarStyle, coordinates, and God Rays VFX`() {
        val chartClip = TimelineClip(
            trackType = TrackType.GRAPHICS,
            title = "Chart Pro",
            startMs = 0L,
            durationMs = 5000L,
            transitionIn = com.example.domain.model.TransitionType.SLIDE_WARP,
            transitionOut = com.example.domain.model.TransitionType.SPIN_FLASH,
            transitionInDurationMs = 900L,
            transitionOutDurationMs = 1100L,
            chartConfig = com.example.domain.model.ComparisonChartConfig(
                title = "GDP তুলনা",
                unitSuffix = "$",
                barCount = 15,
                paletteTheme = com.example.domain.model.ChartPaletteTheme.DOC_GOLD,
                bars = PresetCatalog.generateDefaultBars(15)
            )
        )
        val mapClip = TimelineClip(
            trackType = TrackType.GRAPHICS,
            laneIndex = 1,
            title = "Radar Route",
            startMs = 0L,
            durationMs = 6000L,
            investigativeConfig = com.example.domain.model.InvestigativeDocConfig(
                toolType = com.example.domain.model.InvestigativeToolType.MAP_RADAR_ROUTE,
                headline = "নৌপথ রুট",
                pointALabel = "খুলনা",
                pointBLabel = "চট্টগ্রাম",
                pointAX = 0.18f,
                pointAY = 0.72f,
                pointBX = 0.84f,
                pointBY = 0.32f,
                pointCLabel = "বরিশাল",
                pointCX = 0.48f,
                pointCY = 0.60f,
                useWaypointC = true,
                distanceLabel = "৩১০ কি.মি.",
                mapStyle = com.example.domain.model.MapRadarStyle.MARITIME_NAVAL
            )
        )
        val vfxClip = TimelineClip(
            trackType = TrackType.VFX,
            title = "God Rays",
            startMs = 0L,
            durationMs = 6000L,
            vfxConfig = com.example.domain.model.VfxConfig(
                effectType = com.example.domain.model.VfxEffectType.VOLUMETRIC_GOD_RAYS,
                intensity = 0.8f,
                speed = 1.4f
            )
        )

        val proj = StudioProject(name = "Phase 3 & 4", clips = listOf(chartClip, mapClip, vfxClip))
        val restored = ProjectSerializer.fromEntity(ProjectSerializer.toEntity(proj))

        val rChart = restored.clips[0]
        assertEquals(com.example.domain.model.TransitionType.SLIDE_WARP, rChart.transitionIn)
        assertEquals(com.example.domain.model.TransitionType.SPIN_FLASH, rChart.transitionOut)
        assertEquals(900L, rChart.transitionInDurationMs)
        assertEquals(1100L, rChart.transitionOutDurationMs)
        assertEquals(com.example.domain.model.ChartPaletteTheme.DOC_GOLD, rChart.chartConfig?.paletteTheme)
        assertEquals("$", rChart.chartConfig?.unitSuffix)

        val rMap = restored.clips[1].investigativeConfig!!
        assertEquals(0.18f, rMap.pointAX, 0.001f)
        assertEquals(0.72f, rMap.pointAY, 0.001f)
        assertEquals("বরিশাল", rMap.pointCLabel)
        assertTrue(rMap.useWaypointC)
        assertEquals("৩১০ কি.মি.", rMap.distanceLabel)
        assertEquals(com.example.domain.model.MapRadarStyle.MARITIME_NAVAL, rMap.mapStyle)

        val rVfx = restored.clips[2].vfxConfig!!
        assertEquals(com.example.domain.model.VfxEffectType.VOLUMETRIC_GOD_RAYS, rVfx.effectType)
    }

    @Test
    fun `project serializer roundtrips zero-item custom chart and all 7 ChartVisualStyle options`() {
        val emptyChartClip = TimelineClip(
            trackType = TrackType.GRAPHICS,
            title = "Zero Start Chart",
            startMs = 0L,
            durationMs = 4000L,
            chartConfig = com.example.domain.model.ComparisonChartConfig(
                title = "Empty Custom Chart",
                barCount = 0,
                chartStyle = com.example.domain.model.ChartVisualStyle.AREA_WAVE,
                bars = emptyList()
            )
        )
        val styleClips = com.example.domain.model.ChartVisualStyle.entries.mapIndexed { idx, style ->
            TimelineClip(
                trackType = TrackType.GRAPHICS,
                title = style.name,
                startMs = idx * 1000L,
                durationMs = 3000L,
                chartConfig = com.example.domain.model.ComparisonChartConfig(
                    title = style.labelBn,
                    barCount = 3,
                    chartStyle = style,
                    isHorizontal = style == com.example.domain.model.ChartVisualStyle.HORIZONTAL_BAR,
                    bars = listOf(
                        com.example.domain.model.ChartBarItem(label = "A", value = 30f, colorHex = 0xFF00E5FF),
                        com.example.domain.model.ChartBarItem(label = "B", value = 55f, colorHex = 0xFFFFB300),
                        com.example.domain.model.ChartBarItem(label = "C", value = 85f, colorHex = 0xFF2CE59B)
                    )
                )
            )
        }
        val proj = StudioProject(name = "Chart Styles Roundtrip", clips = listOf(emptyChartClip) + styleClips)
        val restored = ProjectSerializer.fromEntity(ProjectSerializer.toEntity(proj))

        val restoredEmpty = restored.clips[0].chartConfig!!
        assertEquals(0, restoredEmpty.barCount)
        assertTrue(restoredEmpty.bars.isEmpty())
        assertEquals(com.example.domain.model.ChartVisualStyle.AREA_WAVE, restoredEmpty.chartStyle)

        com.example.domain.model.ChartVisualStyle.entries.forEachIndexed { idx, expectedStyle ->
            val restoredChart = restored.clips[idx + 1].chartConfig!!
            assertEquals(expectedStyle, restoredChart.chartStyle)
            assertEquals(3, restoredChart.barCount)
        }
    }

    @Test
    fun `audio export mixer renders 10 second multi-track mix with ambient BGM, voiceover, and SFX cleanly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val music = TimelineClip(
            trackType = TrackType.MUSIC,
            title = "BGM 10s",
            startMs = 0L,
            durationMs = 10_000L,
            audioConfig = AudioConfig(volume = 0.9f, autoDuckingEnabled = true)
        )
        val voice = TimelineClip(
            trackType = TrackType.VOICEOVER,
            title = "VO",
            startMs = 2_000L,
            durationMs = 3_000L,
            lockedSfxId = "sfx_whoosh_1",
            audioConfig = AudioConfig(volume = 1.2f)
        )
        val proj = StudioProject(name = "10s Mix", totalDurationMs = 10_000L, clips = listOf(music, voice))
        val pcm = AudioExportMixer.mixProjectAudioPcm(context, proj)
        assertEquals(AudioExportMixer.SAMPLE_RATE * 10, pcm.size)
        assertTrue(pcm.any { it.toInt() != 0 })
    }
}
