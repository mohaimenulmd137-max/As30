package com.example.domain.engine

import com.example.domain.model.AnimationDirection
import com.example.domain.model.AnimationEasingType
import com.example.domain.model.AnimationSlotConfig
import com.example.domain.model.KeyframeEasing
import com.example.domain.model.SpeedRampProfile
import com.example.domain.model.TimelineClip
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

data class EvaluatedTransform(
    val posX: Float,
    val posY: Float,
    val scale: Float,
    val rotation: Float,
    val opacity: Float,
    val clipWipe: Float = 1f,
    val blurRadius: Float = 0f,
    val shadowStrength: Float = 0f,
    val skewX: Float = 0f,
    val skewY: Float = 0f,
    val rotationX: Float = 0f,
    val rotationY: Float = 0f,
    val anchorX: Float = 0.5f,
    val anchorY: Float = 0.5f,
    val textRevealFraction: Float = 1f,
    val textWordMode: Boolean = false,
    val textLineMode: Boolean = false,
    val textSpacingBoostSp: Float = 0f,
    val underlineProgress: Float = 0f,
    val splitCharWave: Float = 0f,
    val glitchIntensity: Float = 0f,
    val glowBoost: Float = 0f,
    val flashAlpha: Float = 0f,
    val dynamicCropScale: Float = 1f
)

object KeyframeInterpolator {

    fun evaluate(
        clip: TimelineClip,
        playheadMs: Long,
        bypassAnimations: Boolean = false
    ): EvaluatedTransform {
        val localMs = (playheadMs - clip.startMs).coerceIn(0L, clip.durationMs)
        val prof = clip.animationProfile

        val base = evaluateKeyframes(clip, localMs)

        var x = base.posX
        var y = base.posY
        var scale = base.scale
        var rot = base.rotation
        var alpha = base.opacity
        var wipe = 1f
        var blur = maxOf(base.blurRadius, prof.blurRadius)
        var shadow = prof.shadowStrength
        var skewX = prof.skewX
        var skewY = prof.skewY
        var rotX = prof.perspective
        var rotY = 0f
        var textReveal = 1f
        var wordMode = false
        var lineMode = false
        var spacingBoostSp = 0f
        var underlineProg = 0f
        var splitCharWave = 0f
        var glitchInt = 0f
        var glowBoost = 0f
        var flashAlpha = 0f
        var dynamicCrop = 1f

        if (bypassAnimations) {
            return EvaluatedTransform(
                posX = x,
                posY = y,
                scale = scale.coerceIn(0.05f, 5f),
                rotation = rot,
                opacity = alpha.coerceIn(0f, 1f),
                clipWipe = 1f,
                blurRadius = blur.coerceIn(0f, 32f),
                shadowStrength = shadow.coerceIn(0f, 2f),
                skewX = skewX,
                skewY = skewY,
                rotationX = rotX,
                rotationY = rotY,
                anchorX = prof.anchorX.coerceIn(0f, 1f),
                anchorY = prof.anchorY.coerceIn(0f, 1f)
            )
        }

        val effectiveIn = prof.inSlot.let { slot ->
            if (slot.animationId == "in_fade" && clip.inAnimationId != "in_fade") {
                slot.copy(animationId = clip.inAnimationId, enabled = clip.inAnimationId != "in_none")
            } else slot
        }
        val effectiveLoop = prof.loopSlot.let { slot ->
            if (slot.animationId == "loop_none" && clip.loopAnimationId != "loop_none") {
                slot.copy(animationId = clip.loopAnimationId, enabled = true)
            } else slot
        }
        val effectiveOut = prof.outSlot.let { slot ->
            if (slot.animationId == "out_fade" && clip.outAnimationId != "out_fade") {
                slot.copy(animationId = clip.outAnimationId, enabled = clip.outAnimationId != "out_none")
            } else slot
        }

        // 1. IN Animation Slot (supports 0.2s-15s duration, delay, speed, intensity, 7 directions, startOffset, repeat, easing)
        if (effectiveIn.enabled && effectiveIn.animationId != "in_none") {
            val dur = effectiveIn.durationMs.coerceIn(200L, minOf(15_000L, clip.durationMs.coerceAtLeast(200L)))
            val startAt = (effectiveIn.delayMs + effectiveIn.startOffsetMs).coerceIn(0L, (clip.durationMs - 100L).coerceAtLeast(0L))
            val effectiveWindow = (dur / effectiveIn.speed.coerceIn(0.2f, 4f)).toLong().coerceAtLeast(150L)
            if (localMs < startAt) {
                alpha *= 0f
            } else if (localMs < startAt + effectiveWindow) {
                val raw = ((localMs - startAt).toFloat() / effectiveWindow.toFloat()).coerceIn(0f, 1f)
                val reps = effectiveIn.repeatCount.coerceIn(1, 5)
                val repRaw = if (reps > 1) (raw * reps) % 1f else raw
                val t = evaluateSlotEasing(repRaw, effectiveIn)
                val inten = effectiveIn.intensity.coerceIn(0.1f, 2.5f)
                val (dxDir, dyDir) = directionVector(effectiveIn.direction, defaultX = -1f, defaultY = 0f)

                when (effectiveIn.animationId) {
                    "in_fade", "img_fade" -> alpha *= t
                    "in_slide_left" -> { x -= (1f - t) * 0.65f * inten; alpha *= t }
                    "in_slide_right" -> { x += (1f - t) * 0.65f * inten; alpha *= t }
                    "in_slide_up" -> { y += (1f - t) * 0.55f * inten; alpha *= t }
                    "in_slide_down" -> { y -= (1f - t) * 0.55f * inten; alpha *= t }
                    "in_zoom_pop", "img_zoom", "vid_cinematic_zoom" -> {
                        val pop = if (effectiveIn.animationId == "in_zoom_pop") easeOutBack(repRaw) else t
                        scale *= (1f - (0.65f * inten).coerceAtMost(0.92f) + (0.65f * inten).coerceAtMost(0.92f) * pop)
                        alpha *= t.coerceIn(0f, 1f)
                    }
                    "img_zoom_out" -> {
                        scale *= (1f + (1f - t) * 0.55f * inten)
                        alpha *= t
                    }
                    "in_cinema_reveal" -> {
                        wipe = t
                        x += (1f - t) * 0.08f * dxDir * inten
                        y += (1f - t) * 0.08f * dyDir * inten
                        alpha *= t
                    }
                    "in_drop_bounce" -> {
                        y -= (1f - easeOutBounce(repRaw)) * 0.45f * inten
                        alpha *= t
                    }
                    "in_whip_pan" -> {
                        x += (1f - t) * 1.05f * dxDir * inten
                        y += (1f - t) * 0.65f * dyDir * inten
                        rot -= (1f - t) * 18f * inten
                    }
                    "in_spin_scale" -> {
                        scale *= (0.25f + 0.75f * t)
                        rot -= (1f - t) * 180f * inten
                        alpha *= t
                    }
                    "in_flip_tilt", "text_3d_flip" -> {
                        scale *= (0.72f + 0.28f * t)
                        rotX += (1f - t) * 78f * inten
                        rotY += (1f - t) * 28f * dxDir * inten
                        alpha *= t
                    }
                    "in_glitch_snap" -> {
                        val j = if (repRaw < 0.78f) sin(repRaw * 48f) * 0.08f * inten else 0f
                        x += j
                        glitchInt = maxOf(glitchInt, (1f - repRaw) * inten)
                        alpha *= if (repRaw < 0.3f && ((repRaw * 20).toInt() % 2 == 0)) 0.35f else 1f
                    }
                    "in_doc_stamp", "text_ink_stamp" -> {
                        scale *= (1f + (1f - t) * 0.75f * inten)
                        alpha *= t
                        shadow += (1f - t) * 0.8f
                    }
                    "in_flash_cut", "vid_flash" -> {
                        flashAlpha = maxOf(flashAlpha, (1f - repRaw) * 0.85f * inten)
                        alpha *= if (repRaw < 0.15f) 0.35f else 1f
                    }
                    "in_iris_open" -> {
                        wipe = t
                        scale *= (0.85f + 0.15f * t)
                    }
                    "in_drift_rise" -> {
                        y += (1f - t) * 0.2f * inten
                        x += (1f - t) * 0.08f * dxDir * inten
                        alpha *= t
                    }
                    "in_swing_in" -> {
                        rot -= (1f - t) * 28f * inten * cos(repRaw * PI.toFloat() * 2f)
                        alpha *= t
                    }
                    // TEXT SPECIFIC IN ANIMATIONS (16 Total)
                    "text_typewriter", "text_char_reveal" -> {
                        textReveal = t
                    }
                    "text_word_pop", "text_word_cascade" -> {
                        textReveal = t
                        wordMode = true
                        val pop = easeOutBack(repRaw)
                        scale *= (0.88f + 0.12f * pop)
                        y += (1f - t) * (if (effectiveIn.animationId == "text_word_cascade") 0.08f else 0.03f) * inten
                    }
                    "text_line_reveal" -> {
                        textReveal = t
                        lineMode = true
                        y += (1f - t) * 0.06f * inten
                    }
                    "text_cinematic_spacing" -> {
                        spacingBoostSp += (1f - t) * 14f * inten
                        alpha *= t
                    }
                    "text_split_char" -> {
                        splitCharWave = (1f - t) * inten
                        textReveal = (0.35f + 0.65f * t).coerceIn(0f, 1f)
                        alpha *= t
                    }
                    "text_underline_stroke" -> {
                        underlineProg = t
                        alpha *= (0.35f + 0.65f * t)
                    }
                    "text_whip_skew" -> {
                        x += (1f - t) * 0.7f * dxDir * inten
                        skewX += (1f - t) * 28f * inten
                        alpha *= t
                    }
                    "text_blur_reveal", "img_blur_reveal", "vid_motion_blur" -> {
                        blur += (1f - t) * 18f * inten
                        x += (1f - t) * 0.14f * dxDir * inten
                        y += (1f - t) * 0.14f * dyDir * inten
                        alpha *= t
                    }
                }
                if (effectiveIn.direction != AnimationDirection.CENTER &&
                    effectiveIn.animationId !in listOf("in_slide_left", "in_slide_right", "in_slide_up", "in_slide_down")
                ) {
                    // Apply subtle directional travel when user explicitly picks Forward/Backward/Left/Right/Top/Bottom
                    when (effectiveIn.direction) {
                        AnimationDirection.FORWARD -> scale *= (0.84f + 0.16f * t)
                        AnimationDirection.BACKWARD -> scale *= (1.18f - 0.18f * t)
                        else -> Unit
                    }
                }
            }
        }

        // 2. OUT Animation Slot (supports 0.2s-15s, endOffsetMs, speed, intensity, directions, easing)
        if (effectiveOut.enabled && effectiveOut.animationId != "out_none") {
            val dur = effectiveOut.durationMs.coerceIn(200L, minOf(15_000L, clip.durationMs.coerceAtLeast(200L)))
            val effectiveWindow = (dur / effectiveOut.speed.coerceIn(0.2f, 4f)).toLong().coerceAtLeast(150L)
            val endCutoff = clip.durationMs - effectiveOut.endOffsetMs.coerceIn(0L, (clip.durationMs / 2L))
            val outStart = (endCutoff - effectiveWindow).coerceAtLeast(0L)
            if (localMs >= endCutoff && effectiveOut.endOffsetMs > 0L) {
                alpha *= 0f
            } else if (localMs >= outStart) {
                val raw = ((localMs - outStart).toFloat() / effectiveWindow.toFloat()).coerceIn(0f, 1f)
                val t = evaluateSlotEasing(raw, effectiveOut)
                val inten = effectiveOut.intensity.coerceIn(0.1f, 2.5f)
                val (dxDir, dyDir) = directionVector(effectiveOut.direction, defaultX = 1f, defaultY = 0f)

                when (effectiveOut.animationId) {
                    "out_fade" -> alpha *= (1f - t)
                    "out_slide_left" -> { x -= t * 0.65f * inten; alpha *= (1f - t) }
                    "out_slide_right" -> { x += t * 0.65f * inten; alpha *= (1f - t) }
                    "out_slide_up" -> { y -= t * 0.55f * inten; alpha *= (1f - t) }
                    "out_slide_down" -> { y += t * 0.55f * inten; alpha *= (1f - t) }
                    "out_zoom_shrink" -> { scale *= (1f - (0.7f * inten).coerceAtMost(0.95f) * t); alpha *= (1f - t) }
                    "out_zoom_explode" -> { scale *= (1f + 0.9f * inten * t); alpha *= (1f - t) }
                    "out_whip_away" -> {
                        x += t * 1.1f * dxDir * inten
                        y += t * 0.6f * dyDir * inten
                        rot += t * 18f * inten
                        alpha *= (1f - t * 0.7f)
                    }
                    "out_spin_out" -> { scale *= (1f - 0.75f * t); rot += t * 180f * inten; alpha *= (1f - t) }
                    "out_glitch_out" -> {
                        x += sin(raw * 50f) * 0.09f * inten
                        glitchInt = maxOf(glitchInt, t * inten)
                        alpha *= (1f - t)
                    }
                    "out_flash_fade" -> {
                        flashAlpha = maxOf(flashAlpha, (1f - abs(raw - 0.35f) * 2.5f).coerceIn(0f, 1f) * inten)
                        alpha *= (1f - t)
                    }
                    "out_dip_black" -> alpha *= (1f - t)
                    "out_fold_away" -> { scale *= (1f - 0.5f * t); rot -= t * 25f; rotX += t * 65f; alpha *= (1f - t) }
                    "out_drift_down" -> { y += t * 0.2f * inten; alpha *= (1f - t) }
                    "out_snap_shrink" -> { scale *= (1f - 0.85f * t); alpha *= (1f - t) }
                    "out_tilt_exit" -> { rot += t * 32f * inten; rotY += t * 45f; alpha *= (1f - t) }
                    "out_blur_out" -> { blur += t * 18f * inten; alpha *= (1f - t) }
                    "out_iris_close" -> { wipe = minOf(wipe, 1f - t); alpha *= (1f - t * 0.4f) }
                }
            }
        }

        // 3. LOOP / IMAGE-PAN / VIDEO ANIMATION Slot (runs concurrently with IN & OUT!)
        if (effectiveLoop.enabled && effectiveLoop.animationId != "loop_none") {
            val loopLocal = (localMs - effectiveLoop.delayMs).coerceAtLeast(0L)
            val sec = (loopLocal / 1000f) * effectiveLoop.speed.coerceIn(0.2f, 4f)
            val inten = effectiveLoop.intensity.coerceIn(0.1f, 2.5f)
            val cycleDurSec = (effectiveLoop.durationMs.coerceIn(200L, 15_000L) / 1000f)
            val progress = ((localMs.toFloat() / clip.durationMs.coerceAtLeast(1L).toFloat())).coerceIn(0f, 1f)
            val cycPhase = ((sec / cycleDurSec.coerceAtLeast(0.2f)) * 2f * PI.toFloat())
            val (dxDir, dyDir) = directionVector(effectiveLoop.direction, defaultX = 1f, defaultY = 1f)

            when (effectiveLoop.animationId) {
                "loop_float" -> {
                    y += sin(cycPhase * 0.8f) * 0.03f * inten
                    x += cos(cycPhase * 0.4f) * 0.01f * dxDir * inten
                }
                "loop_pulse" -> scale *= (1f + sin(cycPhase * 1.4f) * 0.07f * inten)
                "loop_breathe" -> scale *= (1f + sin(cycPhase * 0.6f) * 0.05f * inten)
                "loop_bounce_idle" -> y -= abs(sin(cycPhase)) * 0.045f * inten
                "loop_jitter", "vid_camera_shake" -> {
                    x += sin(sec * 29f) * 0.014f * inten
                    y += cos(sec * 23f) * 0.014f * inten
                    rot += sin(sec * 19f) * 1.2f * inten
                }
                "loop_spin_slow" -> rot += sec * 24f * inten
                "loop_wave" -> {
                    y += sin(cycPhase) * 0.025f * inten
                    rot += cos(cycPhase) * 4f * inten
                    skewX += sin(cycPhase) * 4f * inten
                }
                "loop_glow" -> {
                    val pulse = 0.5f + 0.5f * sin(cycPhase)
                    glowBoost = maxOf(glowBoost, pulse * 0.9f * inten)
                    shadow += pulse * 0.6f * inten
                }
                "loop_flicker" -> alpha *= (0.78f + 0.22f * sin(sec * 34f))
                "loop_pendulum" -> rot += sin(cycPhase * 0.8f) * 9f * inten
                "loop_zoom_creep", "img_slow_zoom", "vid_smooth_zoom" -> {
                    scale *= (1f + progress * 0.18f * inten)
                }
                "loop_ken_burns" -> {
                    scale *= (1f + progress * 0.18f * inten)
                    x += (progress - 0.5f) * 0.08f * dxDir * inten
                }
                "img_pan_left" -> {
                    scale *= (1.14f)
                    x += (0.5f - progress) * 0.12f * inten
                }
                "img_pan_right" -> {
                    scale *= (1.14f)
                    x += (progress - 0.5f) * 0.12f * inten
                }
                "img_pan_up" -> {
                    scale *= (1.14f)
                    y += (0.5f - progress) * 0.12f * inten
                }
                "img_pan_down" -> {
                    scale *= (1.14f)
                    y += (progress - 0.5f) * 0.12f * inten
                }
                "img_parallax", "loop_parallax" -> {
                    scale *= (1.10f)
                    x += sin(cycPhase * 0.5f) * 0.045f * inten
                    rotY += cos(cycPhase * 0.5f) * 8f * inten
                }
                "vid_slow_pan" -> {
                    scale *= (1.12f)
                    x += (progress - 0.5f) * 0.10f * dxDir * inten
                    y += (progress - 0.5f) * 0.06f * dyDir * inten
                }
                "vid_dynamic_crop" -> {
                    val cropPulse = 1f + (0.14f + 0.08f * sin(cycPhase)) * inten
                    dynamicCrop = cropPulse
                    scale *= cropPulse
                }
                "vid_glitch", "loop_glitch_pulse" -> {
                    val burst = ((sec * 5f).toInt() % 6) == 0
                    if (burst) {
                        x += sin(sec * 60f) * 0.035f * inten
                        glitchInt = maxOf(glitchInt, 0.85f * inten)
                    }
                }
                "vid_speed_ramp" -> {
                    val surge = abs(sin(cycPhase * 0.5f))
                    scale *= (1f + surge * 0.08f * inten)
                    blur += surge * 2.5f * inten
                }
                "loop_orbit" -> {
                    x += cos(cycPhase * 0.7f) * 0.035f * inten
                    y += sin(cycPhase * 0.7f) * 0.035f * inten
                }
                "loop_heartbeat" -> {
                    val beat = abs(sin(sec * 4.2f)).pow(6f)
                    scale *= (1f + beat * 0.12f * inten)
                }
                "loop_strobe" -> alpha *= if (((sec * 8f).toInt() % 4) == 0) 0.55f else 1f
                "loop_vibrate" -> {
                    x += sin(sec * 42f) * 0.01f * inten
                    rot += cos(sec * 38f) * 1.8f * inten
                }
                "loop_tilt_rock" -> rot += sin(cycPhase * 0.7f) * 5.5f * inten
            }
        }

        return EvaluatedTransform(
            posX = x,
            posY = y,
            scale = scale.coerceIn(0.05f, 5f),
            rotation = rot,
            opacity = alpha.coerceIn(0f, 1f),
            clipWipe = wipe.coerceIn(0f, 1f),
            blurRadius = blur.coerceIn(0f, 32f),
            shadowStrength = shadow.coerceIn(0f, 2f),
            skewX = skewX.coerceIn(-45f, 45f),
            skewY = skewY.coerceIn(-45f, 45f),
            rotationX = rotX.coerceIn(-89f, 89f),
            rotationY = rotY.coerceIn(-89f, 89f),
            anchorX = prof.anchorX.coerceIn(0f, 1f),
            anchorY = prof.anchorY.coerceIn(0f, 1f),
            textRevealFraction = textReveal.coerceIn(0f, 1f),
            textWordMode = wordMode,
            textLineMode = lineMode,
            textSpacingBoostSp = spacingBoostSp.coerceIn(0f, 24f),
            underlineProgress = underlineProg.coerceIn(0f, 1f),
            splitCharWave = splitCharWave.coerceIn(0f, 2.5f),
            glitchIntensity = glitchInt.coerceIn(0f, 2f),
            glowBoost = glowBoost.coerceIn(0f, 2f),
            flashAlpha = flashAlpha.coerceIn(0f, 1f),
            dynamicCropScale = dynamicCrop.coerceIn(1f, 2.5f)
        )
    }

    private fun evaluateKeyframes(clip: TimelineClip, localMs: Long): EvaluatedTransform {
        val sorted = clip.keyframes.sortedBy { it.timeOffsetMs }
        if (sorted.isEmpty()) {
            return EvaluatedTransform(clip.posX, clip.posY, clip.scale, clip.rotation, clip.opacity)
        }
        if (localMs <= sorted.first().timeOffsetMs) {
            val k = sorted.first()
            return EvaluatedTransform(k.posX, k.posY, k.scale, k.rotation, k.opacity, blurRadius = k.blurRadius)
        }
        if (localMs >= sorted.last().timeOffsetMs) {
            val k = sorted.last()
            return EvaluatedTransform(k.posX, k.posY, k.scale, k.rotation, k.opacity, blurRadius = k.blurRadius)
        }

        val idx = sorted.indexOfLast { it.timeOffsetMs <= localMs }.coerceIn(0, sorted.lastIndex - 1)
        val a = sorted[idx]
        val b = sorted[idx + 1]
        val span = (b.timeOffsetMs - a.timeOffsetMs).coerceAtLeast(1L)
        val rawT = ((localMs - a.timeOffsetMs).toFloat() / span.toFloat()).coerceIn(0f, 1f)
        val t = applyKeyframeEasing(rawT, b.easing, b.customBezier)

        return EvaluatedTransform(
            posX = lerp(a.posX, b.posX, t),
            posY = lerp(a.posY, b.posY, t),
            scale = lerp(a.scale, b.scale, t),
            rotation = lerp(a.rotation, b.rotation, t),
            opacity = lerp(a.opacity, b.opacity, t),
            blurRadius = lerp(a.blurRadius, b.blurRadius, t)
        )
    }

    fun evaluateSlotEasing(t: Float, slot: AnimationSlotConfig): Float = when (slot.easing) {
        AnimationEasingType.LINEAR -> t
        AnimationEasingType.EASE_IN -> easeInCubic(t)
        AnimationEasingType.EASE_OUT -> easeOutCubic(t)
        AnimationEasingType.EASE_IN_OUT -> if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3f) / 2f
        AnimationEasingType.CUBIC_BEZIER, AnimationEasingType.CUSTOM_CURVE ->
            solveCubicBezier(t, slot.bezierX1, slot.bezierY1, slot.bezierX2, slot.bezierY2)
    }

    private fun applyKeyframeEasing(t: Float, easing: KeyframeEasing, bezier: List<Float>): Float = when (easing) {
        KeyframeEasing.LINEAR -> t
        KeyframeEasing.EASE_IN -> easeInCubic(t)
        KeyframeEasing.EASE_OUT -> easeOutCubic(t)
        KeyframeEasing.EASE_IN_OUT -> if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3f) / 2f
        KeyframeEasing.CUBIC_BEZIER -> solveCubicBezier(t, 0.22f, 1f, 0.36f, 1f)
        KeyframeEasing.CUSTOM_CURVE -> {
            val x1 = bezier.getOrElse(0) { 0.25f }
            val y1 = bezier.getOrElse(1) { 0.1f }
            val x2 = bezier.getOrElse(2) { 0.25f }
            val y2 = bezier.getOrElse(3) { 1f }
            solveCubicBezier(t, x1, y1, x2, y2)
        }
        KeyframeEasing.SPRING -> easeOutBack(t)
        KeyframeEasing.HOLD -> 0f
    }

    /**
     * Evaluates a 1D cubic Bezier curve Y(x) using binary search for t given x = [progress].
     */
    fun solveCubicBezier(x: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val targetX = x.coerceIn(0f, 1f)
        val cx1 = x1.coerceIn(0f, 1f)
        val cx2 = x2.coerceIn(0f, 1f)
        var lo = 0f
        var hi = 1f
        var mid = targetX
        repeat(12) {
            mid = (lo + hi) * 0.5f
            val bx = cubicCoord(mid, cx1, cx2)
            if (bx < targetX) lo = mid else hi = mid
        }
        return cubicCoord(mid, y1.coerceIn(-0.6f, 1.6f), y2.coerceIn(-0.6f, 1.6f))
    }

    private fun cubicCoord(t: Float, p1: Float, p2: Float): Float {
        val u = 1f - t
        return 3f * u * u * t * p1 + 3f * u * t * t * p2 + t * t * t
    }

    private fun directionVector(dir: AnimationDirection, defaultX: Float, defaultY: Float): Pair<Float, Float> = when (dir) {
        AnimationDirection.LEFT -> -1f to 0f
        AnimationDirection.RIGHT -> 1f to 0f
        AnimationDirection.TOP -> 0f to -1f
        AnimationDirection.BOTTOM -> 0f to 1f
        AnimationDirection.FORWARD -> 0f to 0f
        AnimationDirection.BACKWARD -> 0f to 0f
        AnimationDirection.CENTER -> defaultX to defaultY
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
    private fun easeOutCubic(t: Float) = 1f - (1f - t).pow(3f)
    private fun easeInCubic(t: Float) = t * t * t
    private fun easeOutBack(t: Float): Float {
        val c1 = 1.70158f
        val c3 = c1 + 1f
        return 1f + c3 * (t - 1f).pow(3f) + c1 * (t - 1f).pow(2f)
    }

    private fun easeOutBounce(x: Float): Float {
        val n1 = 7.5625f
        val d1 = 2.75f
        return when {
            x < 1f / d1 -> n1 * x * x
            x < 2f / d1 -> { val t = x - 1.5f / d1; n1 * t * t + 0.75f }
            x < 2.5f / d1 -> { val t = x - 2.25f / d1; n1 * t * t + 0.9375f }
            else -> { val t = x - 2.625f / d1; n1 * t * t + 0.984375f }
        }
    }
}

object SpeedRampEngine {
    fun evaluateSpeedAt(profile: SpeedRampProfile, normalizedTime: Float): Float {
        val pts = profile.points.sortedBy { it.normalizedTime }
        if (pts.isEmpty()) return 1f
        val t = normalizedTime.coerceIn(0f, 1f)
        if (t <= pts.first().normalizedTime) return pts.first().speed
        if (t >= pts.last().normalizedTime) return pts.last().speed
        val idx = pts.indexOfLast { it.normalizedTime <= t }.coerceIn(0, pts.lastIndex - 1)
        val a = pts[idx]
        val b = pts[idx + 1]
        val span = (b.normalizedTime - a.normalizedTime).coerceAtLeast(0.001f)
        val frac = (t - a.normalizedTime) / span
        return a.speed + (b.speed - a.speed) * frac
    }

    fun computeRemappedVideoTimeMs(
        profile: SpeedRampProfile,
        localMs: Long,
        clipDurationMs: Long
    ): Long {
        if (clipDurationMs <= 0L) return 0L
        val steps = 20
        val normTarget = (localMs.toFloat() / clipDurationMs.toFloat()).coerceIn(0f, 1f)
        var integral = 0f
        val dt = normTarget / steps
        for (i in 0 until steps) {
            val mid = (i + 0.5f) * dt
            integral += evaluateSpeedAt(profile, mid) * dt
        }
        return (integral * clipDurationMs).toLong().coerceAtLeast(0L)
    }
}
