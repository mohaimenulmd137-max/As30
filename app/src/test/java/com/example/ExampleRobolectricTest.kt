package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.ProjectSerializer
import com.example.domain.engine.AudioExportMixer
import com.example.domain.model.AudioConfig
import com.example.domain.model.PresetCatalog
import com.example.domain.model.StudioProject
import com.example.domain.model.StudioTextAlign
import com.example.domain.model.TextLayerConfig
import com.example.domain.model.TimelineClip
import com.example.domain.model.TrackType
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
}
