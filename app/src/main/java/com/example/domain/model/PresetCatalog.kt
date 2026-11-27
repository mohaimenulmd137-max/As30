package com.example.domain.model

object PresetCatalog {

    val motionAnimations: List<MotionAnimationSpec> = listOf(
        // IN ANIMATIONS — General & Text & Image & Video (with AnimationMediaScope)
        MotionAnimationSpec("in_none", "None", "অ্যানিমেশন নেই", AnimationCategory.IN, AnimationMediaScope.ALL),
        MotionAnimationSpec("in_fade", "Fade In", "ফেড ইন", AnimationCategory.IN, AnimationMediaScope.ALL),
        MotionAnimationSpec("in_slide_left", "Slide Left", "বাম থেকে স্লাইড", AnimationCategory.IN, AnimationMediaScope.ALL),
        MotionAnimationSpec("in_slide_right", "Slide Right", "ডান থেকে স্লাইড", AnimationCategory.IN, AnimationMediaScope.ALL),
        MotionAnimationSpec("in_slide_up", "Slide Up", "নিচ থেকে ওঠা", AnimationCategory.IN, AnimationMediaScope.ALL),
        MotionAnimationSpec("in_slide_down", "Slide Down", "উপর থেকে নামা", AnimationCategory.IN, AnimationMediaScope.ALL),
        MotionAnimationSpec("in_zoom_pop", "Zoom In", "জুম পপ ইন", AnimationCategory.IN, AnimationMediaScope.ALL),
        MotionAnimationSpec("in_cinema_reveal", "Cinematic Reveal", "সিনেমা রিভিল", AnimationCategory.IN, AnimationMediaScope.ALL),
        MotionAnimationSpec("in_drop_bounce", "Elastic Drop", "ইলাস্টিক বাউন্স", AnimationCategory.IN, AnimationMediaScope.ALL),
        MotionAnimationSpec("in_whip_pan", "Whip Pan", "হুইপ প্যান", AnimationCategory.IN, AnimationMediaScope.ALL),
        MotionAnimationSpec("in_spin_scale", "Rotation Reveal", "রোটেশন রিভিল", AnimationCategory.IN, AnimationMediaScope.IMAGE),
        MotionAnimationSpec("in_flip_tilt", "3D Perspective", "থ্রিডি পারস্পেক্টিভ", AnimationCategory.IN, AnimationMediaScope.IMAGE),
        MotionAnimationSpec("in_glitch_snap", "Glitch Reveal", "গ্লিচ রিভিল", AnimationCategory.IN, AnimationMediaScope.TEXT),
        MotionAnimationSpec("in_doc_stamp", "Archive Stamp", "আর্কাইভ স্ট্যাম্প", AnimationCategory.IN, AnimationMediaScope.ALL),
        MotionAnimationSpec("in_flash_cut", "Flash Cut", "ফ্ল্যাশ কাট", AnimationCategory.IN, AnimationMediaScope.VIDEO),
        MotionAnimationSpec("in_iris_open", "Iris Open", "আইরিস ওপেন", AnimationCategory.IN, AnimationMediaScope.ALL),
        MotionAnimationSpec("in_drift_rise", "Slow Drift Rise", "স্লো রাইজ", AnimationCategory.IN, AnimationMediaScope.ALL),
        MotionAnimationSpec("in_swing_in", "Pendulum In", "দোলনা এন্ট্রি", AnimationCategory.IN, AnimationMediaScope.ALL),

        // TEXT ANIMATION LIBRARY (16 Distinct Real Text Animations)
        MotionAnimationSpec("text_typewriter", "Typewriter", "টাইপরাইটার", AnimationCategory.IN, AnimationMediaScope.TEXT),
        MotionAnimationSpec("text_cinematic_spacing", "Cinematic Spacing", "সিনেমা স্পেসিং", AnimationCategory.IN, AnimationMediaScope.TEXT),
        MotionAnimationSpec("text_word_pop", "Word Pop", "ওয়ার্ড পপ", AnimationCategory.IN, AnimationMediaScope.TEXT),
        MotionAnimationSpec("text_split_char", "Split Character", "স্প্লিট ক্যারেক্টার", AnimationCategory.IN, AnimationMediaScope.TEXT),
        MotionAnimationSpec("text_word_cascade", "Word Cascade", "ওয়ার্ড ক্যাসকেড", AnimationCategory.IN, AnimationMediaScope.TEXT),
        MotionAnimationSpec("text_underline_stroke", "Underline Stroke", "আন্ডারলাইন স্ট্রোক", AnimationCategory.IN, AnimationMediaScope.TEXT),
        MotionAnimationSpec("text_3d_flip", "3D Flip", "থ্রিডি ফ্লিপ", AnimationCategory.IN, AnimationMediaScope.TEXT),
        MotionAnimationSpec("text_ink_stamp", "Ink Stamp", "ইঙ্ক স্ট্যাম্প", AnimationCategory.IN, AnimationMediaScope.TEXT),
        MotionAnimationSpec("text_whip_skew", "Whip Skew", "হুইপ স্কিউ", AnimationCategory.IN, AnimationMediaScope.TEXT),
        MotionAnimationSpec("text_blur_reveal", "Blur Reveal", "ব্লার রিভিল", AnimationCategory.IN, AnimationMediaScope.TEXT),
        MotionAnimationSpec("text_char_reveal", "Character Reveal", "ক্যারেক্টার রিভিল", AnimationCategory.IN, AnimationMediaScope.TEXT),
        MotionAnimationSpec("text_line_reveal", "Line-by-Line Reveal", "লাইন বাই লাইন", AnimationCategory.IN, AnimationMediaScope.TEXT),

        // IMAGE ANIMATION LIBRARY (14 Distinct Real Image Animations)
        MotionAnimationSpec("img_fade", "Fade", "ইমেজ ফেড", AnimationCategory.IN, AnimationMediaScope.IMAGE),
        MotionAnimationSpec("img_zoom", "Zoom", "ইমেজ জুম", AnimationCategory.IN, AnimationMediaScope.IMAGE),
        MotionAnimationSpec("img_slow_zoom", "Slow Zoom", "স্লো জুম", AnimationCategory.LOOP, AnimationMediaScope.IMAGE),
        MotionAnimationSpec("img_zoom_out", "Zoom Out", "জুম আউট", AnimationCategory.IN, AnimationMediaScope.IMAGE),
        MotionAnimationSpec("img_pan_left", "Pan Left", "প্যান বামে", AnimationCategory.LOOP, AnimationMediaScope.IMAGE),
        MotionAnimationSpec("img_pan_right", "Pan Right", "প্যান ডানে", AnimationCategory.LOOP, AnimationMediaScope.IMAGE),
        MotionAnimationSpec("img_pan_up", "Pan Up", "প্যান উপরে", AnimationCategory.LOOP, AnimationMediaScope.IMAGE),
        MotionAnimationSpec("img_pan_down", "Pan Down", "প্যান নিচে", AnimationCategory.LOOP, AnimationMediaScope.IMAGE),
        MotionAnimationSpec("img_blur_reveal", "Blur Reveal", "ইমেজ ব্লার রিভিল", AnimationCategory.IN, AnimationMediaScope.IMAGE),
        MotionAnimationSpec("img_parallax", "Parallax", "প্যারালাক্স ইমেজ", AnimationCategory.LOOP, AnimationMediaScope.IMAGE),

        // VIDEO ANIMATION LIBRARY (9 Distinct Real Video Animations)
        MotionAnimationSpec("vid_cinematic_zoom", "Cinematic Zoom", "সিনেমা জুম", AnimationCategory.IN, AnimationMediaScope.VIDEO),
        MotionAnimationSpec("vid_smooth_zoom", "Smooth Zoom", "স্মুথ জুম", AnimationCategory.LOOP, AnimationMediaScope.VIDEO),
        MotionAnimationSpec("vid_camera_shake", "Camera Shake", "ক্যামেরা শেক", AnimationCategory.LOOP, AnimationMediaScope.VIDEO),
        MotionAnimationSpec("vid_motion_blur", "Motion Blur", "মোশন ব্লার", AnimationCategory.IN, AnimationMediaScope.VIDEO),
        MotionAnimationSpec("vid_slow_pan", "Slow Pan", "স্লো প্যান", AnimationCategory.LOOP, AnimationMediaScope.VIDEO),
        MotionAnimationSpec("vid_dynamic_crop", "Dynamic Crop", "ডাইনামিক ক্রপ", AnimationCategory.LOOP, AnimationMediaScope.VIDEO),
        MotionAnimationSpec("vid_glitch", "Glitch", "ভিডিও গ্লিচ", AnimationCategory.LOOP, AnimationMediaScope.VIDEO),
        MotionAnimationSpec("vid_flash", "Flash", "সিনেমা ফ্ল্যাশ", AnimationCategory.IN, AnimationMediaScope.VIDEO),
        MotionAnimationSpec("vid_speed_ramp", "Speed Ramp", "স্পিড র‍্যাম্প ইফেক্ট", AnimationCategory.LOOP, AnimationMediaScope.VIDEO),

        // OUT ANIMATIONS (18)
        MotionAnimationSpec("out_none", "None", "আউট নেই", AnimationCategory.OUT, AnimationMediaScope.ALL),
        MotionAnimationSpec("out_fade", "Fade Out", "ফেড আউট", AnimationCategory.OUT, AnimationMediaScope.ALL),
        MotionAnimationSpec("out_slide_left", "Exit Left", "বামে প্রস্থান", AnimationCategory.OUT, AnimationMediaScope.ALL),
        MotionAnimationSpec("out_slide_right", "Exit Right", "ডানে প্রস্থান", AnimationCategory.OUT, AnimationMediaScope.ALL),
        MotionAnimationSpec("out_slide_up", "Fly Up", "উপরে উড়ে যাওয়া", AnimationCategory.OUT, AnimationMediaScope.ALL),
        MotionAnimationSpec("out_slide_down", "Drop Down", "নিচে নামা", AnimationCategory.OUT, AnimationMediaScope.ALL),
        MotionAnimationSpec("out_zoom_shrink", "Zoom Shrink", "জুম শ্রিঙ্ক", AnimationCategory.OUT, AnimationMediaScope.ALL),
        MotionAnimationSpec("out_zoom_explode", "Zoom Burst", "জুম বার্স্ট", AnimationCategory.OUT, AnimationMediaScope.ALL),
        MotionAnimationSpec("out_whip_away", "Whip Away", "হুইপ আউট", AnimationCategory.OUT, AnimationMediaScope.ALL),
        MotionAnimationSpec("out_spin_out", "Spin Out", "ঘূর্ণন প্রস্থান", AnimationCategory.OUT, AnimationMediaScope.ALL),
        MotionAnimationSpec("out_glitch_out", "Glitch Out", "গ্লিচ আউট", AnimationCategory.OUT, AnimationMediaScope.ALL),
        MotionAnimationSpec("out_flash_fade", "Flash Out", "ফ্ল্যাশ ফেড", AnimationCategory.OUT, AnimationMediaScope.ALL),
        MotionAnimationSpec("out_dip_black", "Cinema Dip", "সিনেমা ডিপ", AnimationCategory.OUT, AnimationMediaScope.ALL),
        MotionAnimationSpec("out_fold_away", "Paper Fold", "ফোল্ড আউট", AnimationCategory.OUT, AnimationMediaScope.ALL),
        MotionAnimationSpec("out_drift_down", "Drift Away", "ড্রিফট আউট", AnimationCategory.OUT, AnimationMediaScope.ALL),
        MotionAnimationSpec("out_snap_shrink", "Snap Out", "স্ন্যাপ আউট", AnimationCategory.OUT, AnimationMediaScope.ALL),
        MotionAnimationSpec("out_tilt_exit", "Tilt Exit", "টিল্ট আউট", AnimationCategory.OUT, AnimationMediaScope.ALL),
        MotionAnimationSpec("out_blur_out", "Blur Out", "ব্লার আউট", AnimationCategory.OUT, AnimationMediaScope.ALL),
        MotionAnimationSpec("out_iris_close", "Iris Close", "আইরিস ক্লোজ", AnimationCategory.OUT, AnimationMediaScope.ALL),

        // LOOP ANIMATIONS (12 Core Loop + Ken Burns/Orbit)
        MotionAnimationSpec("loop_none", "Static", "লুপ নেই", AnimationCategory.LOOP, AnimationMediaScope.ALL),
        MotionAnimationSpec("loop_float", "Floating", "ফ্লোটিং (ভাসা)", AnimationCategory.LOOP, AnimationMediaScope.ALL),
        MotionAnimationSpec("loop_pulse", "Pulse", "পালস বিট", AnimationCategory.LOOP, AnimationMediaScope.ALL),
        MotionAnimationSpec("loop_breathe", "Breathing", "ব্রিদিং জুম", AnimationCategory.LOOP, AnimationMediaScope.ALL),
        MotionAnimationSpec("loop_bounce_idle", "Bounce", "সফট বাউন্স", AnimationCategory.LOOP, AnimationMediaScope.ALL),
        MotionAnimationSpec("loop_jitter", "Shake", "হ্যান্ডহেল্ড শেক", AnimationCategory.LOOP, AnimationMediaScope.ALL),
        MotionAnimationSpec("loop_spin_slow", "Rotation", "ঘূর্ণন রোটেশন", AnimationCategory.LOOP, AnimationMediaScope.ALL),
        MotionAnimationSpec("loop_wave", "Wave", "ওয়েভ দুলুনি", AnimationCategory.LOOP, AnimationMediaScope.ALL),
        MotionAnimationSpec("loop_glow", "Glow", "গোল্ড/নিয়ন গ্লো", AnimationCategory.LOOP, AnimationMediaScope.ALL),
        MotionAnimationSpec("loop_flicker", "Flicker", "প্রজেক্টর ফ্লিকার", AnimationCategory.LOOP, AnimationMediaScope.ALL),
        MotionAnimationSpec("loop_pendulum", "Swing", "সুইং দোলনা", AnimationCategory.LOOP, AnimationMediaScope.ALL),
        MotionAnimationSpec("loop_zoom_creep", "Continuous Zoom", "কন্টিনিউয়াস জুম", AnimationCategory.LOOP, AnimationMediaScope.ALL),
        MotionAnimationSpec("loop_ken_burns", "Ken Burns", "কেন বার্নস প্যান", AnimationCategory.LOOP, AnimationMediaScope.IMAGE),
        MotionAnimationSpec("loop_parallax", "Parallax", "লুপ প্যারালাক্স", AnimationCategory.LOOP, AnimationMediaScope.ALL),
        MotionAnimationSpec("loop_orbit", "Orbit Drift", "অরবিট ড্রিফট", AnimationCategory.LOOP, AnimationMediaScope.ALL),
        MotionAnimationSpec("loop_heartbeat", "Heartbeat", "হার্টবিট", AnimationCategory.LOOP, AnimationMediaScope.ALL),
        MotionAnimationSpec("loop_glitch_pulse", "Glitch Pulse", "গ্লিচ পালস", AnimationCategory.LOOP, AnimationMediaScope.ALL)
    )

    val sfxPresets: List<SfxPreset> = buildList {
        val whooshSpecs = listOf(
            "Fast Air Whoosh" to 320, "Deep Cinema Whoosh" to 520, "Whip Pan Swoosh" to 260,
            "Reverse Riser Swish" to 600, "Wind Pass By" to 450, "Silk Slide Whoosh" to 300,
            "Laser Whip" to 240, "Metallic Swoosh" to 380, "Subtle Page Turn" to 290, "Jet Flyby" to 650
        )
        whooshSpecs.forEachIndexed { i, (name, dur) ->
            add(SfxPreset("sfx_whoosh_$i", name, name, SfxCategory.WHOOSH, dur, 180f + i * 35f, 950f + i * 80f, SynthWaveform.SWEEP_UP, 0.72f))
        }
        val popSpecs = listOf(
            "Clean Bubble Pop" to 110, "Glass Click" to 85, "Modern UI Tap" to 70,
            "Soft Notification" to 190, "Wooden Pluck" to 130, "Camera Shutter" to 140,
            "Switch Snap" to 90, "Sparkle Bell" to 320, "Blip High" to 100, "Chime Confirm" to 280
        )
        popSpecs.forEachIndexed { i, (name, dur) ->
            add(SfxPreset("sfx_pop_$i", name, name, SfxCategory.POP, dur, 520f + i * 90f, 1280f + i * 60f, SynthWaveform.SINE, 0.08f))
        }
        val impactSpecs = listOf(
            "Documentary Boom" to 680, "Sub Bass Drop" to 750, "Trailer Hit" to 540,
            "Anvil Strike" to 460, "Door Slam Thud" to 350, "Heartbeat Thump" to 260,
            "Drum Accent" to 310, "Deep Earth Rumble" to 820, "Punch Hit" to 210
        )
        impactSpecs.forEachIndexed { i, (name, dur) ->
            add(SfxPreset("sfx_impact_$i", name, name, SfxCategory.IMPACT, dur, 160f - i * 8f, 38f, SynthWaveform.SWEEP_DOWN, 0.35f))
        }
        val typingSpecs = listOf(
            "Mechanical Key Click" to 75, "Typewriter Carriage" to 180, "Vintage Keystroke" to 90,
            "Subtitle Tick" to 65, "Morse Beep" to 120, "Digital Counter" to 80,
            "Clock Tick" to 105, "Stopwatch Latch" to 115
        )
        typingSpecs.forEachIndexed { i, (name, dur) ->
            add(SfxPreset("sfx_type_$i", name, name, SfxCategory.TYPING, dur, 1100f + i * 120f, 880f, SynthWaveform.SQUARE, 0.42f))
        }
        val cinemaSpecs = listOf(
            "Tension Riser" to 950, "Warm Pad Swell" to 850, "Broadcast Sting" to 500,
            "Film Reel Start" to 420, "Horizon Chime" to 720, "Time Freeze" to 640,
            "Mystery Drone" to 900, "Chapter Gong" to 800
        )
        cinemaSpecs.forEachIndexed { i, (name, dur) ->
            add(SfxPreset("sfx_cinema_$i", name, name, SfxCategory.CINEMA, dur, 220f + i * 30f, 540f + i * 45f, SynthWaveform.TRIANGLE, 0.18f))
        }
        val glitchSpecs = listOf(
            "VHS Static Burst" to 220, "Digital Tear" to 180, "Cyber Zap" to 250,
            "Data Corrupt" to 290, "Signal Drop" to 340, "Radio Tuning" to 400, "Pixel Snap" to 150
        )
        glitchSpecs.forEachIndexed { i, (name, dur) ->
            add(SfxPreset("sfx_glitch_$i", name, name, SfxCategory.GLITCH, dur, 920f + i * 140f, 240f, SynthWaveform.NOISE_BURST, 0.85f))
        }
    }

    val speedRampPresets: List<SpeedRampProfile> = listOf(
        SpeedRampProfile("Normal (1.0x)", listOf(SpeedPoint(0f, 1f), SpeedPoint(0.5f, 1f), SpeedPoint(1f, 1f))),
        SpeedRampProfile("Hero Slow-Mo Dip", listOf(SpeedPoint(0f, 1.8f), SpeedPoint(0.35f, 0.3f), SpeedPoint(0.65f, 0.3f), SpeedPoint(1f, 2.0f))),
        SpeedRampProfile("Bullet Time", listOf(SpeedPoint(0f, 3.0f), SpeedPoint(0.25f, 0.25f), SpeedPoint(0.75f, 0.25f), SpeedPoint(1f, 3.0f))),
        SpeedRampProfile("Montage Surge", listOf(SpeedPoint(0f, 0.6f), SpeedPoint(0.5f, 2.4f), SpeedPoint(1f, 0.8f))),
        SpeedRampProfile("Documentary Glide", listOf(SpeedPoint(0f, 1.0f), SpeedPoint(0.5f, 0.65f), SpeedPoint(1f, 1.0f)))
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

    val textColorSwatches: List<Long> = listOf(
        0xFFF3F6FC, // Pure Cinema White
        0xFFFFB300, // Hollywood Gold
        0xFF00E5FF, // Cyan Neon
        0xFFFF4D6D, // Thriller Crimson
        0xFF2CE59B, // Emerald Mint
        0xFFA77BFF, // Royal Violet
        0xFFFF7A00, // Sunset Amber
        0xFF38BDF8, // Sky Electric
        0xFFFDE047, // Marker Yellow
        0xFFF472B6, // Rose Neon
        0xFFEDE0C8, // Vintage Parchment
        0xFF101522, // Obsidian Dark
        0xFF0A0C12, // Deep Ink Black
        0xFF94A3B8  // Steel Silver
    )

    val textStylePresets: List<TextStylePreset> = listOf(
        TextStylePreset(
            id = "style_clean",
            nameBn = "ক্লিন সিনেমা",
            nameEn = "Clean Cinema",
            fontId = StudioFontOption.HIND_SILIGURI.id,
            textColorHex = 0xFFF3F6FC,
            accentColorHex = 0xFFFFB300,
            shadowRadius = 8f
        ),
        TextStylePreset(
            id = "style_hollywood_gold",
            nameBn = "হলিউড গোল্ড",
            nameEn = "Hollywood Gold",
            fontId = StudioFontOption.PLAYFAIR_DISPLAY.id,
            textColorHex = 0xFFFFD54F,
            accentColorHex = 0xFFFFB300,
            useGradient = true,
            gradientStartHex = 0xFFFFF3B0,
            gradientEndHex = 0xFFFF9800,
            strokeColorHex = 0xFF2B1B00,
            strokeWidthPx = 2.5f,
            shadowRadius = 14f,
            shadowColorHex = 0xDD000000,
            letterSpacingSp = 1.5f
        ),
        TextStylePreset(
            id = "style_neon_cyan",
            nameBn = "নিয়ন গ্লো",
            nameEn = "Cyan Neon",
            fontId = StudioFontOption.SPACE_GROTESK.id,
            textColorHex = 0xFF00E5FF,
            accentColorHex = 0xFF38BDF8,
            strokeColorHex = 0xFF042F3B,
            strokeWidthPx = 2f,
            shadowRadius = 20f,
            shadowColorHex = 0xEE00E5FF,
            letterSpacingSp = 1.2f
        ),
        TextStylePreset(
            id = "style_breaking_news",
            nameBn = "ব্রেকিং নিউজ",
            nameEn = "Breaking News",
            fontId = StudioFontOption.NOTO_SANS_BENGALI.id,
            textColorHex = 0xFFFFFFFF,
            accentColorHex = 0xFFFF3B30,
            backgroundPill = true,
            pillColorHex = 0xE67F1D1D,
            strokeColorHex = 0xFF1A0505,
            strokeWidthPx = 1.5f,
            lowerThirdTemplate = LowerThirdTemplate.NEWS_LOWER_THIRD
        ),
        TextStylePreset(
            id = "style_doc_serif",
            nameBn = "আর্কাইভ সেরিফ",
            nameEn = "Archive Serif",
            fontId = StudioFontOption.NOTO_SERIF_BENGALI.id,
            textColorHex = 0xFFF5ECD8,
            accentColorHex = 0xFFD97706,
            backgroundPill = true,
            pillColorHex = 0xDF1C1917,
            shadowRadius = 6f,
            lowerThirdTemplate = LowerThirdTemplate.DOCUMENTARY_DATE_CARD
        ),
        TextStylePreset(
            id = "style_bold_stroke",
            nameBn = "বোল্ড স্ট্রোক",
            nameEn = "Impact Stroke",
            fontId = StudioFontOption.HIND_SILIGURI.id,
            textColorHex = 0xFFFFFFFF,
            accentColorHex = 0xFFFDE047,
            strokeColorHex = 0xFF000000,
            strokeWidthPx = 6.5f,
            shadowRadius = 10f,
            shadowColorHex = 0xFF000000
        ),
        TextStylePreset(
            id = "style_sunset_grad",
            nameBn = "সানসেট গ্রেডিয়েন্ট",
            nameEn = "Sunset Gradient",
            fontId = StudioFontOption.HIND_SILIGURI.id,
            textColorHex = 0xFFFF7A00,
            accentColorHex = 0xFFFF4D6D,
            useGradient = true,
            gradientStartHex = 0xFFFF4D6D,
            gradientEndHex = 0xFFFFD166,
            strokeColorHex = 0xFF1F0A14,
            strokeWidthPx = 2.5f,
            shadowRadius = 12f
        ),
        TextStylePreset(
            id = "style_cyber_electric",
            nameBn = "সাইবার ইলেকট্রিক",
            nameEn = "Cyber Electric",
            fontId = StudioFontOption.SPACE_GROTESK.id,
            textColorHex = 0xFFA77BFF,
            accentColorHex = 0xFF00E5FF,
            useGradient = true,
            gradientStartHex = 0xFF00E5FF,
            gradientEndHex = 0xFFA77BFF,
            strokeColorHex = 0xFF090D16,
            strokeWidthPx = 3f,
            shadowRadius = 18f,
            shadowColorHex = 0xCC7C3AED,
            letterSpacingSp = 1.8f
        ),
        TextStylePreset(
            id = "style_glass_pill",
            nameBn = "গ্লাস ক্যাপশন",
            nameEn = "Glass Minimal",
            fontId = StudioFontOption.NOTO_SANS_BENGALI.id,
            textColorHex = 0xFFF8FAFC,
            accentColorHex = 0xFF2CE59B,
            backgroundPill = true,
            pillColorHex = 0xB81E293B,
            shadowRadius = 4f,
            lowerThirdTemplate = LowerThirdTemplate.MINIMAL_CAPTION
        ),
        TextStylePreset(
            id = "style_typewriter_evidence",
            nameBn = "টাইপরাইটার নথি",
            nameEn = "Evidence Mono",
            fontId = StudioFontOption.JETBRAINS_MONO.id,
            textColorHex = 0xFF111827,
            accentColorHex = 0xFFB45309,
            backgroundPill = true,
            pillColorHex = 0xF0EFE6D5,
            shadowRadius = 0f,
            letterSpacingSp = 1.4f
        ),
        TextStylePreset(
            id = "style_crimson_thriller",
            nameBn = "থ্রিলার রেড",
            nameEn = "Crimson Thriller",
            fontId = StudioFontOption.NOTO_SERIF_BENGALI.id,
            textColorHex = 0xFFFF4D6D,
            accentColorHex = 0xFFFFB300,
            useGradient = true,
            gradientStartHex = 0xFFFF4D6D,
            gradientEndHex = 0xFF991B1B,
            strokeColorHex = 0xFF000000,
            strokeWidthPx = 3.5f,
            shadowRadius = 16f,
            shadowColorHex = 0xCCFF1E42
        ),
        TextStylePreset(
            id = "style_emerald_tech",
            nameBn = "এমেরাল্ড টেক",
            nameEn = "Emerald Tech",
            fontId = StudioFontOption.JETBRAINS_MONO.id,
            textColorHex = 0xFF2CE59B,
            accentColorHex = 0xFF00E5FF,
            backgroundPill = true,
            pillColorHex = 0xDC06211A,
            strokeColorHex = 0xFF022C22,
            strokeWidthPx = 1.5f,
            shadowRadius = 14f,
            shadowColorHex = 0x992CE59B
        ),
        TextStylePreset(
            id = "style_vintage_sepia",
            nameBn = "ভিন্টেজ সেপিয়া",
            nameEn = "Vintage Sepia",
            fontId = StudioFontOption.NOTO_SERIF_BENGALI.id,
            textColorHex = 0xFFFDE68A,
            accentColorHex = 0xFFF59E0B,
            strokeColorHex = 0xFF451A03,
            strokeWidthPx = 2f,
            shadowRadius = 8f,
            shadowColorHex = 0xCC291202
        ),
        TextStylePreset(
            id = "style_royal_violet",
            nameBn = "রয়্যাল পার্পল",
            nameEn = "Royal Violet",
            fontId = StudioFontOption.PLAYFAIR_DISPLAY.id,
            textColorHex = 0xFFE9D5FF,
            accentColorHex = 0xFFF472B6,
            useGradient = true,
            gradientStartHex = 0xFFF472B6,
            gradientEndHex = 0xFF8B5CF6,
            strokeColorHex = 0xFF1E1B4B,
            strokeWidthPx = 2f,
            shadowRadius = 14f
        ),
        TextStylePreset(
            id = "style_high_contrast",
            nameBn = "হাই-কনট্রাস্ট সাব",
            nameEn = "High Contrast Sub",
            fontId = StudioFontOption.HIND_SILIGURI.id,
            textColorHex = 0xFFFDE047,
            accentColorHex = 0xFFFFFFFF,
            backgroundPill = true,
            pillColorHex = 0xEB000000,
            strokeColorHex = 0xFF000000,
            strokeWidthPx = 3f,
            shadowRadius = 4f
        ),
        TextStylePreset(
            id = "style_outline_hollow",
            nameBn = "ডাবল বর্ডার",
            nameEn = "Neon Outline",
            fontId = StudioFontOption.SPACE_GROTESK.id,
            textColorHex = 0xFF0A0C12,
            accentColorHex = 0xFF00E5FF,
            strokeColorHex = 0xFF00E5FF,
            strokeWidthPx = 5.5f,
            shadowRadius = 12f,
            shadowColorHex = 0xCC00E5FF,
            letterSpacingSp = 2.0f
        )
    )

    fun applyTextStylePreset(current: TextLayerConfig, preset: TextStylePreset): TextLayerConfig {
        return current.copy(
            stylePresetId = preset.id,
            fontId = if (current.customFontPath == null) preset.fontId else current.fontId,
            textColorHex = preset.textColorHex,
            accentColorHex = preset.accentColorHex,
            useGradient = preset.useGradient,
            gradientStartHex = preset.gradientStartHex,
            gradientEndHex = preset.gradientEndHex,
            strokeColorHex = preset.strokeColorHex,
            strokeWidthPx = preset.strokeWidthPx,
            shadowRadius = preset.shadowRadius,
            shadowColorHex = preset.shadowColorHex,
            letterSpacingSp = preset.letterSpacingSp,
            backgroundPill = preset.backgroundPill,
            pillColorHex = preset.pillColorHex,
            lowerThirdTemplate = preset.lowerThirdTemplate
        )
    }

    private val chartPalette = listOf(
        0xFF00E5FFL, 0xFFFFB300L, 0xFFFF4D6DL, 0xFF2CE59BL,
        0xFFA77BFFL, 0xFF38BDF8L, 0xFFFB7185L, 0xFFFACC15L
    )

    fun generateDefaultBars(count: Int): List<ChartBarItem> {
        val safeCount = count.coerceIn(2, 60)
        val labels = listOf(
            "ঢাকা", "চট্টগ্রাম", "রাজশাহী", "খুলনা", "সিলেট", "বরিশাল", "রংপুর", "ময়মনসিংহ",
            "গাজীপুর", "নারায়ণগঞ্জ", "কুমিল্লা", "বগুড়া", "যশোর", "দিনাজপুর", "টাঙ্গাইল", "ফরিদপুর"
        )
        return (0 until safeCount).map { idx ->
            val label = if (idx < labels.size) labels[idx] else "আইটেম ${idx + 1}"
            val valNum = ((safeCount - idx) * (95f / safeCount) + ((idx * 17) % 19)).coerceIn(8f, 100f)
            ChartBarItem(
                id = "bar_$idx",
                label = label,
                value = valNum,
                colorHex = chartPalette[idx % chartPalette.size]
            )
        }
    }

    fun buildDefaultComparisonGrid(slotCount: Int): ComparisonGridConfig {
        val drawables = listOf("img_tpl_documentary", "img_tpl_street_cinema", "img_tpl_motion", "img_studio_hero")
        val shapes = listOf(GeometricShape.HEXAGON, GeometricShape.CIRCLE, GeometricShape.DIAMOND, GeometricShape.SHIELD)
        val captions = listOf("দৃশ্য ১ · ১৯৭১", "দৃশ্য ২ · ২০২৬", "তথ্য ৩ · আর্কাইভ", "তথ্য ৪ · ম্যাপ")
        val count = slotCount.coerceIn(2, 4)
        return ComparisonGridConfig(
            title = "তুলনামূলক দৃশ্য ($count)",
            slotCount = count,
            slots = (0 until count).map { i ->
                ComparisonSlot(
                    id = "slot_$i",
                    caption = captions[i],
                    shape = shapes[i],
                    borderColorHex = chartPalette[i],
                    mediaSource = MediaSource(drawableName = drawables[i % drawables.size], label = captions[i])
                )
            }
        )
    }

    /**
     * Generates a complete multi-track Investigative Documentary project automatically
     * from a user's topic or script lines!
     */
    fun buildAutoInvestigativeDocumentary(topic: String, scriptNote: String): StudioProject {
        val cleanTopic = topic.ifBlank { "অজানা অনুসন্ধান · গোপন আর্কাইভ" }
        val cleanNote = scriptNote.ifBlank { "ঐতিহাসিক নথি ও পরিসংখ্যান বিশ্লেষণ অনুযায়ী চাঞ্চল্যকর তথ্য" }
        return StudioProject(
            name = cleanTopic.take(26),
            aspectRatio = AspectRatioPreset.LANDSCAPE_16_9,
            previewResolution = PreviewResolution.P1080,
            thumbnailDrawable = "img_tpl_documentary",
            totalDurationMs = 18_000L,
            clips = listOf(
                TimelineClip(
                    trackType = TrackType.VIDEO,
                    title = "আর্কাইভ ফুটেজ",
                    startMs = 0L,
                    durationMs = 9_000L,
                    loopAnimationId = "loop_ken_burns",
                    mediaSource = MediaSource(drawableName = "img_tpl_documentary", label = "Archive Base")
                ),
                TimelineClip(
                    trackType = TrackType.VIDEO,
                    title = "রাতের অনুসন্ধান",
                    startMs = 9_000L,
                    durationMs = 9_000L,
                    loopAnimationId = "loop_zoom_creep",
                    mediaSource = MediaSource(drawableName = "img_tpl_street_cinema", label = "Night Investigation")
                ),
                TimelineClip(
                    trackType = TrackType.VFX,
                    title = "৩৫মিমি ফিল্ম + ফ্লেয়ার",
                    startMs = 0L,
                    durationMs = 18_000L,
                    vfxConfig = VfxConfig(VfxEffectType.FILM_GRAIN, intensity = 0.6f, speed = 1f)
                ),
                TimelineClip(
                    trackType = TrackType.ADJUSTMENT,
                    title = "হলিউড ডক গ্রেড",
                    startMs = 0L,
                    durationMs = 18_000L,
                    colorGrade = lumetriPresets[1].grade
                ),
                TimelineClip(
                    trackType = TrackType.GRAPHICS,
                    title = "নথি হাইলাইট",
                    startMs = 800L,
                    durationMs = 5_500L,
                    posY = 0.05f,
                    inAnimationId = "in_cinema_reveal",
                    lockedSfxId = "sfx_type_1",
                    investigativeConfig = InvestigativeDocConfig(
                        toolType = InvestigativeToolType.NEWSPAPER_LOUPE,
                        headline = cleanTopic,
                        bodyText = cleanNote
                    )
                ),
                TimelineClip(
                    trackType = TrackType.GRAPHICS,
                    title = "তুলনামূলক ৬০-বার চার্ট",
                    startMs = 6_600L,
                    durationMs = 6_000L,
                    inAnimationId = "in_slide_up",
                    lockedSfxId = "sfx_whoosh_1",
                    chartConfig = ComparisonChartConfig(
                        title = "$cleanTopic · ডেটা তুলনা",
                        barCount = 12,
                        bars = generateDefaultBars(12)
                    )
                ),
                TimelineClip(
                    trackType = TrackType.GRAPHICS,
                    laneIndex = 1,
                    title = "আর্কাইভ হেক্সাগন ফ্রেম",
                    startMs = 12_800L,
                    durationMs = 5_000L,
                    scale = 0.72f,
                    inAnimationId = "in_zoom_pop",
                    borderConfig = BorderConfig(
                        style = BorderShapeStyle.HEXAGON_FRAME,
                        thicknessDp = 5f,
                        colorHex = 0xFFFFB300,
                        secondaryColorHex = 0xFF00E5FF,
                        glowRadius = 12f
                    ),
                    mediaSource = MediaSource(drawableName = "img_studio_hero", label = "Archive Frame")
                ),
                TimelineClip(
                    trackType = TrackType.TEXT,
                    title = "কারাওকে ন্যারেশন",
                    startMs = 500L,
                    durationMs = 17_000L,
                    posY = 0.34f,
                    textConfig = TextLayerConfig(
                        text = "$cleanTopic — $cleanNote",
                        subtitle = "অনুসন্ধানী প্রতিবেদন · ChitraFlow Pro",
                        fontId = StudioFontOption.HIND_SILIGURI.id,
                        fontSizeSp = 19f,
                        karaokeHighlight = true,
                        backgroundPill = true,
                        accentColorHex = 0xFFFFB300
                    )
                ),
                TimelineClip(
                    trackType = TrackType.VOICEOVER,
                    title = "মূল ভয়েসওভার (অটো-ডাক ট্রিগার)",
                    startMs = 1_500L,
                    durationMs = 10_000L,
                    audioConfig = AudioConfig(volume = 1.2f, noiseReductionEnabled = true, waveformSeed = 19)
                ),
                TimelineClip(
                    trackType = TrackType.MUSIC,
                    title = "সিনেমা সাসপেন্স BGM",
                    startMs = 0L,
                    durationMs = 18_000L,
                    audioConfig = AudioConfig(volume = 0.85f, autoDuckingEnabled = true, duckingFloor = 0.22f, waveformSeed = 42)
                )
            )
        )
    }

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
            id = "tpl_investigative_pro",
            titleBn = "অনুসন্ধানী ডকুমেন্টারি প্রো",
            titleEn = "Investigative Documentary Pro",
            subtitleBn = "ম্যাপ + নথি লুপ + চার্ট + অটো-ডাকিং",
            aspectRatio = AspectRatioPreset.LANDSCAPE_16_9,
            thumbnailDrawable = "img_tpl_documentary",
            buildProject = { buildAutoInvestigativeDocumentary("বাংলার অজানা ইতিহাস", "ঐতিহাসিক আর্কাইভ ও নথি বিশ্লেষণ") }
        ),
        StudioTemplate(
            id = "tpl_motion_reel",
            titleBn = "মোশন গ্রাফিক্স রিল",
            titleEn = "Kinetic Motion Reel",
            subtitleBn = "কি-ফ্রেম + হলিউড VFX + স্পিড র‍্যাম্প",
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
                            trackType = TrackType.VFX,
                            title = "Anamorphic Flare",
                            startMs = 0L,
                            durationMs = 12_000L,
                            vfxConfig = VfxConfig(VfxEffectType.ANAMORPHIC_FLARE, 0.7f, 1f)
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
                            textConfig = TextLayerConfig(
                                text = "চিত্রফ্লো মাস্টার প্রো",
                                subtitle = "HOLLYWOOD VFX · 60 BAR CHART",
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
            subtitleBn = "৩-স্লট কম্পারিজন + অটো-ডাকিং",
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
                            mediaSource = MediaSource(drawableName = "img_tpl_street_cinema", label = "Rain Street Cinema")
                        ),
                        TimelineClip(
                            trackType = TrackType.VFX,
                            title = "Cinema Rain",
                            startMs = 0L,
                            durationMs = 15_000L,
                            vfxConfig = VfxConfig(VfxEffectType.CINEMA_RAIN, 0.75f, 1.2f)
                        ),
                        TimelineClip(
                            trackType = TrackType.GRAPHICS,
                            title = "লোকেশন ম্যাপ রুট",
                            startMs = 1_200L,
                            durationMs = 6_000L,
                            inAnimationId = "in_cinema_reveal",
                            investigativeConfig = InvestigativeDocConfig(
                                toolType = InvestigativeToolType.MAP_RADAR_ROUTE,
                                headline = "রাত ১১:৪৫ · ঢাকা",
                                pointALabel = "শাহবাগ",
                                pointBLabel = "সদরঘাট"
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
