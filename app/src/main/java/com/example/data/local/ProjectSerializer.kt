package com.example.data.local

import com.example.domain.model.AspectRatioPreset
import com.example.domain.model.AudioConfig
import com.example.domain.model.BlendModeOption
import com.example.domain.model.Keyframe
import com.example.domain.model.KeyframeEasing
import com.example.domain.model.LowerThirdTemplate
import com.example.domain.model.LumetriColorGrade
import com.example.domain.model.MaskConfig
import com.example.domain.model.MaskShape
import com.example.domain.model.MediaSource
import com.example.domain.model.PreviewResolution
import com.example.domain.model.SpeedPoint
import com.example.domain.model.SpeedRampProfile
import com.example.domain.model.StudioProject
import com.example.domain.model.TextLayerConfig
import com.example.domain.model.TimelineClip
import com.example.domain.model.TrackType
import org.json.JSONArray
import org.json.JSONObject

object ProjectSerializer {

    fun toEntity(project: StudioProject): ProjectEntity {
        val json = toJson(project).toString()
        return ProjectEntity(
            id = project.id,
            name = project.name,
            aspectRatio = project.aspectRatio.name,
            previewResolution = project.previewResolution.name,
            thumbnailDrawable = project.thumbnailDrawable,
            totalDurationMs = project.totalDurationMs,
            lastEditedAt = project.lastEditedAt,
            clipCount = project.clips.size,
            payloadJson = json
        )
    }

    fun fromEntity(entity: ProjectEntity): StudioProject {
        return runCatching {
            fromJson(JSONObject(entity.payloadJson))
        }.getOrElse {
            StudioProject(
                id = entity.id,
                name = entity.name,
                aspectRatio = AspectRatioPreset.entries.firstOrNull { it.name == entity.aspectRatio } ?: AspectRatioPreset.PORTRAIT_9_16,
                previewResolution = PreviewResolution.entries.firstOrNull { it.name == entity.previewResolution } ?: PreviewResolution.P1080,
                thumbnailDrawable = entity.thumbnailDrawable,
                totalDurationMs = entity.totalDurationMs,
                lastEditedAt = entity.lastEditedAt
            )
        }
    }

    private fun toJson(p: StudioProject): JSONObject = JSONObject().apply {
        put("id", p.id)
        put("name", p.name)
        put("aspectRatio", p.aspectRatio.name)
        put("previewResolution", p.previewResolution.name)
        put("thumbnailDrawable", p.thumbnailDrawable)
        put("totalDurationMs", p.totalDurationMs)
        put("lastEditedAt", p.lastEditedAt)
        put("clips", JSONArray().apply { p.clips.forEach { put(clipToJson(it)) } })
    }

    private fun fromJson(obj: JSONObject): StudioProject {
        val clipsArr = obj.optJSONArray("clips") ?: JSONArray()
        val clips = buildList {
            for (i in 0 until clipsArr.length()) {
                add(clipFromJson(clipsArr.getJSONObject(i)))
            }
        }
        return StudioProject(
            id = obj.getString("id"),
            name = obj.getString("name"),
            aspectRatio = AspectRatioPreset.entries.firstOrNull { it.name == obj.optString("aspectRatio") } ?: AspectRatioPreset.PORTRAIT_9_16,
            previewResolution = PreviewResolution.entries.firstOrNull { it.name == obj.optString("previewResolution") } ?: PreviewResolution.P1080,
            thumbnailDrawable = obj.optString("thumbnailDrawable", "img_tpl_documentary"),
            totalDurationMs = obj.optLong("totalDurationMs", 15_000L),
            lastEditedAt = obj.optLong("lastEditedAt", System.currentTimeMillis()),
            clips = clips
        )
    }

    private fun clipToJson(c: TimelineClip): JSONObject = JSONObject().apply {
        put("id", c.id)
        put("trackType", c.trackType.name)
        put("title", c.title)
        put("startMs", c.startMs)
        put("durationMs", c.durationMs)
        put("posX", c.posX.toDouble())
        put("posY", c.posY.toDouble())
        put("scale", c.scale.toDouble())
        put("rotation", c.rotation.toDouble())
        put("opacity", c.opacity.toDouble())
        put("inAnimationId", c.inAnimationId)
        put("outAnimationId", c.outAnimationId)
        put("loopAnimationId", c.loopAnimationId)
        put("lockedSfxId", c.lockedSfxId ?: "")
        put("blendMode", c.blendMode.name)
        put("keyframes", JSONArray().apply {
            c.keyframes.forEach { k ->
                put(JSONObject().apply {
                    put("id", k.id)
                    put("timeOffsetMs", k.timeOffsetMs)
                    put("posX", k.posX.toDouble())
                    put("posY", k.posY.toDouble())
                    put("scale", k.scale.toDouble())
                    put("rotation", k.rotation.toDouble())
                    put("opacity", k.opacity.toDouble())
                    put("easing", k.easing.name)
                })
            }
        })
        put("colorGrade", JSONObject().apply {
            put("brightness", c.colorGrade.brightness.toDouble())
            put("contrast", c.colorGrade.contrast.toDouble())
            put("saturation", c.colorGrade.saturation.toDouble())
            put("temperature", c.colorGrade.temperature.toDouble())
            put("glow", c.colorGrade.glow.toDouble())
            put("blurRadius", c.colorGrade.blurRadius.toDouble())
            put("vignette", c.colorGrade.vignette.toDouble())
            put("masterCurve", floatListToJson(c.colorGrade.masterCurve))
            put("redCurve", floatListToJson(c.colorGrade.redCurve))
            put("greenCurve", floatListToJson(c.colorGrade.greenCurve))
            put("blueCurve", floatListToJson(c.colorGrade.blueCurve))
        })
        put("speedRamp", JSONObject().apply {
            put("presetName", c.speedRamp.presetName)
            put("points", JSONArray().apply {
                c.speedRamp.points.forEach { sp ->
                    put(JSONObject().apply {
                        put("t", sp.normalizedTime.toDouble())
                        put("s", sp.speed.toDouble())
                    })
                }
            })
        })
        put("maskConfig", JSONObject().apply {
            put("shape", c.maskConfig.shape.name)
            put("feather", c.maskConfig.feather.toDouble())
            put("scale", c.maskConfig.scale.toDouble())
            put("inverted", c.maskConfig.inverted)
        })
        c.textConfig?.let { tc ->
            put("textConfig", JSONObject().apply {
                put("text", tc.text)
                put("subtitle", tc.subtitle)
                put("fontId", tc.fontId)
                put("fontSizeSp", tc.fontSizeSp.toDouble())
                put("textColorHex", tc.textColorHex)
                put("accentColorHex", tc.accentColorHex)
                put("karaokeHighlight", tc.karaokeHighlight)
                put("backgroundPill", tc.backgroundPill)
                put("lowerThirdTemplate", tc.lowerThirdTemplate.name)
            })
        }
        put("audioConfig", JSONObject().apply {
            put("volume", c.audioConfig.volume.toDouble())
            put("fadeInMs", c.audioConfig.fadeInMs)
            put("fadeOutMs", c.audioConfig.fadeOutMs)
            put("autoDuckingEnabled", c.audioConfig.autoDuckingEnabled)
            put("duckingFloor", c.audioConfig.duckingFloor.toDouble())
            put("noiseReductionEnabled", c.audioConfig.noiseReductionEnabled)
            put("audioPath", c.audioConfig.audioPath ?: "")
            put("waveformSeed", c.audioConfig.waveformSeed)
        })
        c.mediaSource?.let { ms ->
            put("mediaSource", JSONObject().apply {
                put("uriString", ms.uriString ?: "")
                put("drawableName", ms.drawableName ?: "")
                put("isVideo", ms.isVideo)
                put("label", ms.label)
            })
        }
    }

    private fun clipFromJson(o: JSONObject): TimelineClip {
        val kfArr = o.optJSONArray("keyframes") ?: JSONArray()
        val keyframes = buildList {
            for (i in 0 until kfArr.length()) {
                val k = kfArr.getJSONObject(i)
                add(
                    Keyframe(
                        id = k.optString("id", "$i"),
                        timeOffsetMs = k.optLong("timeOffsetMs", 0L),
                        posX = k.optDouble("posX", 0.0).toFloat(),
                        posY = k.optDouble("posY", 0.0).toFloat(),
                        scale = k.optDouble("scale", 1.0).toFloat(),
                        rotation = k.optDouble("rotation", 0.0).toFloat(),
                        opacity = k.optDouble("opacity", 1.0).toFloat(),
                        easing = KeyframeEasing.entries.firstOrNull { it.name == k.optString("easing") } ?: KeyframeEasing.EASE_IN_OUT
                    )
                )
            }
        }
        val cg = o.optJSONObject("colorGrade")
        val colorGrade = if (cg != null) {
            LumetriColorGrade(
                brightness = cg.optDouble("brightness", 0.0).toFloat(),
                contrast = cg.optDouble("contrast", 1.0).toFloat(),
                saturation = cg.optDouble("saturation", 1.0).toFloat(),
                temperature = cg.optDouble("temperature", 0.0).toFloat(),
                glow = cg.optDouble("glow", 0.0).toFloat(),
                blurRadius = cg.optDouble("blurRadius", 0.0).toFloat(),
                vignette = cg.optDouble("vignette", 0.12).toFloat(),
                masterCurve = jsonToFloatList(cg.optJSONArray("masterCurve"), listOf(0f, 0.25f, 0.5f, 0.75f, 1f)),
                redCurve = jsonToFloatList(cg.optJSONArray("redCurve"), listOf(0f, 0.25f, 0.5f, 0.75f, 1f)),
                greenCurve = jsonToFloatList(cg.optJSONArray("greenCurve"), listOf(0f, 0.25f, 0.5f, 0.75f, 1f)),
                blueCurve = jsonToFloatList(cg.optJSONArray("blueCurve"), listOf(0f, 0.25f, 0.5f, 0.75f, 1f))
            )
        } else LumetriColorGrade()

        val sr = o.optJSONObject("speedRamp")
        val speedRamp = if (sr != null) {
            val ptsArr = sr.optJSONArray("points") ?: JSONArray()
            val pts = buildList {
                for (i in 0 until ptsArr.length()) {
                    val p = ptsArr.getJSONObject(i)
                    add(SpeedPoint(p.optDouble("t", 0.0).toFloat(), p.optDouble("s", 1.0).toFloat()))
                }
            }
            SpeedRampProfile(sr.optString("presetName", "Normal"), pts.ifEmpty { SpeedRampProfile().points })
        } else SpeedRampProfile()

        val mc = o.optJSONObject("maskConfig")
        val maskConfig = if (mc != null) {
            MaskConfig(
                shape = MaskShape.entries.firstOrNull { it.name == mc.optString("shape") } ?: MaskShape.NONE,
                feather = mc.optDouble("feather", 0.15).toFloat(),
                scale = mc.optDouble("scale", 0.75).toFloat(),
                inverted = mc.optBoolean("inverted", false)
            )
        } else MaskConfig()

        val tc = o.optJSONObject("textConfig")
        val textConfig = if (tc != null) {
            TextLayerConfig(
                text = tc.optString("text", ""),
                subtitle = tc.optString("subtitle", ""),
                fontId = tc.optString("fontId", "hind_siliguri"),
                fontSizeSp = tc.optDouble("fontSizeSp", 24.0).toFloat(),
                textColorHex = tc.optLong("textColorHex", 0xFFF3F6FC),
                accentColorHex = tc.optLong("accentColorHex", 0xFFFFB300),
                karaokeHighlight = tc.optBoolean("karaokeHighlight", false),
                backgroundPill = tc.optBoolean("backgroundPill", false),
                lowerThirdTemplate = LowerThirdTemplate.entries.firstOrNull { it.name == tc.optString("lowerThirdTemplate") } ?: LowerThirdTemplate.NONE
            )
        } else null

        val ac = o.optJSONObject("audioConfig")
        val audioConfig = if (ac != null) {
            AudioConfig(
                volume = ac.optDouble("volume", 1.0).toFloat(),
                fadeInMs = ac.optLong("fadeInMs", 300L),
                fadeOutMs = ac.optLong("fadeOutMs", 400L),
                autoDuckingEnabled = ac.optBoolean("autoDuckingEnabled", true),
                duckingFloor = ac.optDouble("duckingFloor", 0.25).toFloat(),
                noiseReductionEnabled = ac.optBoolean("noiseReductionEnabled", false),
                audioPath = ac.optString("audioPath").takeIf { it.isNotBlank() },
                waveformSeed = ac.optInt("waveformSeed", 7)
            )
        } else AudioConfig()

        val ms = o.optJSONObject("mediaSource")
        val mediaSource = if (ms != null) {
            MediaSource(
                uriString = ms.optString("uriString").takeIf { it.isNotBlank() },
                drawableName = ms.optString("drawableName").takeIf { it.isNotBlank() },
                isVideo = ms.optBoolean("isVideo", false),
                label = ms.optString("label", "Media")
            )
        } else null

        return TimelineClip(
            id = o.getString("id"),
            trackType = TrackType.entries.firstOrNull { it.name == o.optString("trackType") } ?: TrackType.VIDEO,
            title = o.optString("title", "Clip"),
            startMs = o.optLong("startMs", 0L),
            durationMs = o.optLong("durationMs", 4000L),
            posX = o.optDouble("posX", 0.0).toFloat(),
            posY = o.optDouble("posY", 0.0).toFloat(),
            scale = o.optDouble("scale", 1.0).toFloat(),
            rotation = o.optDouble("rotation", 0.0).toFloat(),
            opacity = o.optDouble("opacity", 1.0).toFloat(),
            keyframes = keyframes,
            inAnimationId = o.optString("inAnimationId", "in_fade"),
            outAnimationId = o.optString("outAnimationId", "out_fade"),
            loopAnimationId = o.optString("loopAnimationId", "loop_none"),
            lockedSfxId = o.optString("lockedSfxId").takeIf { it.isNotBlank() },
            colorGrade = colorGrade,
            speedRamp = speedRamp,
            maskConfig = maskConfig,
            blendMode = BlendModeOption.entries.firstOrNull { it.name == o.optString("blendMode") } ?: BlendModeOption.NORMAL,
            textConfig = textConfig,
            audioConfig = audioConfig,
            mediaSource = mediaSource
        )
    }

    private fun floatListToJson(list: List<Float>) = JSONArray().apply { list.forEach { put(it.toDouble()) } }

    private fun jsonToFloatList(arr: JSONArray?, fallback: List<Float>): List<Float> {
        if (arr == null || arr.length() != fallback.size) return fallback
        return buildList {
            for (i in 0 until arr.length()) add(arr.optDouble(i, fallback[i].toDouble()).toFloat())
        }
    }
}
