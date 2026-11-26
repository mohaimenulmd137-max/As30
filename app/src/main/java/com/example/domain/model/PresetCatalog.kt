package com.example.domain.model

object PresetCatalog {

    // 54 Motion Animations across IN (18), OUT (18), LOOP (18)
    val motionAnimations: List<MotionAnimationSpec> = listOf(
        // IN (18)
        MotionAnimationSpec("in_none", "None", "অ্যানিমেশন নেই", AnimationCategory.IN),
        MotionAnimationSpec("in_fade", "Cinema Fade", "স্মুথ ফেড ইন", AnimationCategory.IN),
        MotionAnimationSpec("in_slide_left", "Slide Left", "বাম থেকে স্লাইড", AnimationCategory.IN),
        MotionAnimationSpec("in_slide_right", "Slide Right", "ডান থেকে স্লাইড", AnimationCategory.IN),
        MotionAnimationSpec("in_slide_up", "Slide Up", "নিচ থেকে ওঠা", AnimationCategory.IN),
        MotionAnimationSpec("in_slide_down", "Slide Down", "উপর থেকে নামা", AnimationCategory.IN),
        MotionAnimationSpec("in_zoom_pop", "Zoom Pop", "জুম পপ", AnimationCategory.IN),
        MotionAnimationSpec("in_cinema_reveal", "Cinema Reveal", "সিনেমা রিভিল", AnimationCategory.IN),
        MotionAnimationSpec("in_drop_bounce", "Elastic Drop", "ইলাস্টিক বাউন্স", AnimationCategory.IN),
        MotionAnimationSpec("in_whip_pan", "Whip Pan", "হুইপ প্যান", AnimationCategory.IN),
        MotionAnimationSpec("in_spin_scale", "Spin Scale", "স্পিন ও স্কেল", AnimationCategory.IN),
        MotionAnimationSpec("in_flip_tilt", "3D Tilt Reveal", "থ্রিডি টিল্ট", AnimationCategory.IN),
        MotionAnimationSpec("in_glitch_snap", "Glitch Snap", "গ্লিচ স্ন্যাপ", AnimationCategory.IN),
        MotionAnimationSpec("in_doc_stamp", "Archive Stamp", "আর্কাইভ স্ট্যাম্প", AnimationCategory.IN),
        MotionAnimationSpec("in_flash_cut", "Flash Cut", "ফ্ল্যাশ কাট", AnimationCategory.IN),
        MotionAnimationSpec("in_iris_open", "Iris Open", "আইরিস ওপেন", AnimationCategory.IN),
        MotionAnimationSpec("in_drift_rise", "Slow Drift Rise", "স্লো রাইজ", AnimationCategory.IN),
        MotionAnimationSpec("in_swing_in", "Pendulum In", "দোলনা এন্ট্রি", AnimationCategory.IN),

        // OUT (18)
        MotionAnimationSpec("out_none", "None", "আউট নেই", AnimationCategory.OUT),
        MotionAnimationSpec("out_fade", "Fade Out", "ফেড আউট", AnimationCategory.OUT),
        MotionAnimationSpec("out_slide_left", "Exit Left", "বামে প্রস্থান", AnimationCategory.OUT),
        MotionAnimationSpec("out_slide_right", "Exit Right", "ডানে প্রস্থান", AnimationCategory.OUT),
        MotionAnimationSpec("out_slide_up", "Fly Up", "উপরে উড়ে যাওয়া", AnimationCategory.OUT),
        MotionAnimationSpec("out_slide_down", "Drop Down", "নিচে নামা", AnimationCategory.OUT),
        MotionAnimationSpec("out_zoom_shrink", "Zoom Shrink", "জুম শ্রিঙ্ক", AnimationCategory.OUT),
        MotionAnimationSpec("out_zoom_explode", "Zoom Burst", "জুম বার্স্ট", AnimationCategory.OUT),
        MotionAnimationSpec("out_whip_away", "Whip Away", "হুইপ আউট", AnimationCategory.OUT),
        MotionAnimationSpec("out_spin_out", "Spin Out", "ঘূর্ণন প্রস্থান", AnimationCategory.OUT),
        MotionAnimationSpec("out_glitch_out", "Glitch Out", "গ্লিচ আউট", AnimationCategory.OUT),
        MotionAnimationSpec("out_flash_fade", "Flash Out", "ফ্ল্যাশ ফেড", AnimationCategory.OUT),
        MotionAnimationSpec("out_dip_black", "Cinema Dip", "সিনেমা ডিপ", AnimationCategory.OUT),
        MotionAnimationSpec("out_fold_away", "Paper Fold", "ফোল্ড আউট", AnimationCategory.OUT),
        MotionAnimationSpec("out_drift_down", "Drift Away", "ড্রিফট আউট", AnimationCategory.OUT),
        MotionAnimationSpec("out_snap_shrink", "Snap Out", "স্ন্যাপ আউট", AnimationCategory.OUT),
        MotionAnimationSpec("out_tilt_exit", "Tilt Exit", "টিল্ট আউট", AnimationCategory.OUT),
        MotionAnimationSpec("out_iris_close", "Iris Close", "আইরিস ক্লোজ", AnimationCategory.OUT),

        // LOOP (18)
        MotionAnimationSpec("loop_none", "Static", "লুপ নেই", AnimationCategory.LOOP),
        MotionAnimationSpec("loop_float", "Gentle Float", "হালকা ভাসা", AnimationCategory.LOOP),
        MotionAnimationSpec("loop_pulse", "Pulse Beat", "পালস বিট", AnimationCategory.LOOP),
        MotionAnimationSpec("loop_breathe", "Slow Breathe", "স্লো ব্রিদ", AnimationCategory.LOOP),
        MotionAnimationSpec("loop_ken_burns", "Ken Burns Pan", "কেন বার্নস প্যান", AnimationCategory.LOOP),
        MotionAnimationSpec("loop_jitter", "Hand-Held Shake", "হ্যান্ডহেল্ড শেক", AnimationCategory.LOOP),
        MotionAnimationSpec("loop_flicker", "Projector Flicker", "প্রজেক্টর ফ্লিকার", AnimationCategory.LOOP),
        MotionAnimationSpec("loop_pendulum", "Pendulum Sway", "পেন্ডুলাম দুলুনি", AnimationCategory.LOOP),
        MotionAnimationSpec("loop_orbit", "Orbit Drift", "অরবিট ড্রিফট", AnimationCategory.LOOP),
        MotionAnimationSpec("loop_heartbeat", "Heartbeat", "হার্টবিট", AnimationCategory.LOOP),
        MotionAnimationSpec("loop_glitch_pulse", "Glitch Pulse", "গ্লিচ পালস", AnimationCategory.LOOP),
        MotionAnimationSpec("loop_wave", "Wave Sway", "ওয়েভ সুইং", AnimationCategory.LOOP),
        MotionAnimationSpec("loop_spin_slow", "Slow Orbit Spin", "ধীর ঘূর্ণন", AnimationCategory.LOOP),
        MotionAnimationSpec("loop_zoom_creep", "Documentary Zoom", "ডকুমেন্টারি জুম", AnimationCategory.LOOP),
        MotionAnimationSpec("loop_strobe", "Micro Strobe", "মাইক্রো স্ট্রোব", AnimationCategory.LOOP),
        MotionAnimationSpec("loop_bounce_idle", "Soft Bounce", "সফট বাউন্স", AnimationCategory.LOOP),
        MotionAnimationSpec("loop_vibrate", "Tension Shake", "টেনশন শেক", AnimationCategory.LOOP),
        MotionAnimationSpec("loop_tilt_rock", "Tilt Rock", "টিল্ট রক", AnimationCategory.LOOP)
    )

    // 52 Synthesized SFX Presets
    val sfxPresets: List<SfxPreset> = buildList {
        // Whooshes (10)
        val whooshSpecs = listOf(
            "Fast Air Whoosh" to 320, "Deep Cinema Whoosh" to 520, "Whip Pan Swoosh" to 260,
            "Reverse Riser Swish" to 600, "Wind Pass By" to 450, "Silk Slide Whoosh" to 300,
            "Laser Whip" to 240, "Metallic Swoosh" to 380, "Subtle Page Turn" to 290, "Jet Flyby" to 650
        )
        whooshSpecs.forEachIndexed { i, (name, dur) ->
            add(SfxPreset("sfx_whoosh_$i", name, name, SfxCategory.WHOOSH, dur, 180f + i * 35f, 950f + i * 80f, SynthWaveform.SWEEP_UP, 0.72f))
        }
        // Pop & UI (10)
        val popSpecs = listOf(
            "Clean Bubble Pop" to 110, "Glass Click" to 85, "Modern UI Tap" to 70,
            "Soft Notification" to 190, "Wooden Pluck" to 130, "Camera Shutter" to 140,
            "Switch Snap" to 90, "Sparkle Bell" to 320, "Blip High" to 100, "Chime Confirm" to 280
        )
        popSpecs.forEachIndexed { i, (name, dur) ->
            add(SfxPreset("sfx_pop_$i", name, name, SfxCategory.POP, dur, 520f + i * 90f, 1280f + i * 60f, SynthWaveform.SINE, 0.08f))
        }
        // Impact (9)
        val impactSpecs = listOf(
            "Documentary Boom" to 680, "Sub Bass Drop" to 750, "Trailer Hit" to 540,
            "Anvil Strike" to 460, "Door Slam Thud" to 350, "Heartbeat Thump" to 260,
            "Drum Accent" to 310, "Deep Earth Rumble" to 820, "Punch Hit" to 210
        )
        impactSpecs.forEachIndexed { i, (name, dur) ->
            add(SfxPreset("sfx_impact_$i", name, name, SfxCategory.IMPACT, dur, 160f - i * 8f, 38f, SynthWaveform.SWEEP_DOWN, 0.35f))
        }
        // Typing (8)
        val typingSpecs = listOf(
            "Mechanical Key Click" to 75, "Typewriter Carriage" to 180, "Vintage Keystroke" to 90,
            "Subtitle Tick" to 65, "Morse Beep" to 120, "Digital Counter" to 80,
            "Clock Tick" to 105, "Stopwatch Latch" to 115
        )
        typingSpecs.forEachIndexed { i, (name, dur) ->
            add(SfxPreset("sfx_type_$i", name, name, SfxCategory.TYPING, dur, 1100f + i * 120f, 880f, SynthWaveform.SQUARE, 0.42f))
        }
        // Cinema (8)
        val cinemaSpecs = listOf(
            "Tension Riser" to 950, "Warm Pad Swell" to 850, "Broadcast Sting" to 500,
            "Film Reel Start" to 420, "Horizon Chime" to 720, "Time Freeze" to 640,
            "Mystery Drone" to 900, "Chapter Gong" to 800
        )
        cinemaSpecs.forEachIndexed { i, (name, dur) ->
            add(SfxPreset("sfx_cinema_$i", name, name, SfxCategory.CINEMA, dur, 220f + i * 30f, 540f + i * 45f, SynthWaveform.TRIANGLE, 0.18f))
        }
        // Glitch (7)
        val glitchSpecs = listOf(
            "VHS Static Burst" to 220, "Digital Tear" to 180, "Cyber Zap" to 250,
            "Data Corrupt" to 290, "Signal Drop" to 340, "Radio Tuning" to 400, "Pixel Snap" to 150
        )
        glitchSpecs.forEachIndexed { i, (name, dur) ->
            add(SfxPreset("sfx_glitch_$i", name, name, SfxCategory.GLITCH, dur, 920f + i * 140f, 240f, SynthWaveform.NOISE_BURST, 0.85f))
        }
    }

    val speedRampPresets: List<SpeedRampProfile> = listOf(
        SpeedRampProfile(
            "Normal (1.0x)",
            listOf(SpeedPoint(0f, 1f), SpeedPoint(0.5f, 1f), SpeedPoint(1f, 1f))
        ),
        SpeedRampProfile(
            "Hero Slow-Mo Dip",
            listOf(SpeedPoint(0f, 1.8f), SpeedPoint(0.35f, 0.3f), SpeedPoint(0.65f, 0.3f), SpeedPoint(1f, 2.0f))
        ),
        SpeedRampProfile(
            "Bullet Time",
            listOf(SpeedPoint(0f, 3.0f), SpeedPoint(0.25f, 0.25f), SpeedPoint(0.75f, 0.25f), SpeedPoint(1f, 3.0f))
        ),
        SpeedRampProfile(
            "Montage Surge",
            listOf(SpeedPoint(0f, 0.6f), SpeedPoint(0.5f, 2.4f), SpeedPoint(1f, 0.8f))
        ),
        SpeedRampProfile(
            "Documentary Glide",
            listOf(SpeedPoint(0f, 1.0f), SpeedPoint(0.5f, 0.65f), SpeedPoint(1f, 1.0f))
        )
    )

    data class LumetriPreset(val name: String, val nameBn: String, val grade: LumetriColorGrade)

    val lumetriPresets: List<LumetriPreset> = listOf(
        LumetriPreset("Original", "অরিজিনাল", LumetriColorGrade()),
        LumetriPreset(
            "Teal & Amber Doc", "ডকুমেন্টারি গোল্ড",
            LumetriColorGrade(
                contrast = 1.18f, saturation = 1.12f, temperature = 0.22f, vignette = 0.28f,
                redCurve = listOf(0f, 0.22f, 0.54f, 0.80f, 1f),
                blueCurve = listOf(0.05f, 0.28f, 0.48f, 0.70f, 0.94f)
            )
        ),
        LumetriPreset(
            "Noir Archive", "সাদাকালো আর্কাইভ",
            LumetriColorGrade(
                brightness = -0.05f, contrast = 1.38f, saturation = 0.08f, vignette = 0.45f,
                masterCurve = listOf(0.02f, 0.18f, 0.50f, 0.84f, 0.98f)
            )
        ),
        LumetriPreset(
            "Cyber Neon", "সাইবার নিয়ন",
            LumetriColorGrade(
                contrast = 1.24f, saturation = 1.45f, temperature = -0.28f, glow = 0.35f,
                blueCurve = listOf(0.04f, 0.32f, 0.60f, 0.85f, 1f)
            )
        ),
        LumetriPreset(
            "Warm Vintage 35mm", "ভিন্টেজ ৩৫মিমি",
            LumetriColorGrade(
                brightness = 0.04f, contrast = 1.08f, saturation = 0.92f, temperature = 0.35f, vignette = 0.32f,
                masterCurve = listOf(0.06f, 0.27f, 0.52f, 0.76f, 0.95f)
            )
        )
    )

    data class StudioTemplate(
        val id: String,
        val titleBn: String,
        val titleEn: String,
        val subtitleBn: String,
        val aspectRatio: AspectRatioPreset,
        val thumbnailDrawable: String,
        val buildProject: () -> StudioProject
    )

    val studioTemplates: List<StudioTemplate> = listOf(
        StudioTemplate(
            id = "tpl_history_doc",
            titleBn = "ইতিহাস ও ডকুমেন্টারি",
            titleEn = "History Documentary",
            subtitleBn = "সাল কার্ড + লোয়ার থার্ড + লুমেট্রি গ্রেড",
            aspectRatio = AspectRatioPreset.LANDSCAPE_16_9,
            thumbnailDrawable = "img_tpl_documentary",
            buildProject = {
                StudioProject(
                    name = "বাংলার নদী ও ঐতিহ্য",
                    aspectRatio = AspectRatioPreset.LANDSCAPE_16_9,
                    previewResolution = PreviewResolution.P1080,
                    thumbnailDrawable = "img_tpl_documentary",
                    totalDurationMs = 14_000L,
                    clips = listOf(
                        TimelineClip(
                            trackType = TrackType.VIDEO,
                            title = "River Golden Hour",
                            startMs = 0L,
                            durationMs = 14_000L,
                            loopAnimationId = "loop_zoom_creep",
                            speedRamp = speedRampPresets[4],
                            mediaSource = MediaSource(drawableName = "img_tpl_documentary", label = "Golden River Footage")
                        ),
                        TimelineClip(
                            trackType = TrackType.ADJUSTMENT,
                            title = "Teal & Amber Grade",
                            startMs = 0L,
                            durationMs = 14_000L,
                            colorGrade = lumetriPresets[1].grade
                        ),
                        TimelineClip(
                            trackType = TrackType.GRAPHICS,
                            title = "ঐতিহাসিক সাল কার্ড",
                            startMs = 600L,
                            durationMs = 5_500L,
                            posX = -0.18f,
                            posY = 0.26f,
                            inAnimationId = "in_slide_left",
                            outAnimationId = "out_fade",
                            lockedSfxId = "sfx_whoosh_1",
                            keyframes = listOf(
                                Keyframe(timeOffsetMs = 0L, posX = -0.32f, posY = 0.26f, opacity = 0.2f),
                                Keyframe(timeOffsetMs = 1000L, posX = -0.14f, posY = 0.26f, opacity = 1f),
                                Keyframe(timeOffsetMs = 4500L, posX = -0.14f, posY = 0.26f, opacity = 1f)
                            ),
                            textConfig = TextLayerConfig(
                                text = "১৭৫৭ · পলাশী প্রান্তর",
                                subtitle = "ঐতিহাসিক প্রামাণ্যচিত্র · আর্কাইভ",
                                fontId = StudioFontOption.NOTO_SERIF_BENGALI.id,
                                fontSizeSp = 22f,
                                accentColorHex = 0xFFFFB300,
                                lowerThirdTemplate = LowerThirdTemplate.DOCUMENTARY_DATE_CARD
                            )
                        ),
                        TimelineClip(
                            trackType = TrackType.TEXT,
                            title = "কারাওকে সাবটাইটেল",
                            startMs = 3_500L,
                            durationMs = 8_500L,
                            posX = 0f,
                            posY = -0.25f,
                            inAnimationId = "in_fade",
                            outAnimationId = "out_fade",
                            textConfig = TextLayerConfig(
                                text = "শতাব্দীর স্রোতে ভেসে চলা বাংলার অজানা ইতিহাস ও লোকগাথা",
                                subtitle = "",
                                fontId = StudioFontOption.HIND_SILIGURI.id,
                                fontSizeSp = 20f,
                                karaokeHighlight = true,
                                backgroundPill = true,
                                accentColorHex = 0xFF00E5FF
                            )
                        ),
                        TimelineClip(
                            trackType = TrackType.VOICEOVER,
                            title = "নদীমাতৃক ভয়েসওভার",
                            startMs = 2_000L,
                            durationMs = 6_500L,
                            lockedSfxId = "sfx_cinema_0",
                            audioConfig = AudioConfig(volume = 1.2f, noiseReductionEnabled = true, waveformSeed = 11)
                        ),
                        TimelineClip(
                            trackType = TrackType.MUSIC,
                            title = "Ambient Documentary Bed",
                            startMs = 0L,
                            durationMs = 14_000L,
                            audioConfig = AudioConfig(volume = 0.78f, autoDuckingEnabled = true, duckingFloor = 0.22f, waveformSeed = 23)
                        )
                    )
                )
            }
        ),
        StudioTemplate(
            id = "tpl_motion_reel",
            titleBn = "মোশন গ্রাফিক্স রিল",
            titleEn = "Kinetic Motion Reel",
            subtitleBn = "কি-ফ্রেম অ্যানিমেশন + স্পিড র‍্যাম্প + SFX",
            aspectRatio = AspectRatioPreset.PORTRAIT_9_16,
            thumbnailDrawable = "img_tpl_motion",
            buildProject = {
                StudioProject(
                    name = "কাইনেটিক মোশন প্রো",
                    aspectRatio = AspectRatioPreset.PORTRAIT_9_16,
                    previewResolution = PreviewResolution.P1080,
                    thumbnailDrawable = "img_tpl_motion",
                    totalDurationMs = 12_000L,
                    clips = listOf(
                        TimelineClip(
                            trackType = TrackType.VIDEO,
                            title = "Kinetic Glass Layers",
                            startMs = 0L,
                            durationMs = 12_000L,
                            loopAnimationId = "loop_ken_burns",
                            speedRamp = speedRampPresets[1],
                            mediaSource = MediaSource(drawableName = "img_tpl_motion", label = "Kinetic Glass")
                        ),
                        TimelineClip(
                            trackType = TrackType.ADJUSTMENT,
                            title = "Cyber Neon Glow",
                            startMs = 0L,
                            durationMs = 12_000L,
                            colorGrade = lumetriPresets[3].grade
                        ),
                        TimelineClip(
                            trackType = TrackType.TEXT,
                            title = "কাইনেটিক শিরোনাম",
                            startMs = 400L,
                            durationMs = 6_000L,
                            posX = 0f,
                            posY = -0.05f,
                            inAnimationId = "in_zoom_pop",
                            outAnimationId = "out_whip_away",
                            loopAnimationId = "loop_float",
                            lockedSfxId = "sfx_impact_0",
                            keyframes = listOf(
                                Keyframe(timeOffsetMs = 0L, posX = 0f, posY = 0.15f, scale = 0.7f, opacity = 0.2f),
                                Keyframe(timeOffsetMs = 1200L, posX = 0f, posY = -0.06f, scale = 1.15f, opacity = 1f),
                                Keyframe(timeOffsetMs = 4500L, posX = 0f, posY = -0.06f, scale = 1.0f, opacity = 1f)
                            ),
                            textConfig = TextLayerConfig(
                                text = "চিত্রফ্লো মোশন স্টুডিও",
                                subtitle = "4K 60FPS · KEYFRAME ENGINE",
                                fontId = StudioFontOption.HIND_SILIGURI.id,
                                fontSizeSp = 28f,
                                karaokeHighlight = true,
                                accentColorHex = 0xFF00E5FF,
                                lowerThirdTemplate = LowerThirdTemplate.CINEMA_CHAPTER
                            )
                        ),
                        TimelineClip(
                            trackType = TrackType.MUSIC,
                            title = "Pulse Synth Beat",
                            startMs = 0L,
                            durationMs = 12_000L,
                            audioConfig = AudioConfig(volume = 0.85f, autoDuckingEnabled = true, waveformSeed = 39)
                        )
                    )
                )
            }
        ),
        StudioTemplate(
            id = "tpl_street_cinema",
            titleBn = "নাইট স্ট্রিট ন্যারেটিভ",
            titleEn = "Street Cinema Story",
            subtitleBn = "রেডিয়াল মাস্ক + RGB কার্ভ + অটো-ডাকিং",
            aspectRatio = AspectRatioPreset.PORTRAIT_9_16,
            thumbnailDrawable = "img_tpl_street_cinema",
            buildProject = {
                StudioProject(
                    name = "রাতের শহরের গল্প",
                    aspectRatio = AspectRatioPreset.PORTRAIT_9_16,
                    previewResolution = PreviewResolution.P1080,
                    thumbnailDrawable = "img_tpl_street_cinema",
                    totalDurationMs = 15_000L,
                    clips = listOf(
                        TimelineClip(
                            trackType = TrackType.VIDEO,
                            title = "Rainy Street Walk",
                            startMs = 0L,
                            durationMs = 15_000L,
                            maskConfig = MaskConfig(shape = MaskShape.CINEMA_BARS, feather = 0.1f, scale = 0.82f),
                            mediaSource = MediaSource(drawableName = "img_tpl_street_cinema", label = "Rain Street Cinema")
                        ),
                        TimelineClip(
                            trackType = TrackType.ADJUSTMENT,
                            title = "35mm Vintage Curve",
                            startMs = 0L,
                            durationMs = 15_000L,
                            colorGrade = lumetriPresets[4].grade
                        ),
                        TimelineClip(
                            trackType = TrackType.GRAPHICS,
                            title = "লোকেশন লোয়ার থার্ড",
                            startMs = 1_200L,
                            durationMs = 6_000L,
                            posX = -0.1f,
                            posY = 0.28f,
                            inAnimationId = "in_cinema_reveal",
                            outAnimationId = "out_slide_left",
                            lockedSfxId = "sfx_pop_5",
                            textConfig = TextLayerConfig(
                                text = "ঢাকা · রাত ১১:৪৫",
                                subtitle = "বৃষ্টির শহর · স্ট্রিট ডকুমেন্টারি",
                                fontId = StudioFontOption.NOTO_SANS_BENGALI.id,
                                fontSizeSp = 21f,
                                accentColorHex = 0xFFFF4D6D,
                                lowerThirdTemplate = LowerThirdTemplate.NEWS_LOWER_THIRD
                            )
                        ),
                        TimelineClip(
                            trackType = TrackType.VOICEOVER,
                            title = "কথক ভয়েসওভার",
                            startMs = 2_500L,
                            durationMs = 8_000L,
                            audioConfig = AudioConfig(volume = 1.15f, noiseReductionEnabled = true, waveformSeed = 51)
                        ),
                        TimelineClip(
                            trackType = TrackType.MUSIC,
                            title = "Midnight Lo-Fi Pad",
                            startMs = 0L,
                            durationMs = 15_000L,
                            audioConfig = AudioConfig(volume = 0.72f, autoDuckingEnabled = true, duckingFloor = 0.2f, waveformSeed = 64)
                        )
                    )
                )
            }
        )
    )
}
