package com.example.data.local

import com.example.domain.model.AnimationDirection
import com.example.domain.model.AnimationEasingType
import com.example.domain.model.AnimationSlotConfig
import com.example.domain.model.AspectRatioPreset
import com.example.domain.model.AudioConfig
import com.example.domain.model.BlendModeOption
import com.example.domain.model.BorderConfig
import com.example.domain.model.BorderShapeStyle
import com.example.domain.model.ChartBarItem
import com.example.domain.model.ChartPaletteTheme
import com.example.domain.model.ChartVisualStyle
import com.example.domain.model.ClipAnimationProfile
import com.example.domain.model.ComparisonChartConfig
import com.example.domain.model.ComparisonGridConfig
import com.example.domain.model.ComparisonSlot
import com.example.domain.model.GeometricShape
import com.example.domain.model.InvestigativeDocConfig
import com.example.domain.model.InvestigativeToolType
import com.example.domain.model.Keyframe
import com.example.domain.model.KeyframeEasing
import com.example.domain.model.LowerThirdTemplate
import com.example.domain.model.LumetriColorGrade
import com.example.domain.model.MapRadarStyle
import com.example.domain.model.MaskConfig
import com.example.domain.model.MaskShape
import com.example.domain.model.MediaSource
import com.example.domain.model.PreviewResolution
import com.example.domain.model.SpeedPoint
import com.example.domain.model.SpeedRampProfile
import com.example.domain.model.StudioProject
import com.example.domain.model.TextLayerConfig
import com.example.domain.model.TimelineClip
import com.example.domain.model.TrackLayerState
import com.example.domain.model.TrackType
import com.example.domain.model.TransitionType
import com.example.domain.model.UserAnimationPreset
import com.example.domain.model.VfxConfig
import com.example.domain.model.VfxEffectType
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
                aspectRatio = AspectRatioPreset.entries.firstOrNull { it.name == entity.aspectRatio } ?: AspectRatioPreset.LANDSCAPE_16_9,
                previewResolution = PreviewResolution.entries.firstOrNull { it.name == entity.previewResolution } ?: PreviewResolution.P1080,
                thumbnailDrawable = entity.thumbnailDrawable,
                totalDurationMs = entity.totalDurationMs,
                lastEditedAt = entity.lastEditedAt
            )
        }
    }

    fun userPresetsToJsonString(presets: List<UserAnimationPreset>): String {
        return JSONArray().apply {
            presets.forEach { preset ->
                put(JSONObject().apply {
                    put("id", preset.id)
                    put("name", preset.name)
                    put("createdAt", preset.createdAt)
                    put("profile", animProfileToJson(preset.profile))
                })
            }
        }.toString()
    }

    fun userPresetsFromJsonString(jsonStr: String): List<UserAnimationPreset> {
        if (jsonStr.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(jsonStr)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val prof = animProfileFromJson(o.optJSONObject("profile"), "in_fade", "out_fade", "loop_none")
                    add(
                        UserAnimationPreset(
                            id = o.optString("id", "preset_$i"),
                            name = o.optString("name", "Custom Preset ${i + 1}"),
                            profile = prof,
                            createdAt = o.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun slotToJson(s: AnimationSlotConfig): JSONObject = JSONObject().apply {
        put("animationId", s.animationId)
        put("enabled", s.enabled)
        put("durationMs", s.durationMs)
        put("delayMs", s.delayMs)
        put("speed", s.speed.toDouble())
        put("intensity", s.intensity.toDouble())
        put("direction", s.direction.name)
        put("repeatCount", s.repeatCount)
        put("startOffsetMs", s.startOffsetMs)
        put("endOffsetMs", s.endOffsetMs)
        put("easing", s.easing.name)
        put("bx1", s.bezierX1.toDouble())
        put("by1", s.bezierY1.toDouble())
        put("bx2", s.bezierX2.toDouble())
        put("by2", s.bezierY2.toDouble())
    }

    private fun slotFromJson(o: JSONObject?, defaultId: String, defaultEnabled: Boolean, defaultDur: Long): AnimationSlotConfig {
        if (o == null) return AnimationSlotConfig(animationId = defaultId, enabled = defaultEnabled, durationMs = defaultDur)
        return AnimationSlotConfig(
            animationId = o.optString("animationId", defaultId),
            enabled = o.optBoolean("enabled", defaultEnabled),
            durationMs = o.optLong("durationMs", defaultDur).coerceIn(200L, 15_000L),
            delayMs = o.optLong("delayMs", 0L).coerceAtLeast(0L),
            speed = o.optDouble("speed", 1.0).toFloat(),
            intensity = o.optDouble("intensity", 1.0).toFloat(),
            direction = AnimationDirection.entries.firstOrNull { it.name == o.optString("direction") } ?: AnimationDirection.CENTER,
            repeatCount = o.optInt("repeatCount", 1),
            startOffsetMs = o.optLong("startOffsetMs", 0L),
            endOffsetMs = o.optLong("endOffsetMs", 0L),
            easing = AnimationEasingType.entries.firstOrNull { it.name == o.optString("easing") } ?: AnimationEasingType.EASE_OUT,
            bezierX1 = o.optDouble("bx1", 0.25).toFloat(),
            bezierY1 = o.optDouble("by1", 0.1).toFloat(),
            bezierX2 = o.optDouble("bx2", 0.25).toFloat(),
            bezierY2 = o.optDouble("by2", 1.0).toFloat()
        )
    }

    private fun animProfileToJson(p: ClipAnimationProfile): JSONObject = JSONObject().apply {
        put("inSlot", slotToJson(p.inSlot))
        put("loopSlot", slotToJson(p.loopSlot))
        put("outSlot", slotToJson(p.outSlot))
        put("blurRadius", p.blurRadius.toDouble())
        put("shadowStrength", p.shadowStrength.toDouble())
        put("skewX", p.skewX.toDouble())
        put("skewY", p.skewY.toDouble())
        put("perspective", p.perspective.toDouble())
        put("anchorX", p.anchorX.toDouble())
        put("anchorY", p.anchorY.toDouble())
    }

    private fun animProfileFromJson(
        o: JSONObject?,
        fallbackInId: String,
        fallbackOutId: String,
        fallbackLoopId: String
    ): ClipAnimationProfile {
        if (o == null) {
            return ClipAnimationProfile(
                inSlot = AnimationSlotConfig(fallbackInId, enabled = fallbackInId != "in_none"),
                loopSlot = AnimationSlotConfig(fallbackLoopId, enabled = fallbackLoopId != "loop_none", durationMs = 2000L, repeatCount = 0),
                outSlot = AnimationSlotConfig(fallbackOutId, enabled = fallbackOutId != "out_none")
            )
        }
        return ClipAnimationProfile(
            inSlot = slotFromJson(o.optJSONObject("inSlot"), fallbackInId, fallbackInId != "in_none", 700L),
            loopSlot = slotFromJson(o.optJSONObject("loopSlot"), fallbackLoopId, fallbackLoopId != "loop_none", 2000L),
            outSlot = slotFromJson(o.optJSONObject("outSlot"), fallbackOutId, fallbackOutId != "out_none", 700L),
            blurRadius = o.optDouble("blurRadius", 0.0).toFloat(),
            shadowStrength = o.optDouble("shadowStrength", 0.0).toFloat(),
            skewX = o.optDouble("skewX", 0.0).toFloat(),
            skewY = o.optDouble("skewY", 0.0).toFloat(),
            perspective = o.optDouble("perspective", 0.0).toFloat(),
            anchorX = o.optDouble("anchorX", 0.5).toFloat(),
            anchorY = o.optDouble("anchorY", 0.5).toFloat()
        )
    }

    private fun toJson(p: StudioProject): JSONObject = JSONObject().apply {
        put("id", p.id)
        put("name", p.name)
        put("aspectRatio", p.aspectRatio.name)
        put("previewResolution", p.previewResolution.name)
        put("thumbnailDrawable", p.thumbnailDrawable)
        put("totalDurationMs", p.totalDurationMs)
        put("lastEditedAt", p.lastEditedAt)
        put("masterVolume", p.masterVolume.toDouble())
        put("videoTrackPreviewOn", p.videoTrackPreviewOn)
        put("editModeOn", p.editModeOn)
        put("showSafeZoneGuides", p.showSafeZoneGuides)
        put("trackLayers", JSONArray().apply {
            p.trackLayers.forEach { tl ->
                put(JSONObject().apply {
                    put("trackType", tl.trackType.name)
                    put("order", tl.order)
                    put("isVisible", tl.isVisible)
                    put("isLocked", tl.isLocked)
                })
            }
        })
        put("clips", JSONArray().apply { p.clips.forEach { put(clipToJson(it)) } })
    }

    private fun fromJson(obj: JSONObject): StudioProject {
        val clipsArr = obj.optJSONArray("clips") ?: JSONArray()
        val clips = buildList {
            for (i in 0 until clipsArr.length()) {
                add(clipFromJson(clipsArr.getJSONObject(i)))
            }
        }
        val tlArr = obj.optJSONArray("trackLayers")
        val trackLayers = if (tlArr != null && tlArr.length() > 0) {
            buildList {
                for (i in 0 until tlArr.length()) {
                    val o = tlArr.getJSONObject(i)
                    val tt = TrackType.entries.firstOrNull { it.name == o.optString("trackType") } ?: continue
                    add(
                        TrackLayerState(
                            trackType = tt,
                            order = o.optInt("order", tt.defaultOrder),
                            isVisible = o.optBoolean("isVisible", true),
                            isLocked = o.optBoolean("isLocked", false)
                        )
                    )
                }
            }.let { parsed ->
                val missing = TrackType.entries.filter { tt -> parsed.none { it.trackType == tt } }
                    .map { TrackLayerState(it) }
                parsed + missing
            }
        } else {
            TrackType.entries.map { TrackLayerState(it) }
        }

        return StudioProject(
            id = obj.getString("id"),
            name = obj.getString("name"),
            aspectRatio = AspectRatioPreset.entries.firstOrNull { it.name == obj.optString("aspectRatio") } ?: AspectRatioPreset.LANDSCAPE_16_9,
            previewResolution = PreviewResolution.entries.firstOrNull { it.name == obj.optString("previewResolution") } ?: PreviewResolution.P1080,
            thumbnailDrawable = obj.optString("thumbnailDrawable", "img_tpl_documentary"),
            totalDurationMs = obj.optLong("totalDurationMs", 15_000L),
            lastEditedAt = obj.optLong("lastEditedAt", System.currentTimeMillis()),
            masterVolume = obj.optDouble("masterVolume", 1.0).toFloat(),
            videoTrackPreviewOn = obj.optBoolean("videoTrackPreviewOn", true),
            editModeOn = obj.optBoolean("editModeOn", true),
            showSafeZoneGuides = obj.optBoolean("showSafeZoneGuides", true),
            trackLayers = trackLayers,
            clips = clips
        )
    }

    private fun clipToJson(c: TimelineClip): JSONObject = JSONObject().apply {
        put("id", c.id)
        put("trackType", c.trackType.name)
        put("laneIndex", c.laneIndex)
        put("title", c.title)
        put("startMs", c.startMs)
        put("durationMs", c.durationMs)
        put("isLocked", c.isLocked)
        put("isHidden", c.isHidden)
        put("posX", c.posX.toDouble())
        put("posY", c.posY.toDouble())
        put("scale", c.scale.toDouble())
        put("rotation", c.rotation.toDouble())
        put("opacity", c.opacity.toDouble())
        put("inAnimationId", c.inAnimationId)
        put("outAnimationId", c.outAnimationId)
        put("loopAnimationId", c.loopAnimationId)
        put("animationProfile", animProfileToJson(c.animationProfile))
        put("transitionIn", c.transitionIn.name)
        put("transitionOut", c.transitionOut.name)
        put("transitionInDurationMs", c.transitionInDurationMs)
        put("transitionOutDurationMs", c.transitionOutDurationMs)
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
                    put("blurRadius", k.blurRadius.toDouble())
                    put("easing", k.easing.name)
                    put("customBezier", floatListToJson(k.customBezier))
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
            put("chromaKeyEnabled", c.colorGrade.chromaKeyEnabled)
            put("chromaKeyStrength", c.colorGrade.chromaKeyStrength.toDouble())
            put("masterCurve", floatListToJson(c.colorGrade.masterCurve))
            put("redCurve", floatListToJson(c.colorGrade.redCurve))
            put("greenCurve", floatListToJson(c.colorGrade.greenCurve))
            put("blueCurve", floatListToJson(c.colorGrade.blueCurve))
        })
        c.vfxConfig?.let { v ->
            put("vfxConfig", JSONObject().apply {
                put("effectType", v.effectType.name)
                put("intensity", v.intensity.toDouble())
                put("speed", v.speed.toDouble())
            })
        }
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
        put("borderConfig", JSONObject().apply {
            put("style", c.borderConfig.style.name)
            put("thicknessDp", c.borderConfig.thicknessDp.toDouble())
            put("colorHex", c.borderConfig.colorHex)
            put("secondaryColorHex", c.borderConfig.secondaryColorHex)
            put("glowRadius", c.borderConfig.glowRadius.toDouble())
            put("cornerRadiusDp", c.borderConfig.cornerRadiusDp.toDouble())
            put("insetPercent", c.borderConfig.insetPercent.toDouble())
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
                put("stylePresetId", tc.stylePresetId)
                put("useGradient", tc.useGradient)
                put("gradientStartHex", tc.gradientStartHex)
                put("gradientEndHex", tc.gradientEndHex)
                put("strokeColorHex", tc.strokeColorHex)
                put("strokeWidthPx", tc.strokeWidthPx.toDouble())
                put("shadowRadius", tc.shadowRadius.toDouble())
                put("shadowColorHex", tc.shadowColorHex)
                put("letterSpacingSp", tc.letterSpacingSp.toDouble())
                put("lineHeightMult", tc.lineHeightMult.toDouble())
                put("textAlign", tc.textAlign.name)
                put("pillColorHex", tc.pillColorHex)
                put("pillCornerDp", tc.pillCornerDp.toDouble())
                put("customFontPath", tc.customFontPath ?: "")
                put("customFontName", tc.customFontName ?: "")
            })
        }
        c.chartConfig?.let { cc ->
            put("chartConfig", JSONObject().apply {
                put("title", cc.title)
                put("unitSuffix", cc.unitSuffix)
                put("barCount", cc.barCount)
                put("chartStyle", cc.chartStyle.name)
                put("isHorizontal", cc.isHorizontal)
                put("sortDescending", cc.sortDescending)
                put("animateGrowth", cc.animateGrowth)
                put("paletteTheme", cc.paletteTheme.name)
                put("bars", JSONArray().apply {
                    cc.bars.forEach { b ->
                        put(JSONObject().apply {
                            put("id", b.id)
                            put("label", b.label)
                            put("value", b.value.toDouble())
                            put("colorHex", b.colorHex)
                        })
                    }
                })
            })
        }
        c.comparisonGrid?.let { cg ->
            put("comparisonGrid", JSONObject().apply {
                put("title", cg.title)
                put("slotCount", cg.slotCount)
                put("spacingDp", cg.spacingDp.toDouble())
                put("slots", JSONArray().apply {
                    cg.slots.forEach { sl ->
                        put(JSONObject().apply {
                            put("id", sl.id)
                            put("caption", sl.caption)
                            put("shape", sl.shape.name)
                            put("borderColorHex", sl.borderColorHex)
                            sl.mediaSource?.let { ms ->
                                put("mediaSource", JSONObject().apply {
                                    put("uriString", ms.uriString ?: "")
                                    put("drawableName", ms.drawableName ?: "")
                                    put("isVideo", ms.isVideo)
                                    put("label", ms.label)
                                })
                            }
                        })
                    }
                })
            })
        }
        c.investigativeConfig?.let { ic ->
            put("investigativeConfig", JSONObject().apply {
                put("toolType", ic.toolType.name)
                put("headline", ic.headline)
                put("bodyText", ic.bodyText)
                put("pointALabel", ic.pointALabel)
                put("pointBLabel", ic.pointBLabel)
                put("pointAX", ic.pointAX.toDouble())
                put("pointAY", ic.pointAY.toDouble())
                put("pointBX", ic.pointBX.toDouble())
                put("pointBY", ic.pointBY.toDouble())
                put("pointCLabel", ic.pointCLabel)
                put("pointCX", ic.pointCX.toDouble())
                put("pointCY", ic.pointCY.toDouble())
                put("useWaypointC", ic.useWaypointC)
                put("distanceLabel", ic.distanceLabel)
                put("mapStyle", ic.mapStyle.name)
                put("counterStart", ic.counterStart)
                put("counterEnd", ic.counterEnd)
                put("counterPrefix", ic.counterPrefix)
                put("counterSuffix", ic.counterSuffix)
                put("accentHex", ic.accentHex)
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
            put("isMuted", c.audioConfig.isMuted)
            put("isSolo", c.audioConfig.isSolo)
            put("pan", c.audioConfig.pan.toDouble())
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
                        blurRadius = k.optDouble("blurRadius", 0.0).toFloat(),
                        easing = KeyframeEasing.entries.firstOrNull { it.name == k.optString("easing") } ?: KeyframeEasing.EASE_IN_OUT,
                        customBezier = jsonToFloatList(k.optJSONArray("customBezier"), listOf(0.25f, 0.1f, 0.25f, 1.0f))
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
                chromaKeyEnabled = cg.optBoolean("chromaKeyEnabled", false),
                chromaKeyStrength = cg.optDouble("chromaKeyStrength", 0.45).toFloat(),
                masterCurve = jsonToFloatList(cg.optJSONArray("masterCurve"), listOf(0f, 0.25f, 0.5f, 0.75f, 1f)),
                redCurve = jsonToFloatList(cg.optJSONArray("redCurve"), listOf(0f, 0.25f, 0.5f, 0.75f, 1f)),
                greenCurve = jsonToFloatList(cg.optJSONArray("greenCurve"), listOf(0f, 0.25f, 0.5f, 0.75f, 1f)),
                blueCurve = jsonToFloatList(cg.optJSONArray("blueCurve"), listOf(0f, 0.25f, 0.5f, 0.75f, 1f))
            )
        } else LumetriColorGrade()

        val vf = o.optJSONObject("vfxConfig")
        val vfxConfig = if (vf != null) {
            VfxConfig(
                effectType = VfxEffectType.entries.firstOrNull { it.name == vf.optString("effectType") } ?: VfxEffectType.NONE,
                intensity = vf.optDouble("intensity", 0.65).toFloat(),
                speed = vf.optDouble("speed", 1.0).toFloat()
            )
        } else null

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

        val bc = o.optJSONObject("borderConfig")
        val borderConfig = if (bc != null) {
            BorderConfig(
                style = BorderShapeStyle.entries.firstOrNull { it.name == bc.optString("style") } ?: BorderShapeStyle.NONE,
                thicknessDp = bc.optDouble("thicknessDp", 4.0).toFloat(),
                colorHex = bc.optLong("colorHex", 0xFFFFB300),
                secondaryColorHex = bc.optLong("secondaryColorHex", 0xFF00E5FF),
                glowRadius = bc.optDouble("glowRadius", 8.0).toFloat(),
                cornerRadiusDp = bc.optDouble("cornerRadiusDp", 16.0).toFloat(),
                insetPercent = bc.optDouble("insetPercent", 0.03).toFloat()
            )
        } else BorderConfig()

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
                lowerThirdTemplate = LowerThirdTemplate.entries.firstOrNull { it.name == tc.optString("lowerThirdTemplate") } ?: LowerThirdTemplate.NONE,
                stylePresetId = tc.optString("stylePresetId", "style_clean"),
                useGradient = tc.optBoolean("useGradient", false),
                gradientStartHex = tc.optLong("gradientStartHex", 0xFF00E5FF),
                gradientEndHex = tc.optLong("gradientEndHex", 0xFFFFB300),
                strokeColorHex = tc.optLong("strokeColorHex", 0xFF000000),
                strokeWidthPx = tc.optDouble("strokeWidthPx", 0.0).toFloat(),
                shadowRadius = tc.optDouble("shadowRadius", 6.0).toFloat(),
                shadowColorHex = tc.optLong("shadowColorHex", 0xCC000000),
                letterSpacingSp = tc.optDouble("letterSpacingSp", 0.0).toFloat(),
                lineHeightMult = tc.optDouble("lineHeightMult", 1.22).toFloat(),
                textAlign = com.example.domain.model.StudioTextAlign.entries.firstOrNull { it.name == tc.optString("textAlign") } ?: com.example.domain.model.StudioTextAlign.CENTER,
                pillColorHex = tc.optLong("pillColorHex", 0xDC0D111C),
                pillCornerDp = tc.optDouble("pillCornerDp", 10.0).toFloat(),
                customFontPath = tc.optString("customFontPath").takeIf { it.isNotBlank() },
                customFontName = tc.optString("customFontName").takeIf { it.isNotBlank() }
            )
        } else null

        val cc = o.optJSONObject("chartConfig")
        val chartConfig = if (cc != null) {
            val bArr = cc.optJSONArray("bars") ?: JSONArray()
            val bars = buildList {
                for (i in 0 until bArr.length()) {
                    val b = bArr.getJSONObject(i)
                    add(
                        ChartBarItem(
                            id = b.optString("id", "bar_$i"),
                            label = b.optString("label", "Item"),
                            value = b.optDouble("value", 50.0).toFloat(),
                            colorHex = b.optLong("colorHex", 0xFF00E5FF)
                        )
                    )
                }
            }
            val isHoriz = cc.optBoolean("isHorizontal", true)
            val parsedStyle = ChartVisualStyle.entries.firstOrNull { it.name == cc.optString("chartStyle") }
                ?: if (isHoriz) ChartVisualStyle.HORIZONTAL_BAR else ChartVisualStyle.VERTICAL_BAR
            ComparisonChartConfig(
                title = cc.optString("title", "Comparison Chart"),
                unitSuffix = cc.optString("unitSuffix", "%"),
                barCount = cc.optInt("barCount", bars.size).coerceIn(0, 100),
                chartStyle = parsedStyle,
                isHorizontal = parsedStyle == ChartVisualStyle.HORIZONTAL_BAR,
                sortDescending = cc.optBoolean("sortDescending", true),
                animateGrowth = cc.optBoolean("animateGrowth", true),
                paletteTheme = ChartPaletteTheme.entries.firstOrNull { it.name == cc.optString("paletteTheme") } ?: ChartPaletteTheme.DEFAULT_MIX,
                bars = bars
            )
        } else null

        val compGridJson = o.optJSONObject("comparisonGrid")
        val comparisonGrid = if (compGridJson != null) {
            val sArr = compGridJson.optJSONArray("slots") ?: JSONArray()
            val slots = buildList {
                for (i in 0 until sArr.length()) {
                    val s = sArr.getJSONObject(i)
                    val msObj = s.optJSONObject("mediaSource")
                    val slotMs = if (msObj != null) {
                        MediaSource(
                            uriString = msObj.optString("uriString").takeIf { it.isNotBlank() },
                            drawableName = msObj.optString("drawableName").takeIf { it.isNotBlank() },
                            isVideo = msObj.optBoolean("isVideo", false),
                            label = msObj.optString("label", "Slot")
                        )
                    } else null
                    add(
                        ComparisonSlot(
                            id = s.optString("id", "slot_$i"),
                            caption = s.optString("caption", "Slot ${i + 1}"),
                            shape = GeometricShape.entries.firstOrNull { it.name == s.optString("shape") } ?: GeometricShape.ROUNDED_RECT,
                            borderColorHex = s.optLong("borderColorHex", 0xFF00E5FF),
                            mediaSource = slotMs
                        )
                    )
                }
            }
            ComparisonGridConfig(
                title = compGridJson.optString("title", "Comparison"),
                slotCount = compGridJson.optInt("slotCount", 3),
                spacingDp = compGridJson.optDouble("spacingDp", 8.0).toFloat(),
                slots = slots
            )
        } else null

        val ic = o.optJSONObject("investigativeConfig")
        val investigativeConfig = if (ic != null) {
            InvestigativeDocConfig(
                toolType = InvestigativeToolType.entries.firstOrNull { it.name == ic.optString("toolType") } ?: InvestigativeToolType.NONE,
                headline = ic.optString("headline", ""),
                bodyText = ic.optString("bodyText", ""),
                pointALabel = ic.optString("pointALabel", "ঢাকা"),
                pointBLabel = ic.optString("pointBLabel", "দিল্লি"),
                pointAX = ic.optDouble("pointAX", 0.22).toFloat(),
                pointAY = ic.optDouble("pointAY", 0.68).toFloat(),
                pointBX = ic.optDouble("pointBX", 0.78).toFloat(),
                pointBY = ic.optDouble("pointBY", 0.34).toFloat(),
                pointCLabel = ic.optString("pointCLabel", ""),
                pointCX = ic.optDouble("pointCX", 0.50).toFloat(),
                pointCY = ic.optDouble("pointCY", 0.24).toFloat(),
                useWaypointC = ic.optBoolean("useWaypointC", false),
                distanceLabel = ic.optString("distanceLabel", "২৬৫ কি.মি."),
                mapStyle = MapRadarStyle.entries.firstOrNull { it.name == ic.optString("mapStyle") } ?: MapRadarStyle.TACTICAL_DARK,
                counterStart = ic.optInt("counterStart", 1971),
                counterEnd = ic.optInt("counterEnd", 2026),
                counterPrefix = ic.optString("counterPrefix", ""),
                counterSuffix = ic.optString("counterSuffix", ""),
                accentHex = ic.optLong("accentHex", 0xFFFFB300)
            )
        } else null

        val ac = o.optJSONObject("audioConfig")
        val audioConfig = if (ac != null) {
            AudioConfig(
                volume = ac.optDouble("volume", 1.0).toFloat(),
                fadeInMs = ac.optLong("fadeInMs", 300L),
                fadeOutMs = ac.optLong("fadeOutMs", 400L),
                autoDuckingEnabled = ac.optBoolean("autoDuckingEnabled", true),
                duckingFloor = ac.optDouble("duckingFloor", 0.22).toFloat(),
                noiseReductionEnabled = ac.optBoolean("noiseReductionEnabled", false),
                audioPath = ac.optString("audioPath").takeIf { it.isNotBlank() },
                waveformSeed = ac.optInt("waveformSeed", 7),
                isMuted = ac.optBoolean("isMuted", false),
                isSolo = ac.optBoolean("isSolo", false),
                pan = ac.optDouble("pan", 0.0).toFloat()
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

        val inAnim = o.optString("inAnimationId", "in_fade")
        val outAnim = o.optString("outAnimationId", "out_fade")
        val loopAnim = o.optString("loopAnimationId", "loop_none")
        val animProfile = animProfileFromJson(o.optJSONObject("animationProfile"), inAnim, outAnim, loopAnim)

        return TimelineClip(
            id = o.getString("id"),
            trackType = TrackType.entries.firstOrNull { it.name == o.optString("trackType") } ?: TrackType.VIDEO,
            laneIndex = o.optInt("laneIndex", 0).coerceAtLeast(0),
            title = o.optString("title", "Clip"),
            startMs = o.optLong("startMs", 0L),
            durationMs = o.optLong("durationMs", 4000L),
            isLocked = o.optBoolean("isLocked", false),
            isHidden = o.optBoolean("isHidden", false),
            posX = o.optDouble("posX", 0.0).toFloat(),
            posY = o.optDouble("posY", 0.0).toFloat(),
            scale = o.optDouble("scale", 1.0).toFloat(),
            rotation = o.optDouble("rotation", 0.0).toFloat(),
            opacity = o.optDouble("opacity", 1.0).toFloat(),
            keyframes = keyframes,
            inAnimationId = inAnim,
            outAnimationId = outAnim,
            loopAnimationId = loopAnim,
            animationProfile = animProfile,
            transitionIn = TransitionType.entries.firstOrNull { it.name == o.optString("transitionIn") } ?: TransitionType.NONE,
            transitionOut = TransitionType.entries.firstOrNull { it.name == o.optString("transitionOut") } ?: TransitionType.NONE,
            transitionInDurationMs = o.optLong("transitionInDurationMs", 650L).coerceIn(150L, 3000L),
            transitionOutDurationMs = o.optLong("transitionOutDurationMs", 650L).coerceIn(150L, 3000L),
            lockedSfxId = o.optString("lockedSfxId").takeIf { it.isNotBlank() },
            colorGrade = colorGrade,
            vfxConfig = vfxConfig,
            speedRamp = speedRamp,
            maskConfig = maskConfig,
            borderConfig = borderConfig,
            blendMode = BlendModeOption.entries.firstOrNull { it.name == o.optString("blendMode") } ?: BlendModeOption.NORMAL,
            textConfig = textConfig,
            chartConfig = chartConfig,
            comparisonGrid = comparisonGrid,
            investigativeConfig = investigativeConfig,
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
