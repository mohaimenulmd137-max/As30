package com.example.domain.engine

import com.example.domain.model.Keyframe
import com.example.domain.model.KeyframeEasing
import com.example.domain.model.SpeedPoint
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
    val clipWipe: Float = 1f
)

object KeyframeInterpolator {

    fun evaluate(clip: TimelineClip, playheadMs: Long): EvaluatedTransform {
        val localMs = (playheadMs - clip.startMs).coerceIn(0L, clip.durationMs)
        val base = evaluateKeyframes(clip, localMs)

        var x = base.posX
        var y = base.posY
        var scale = base.scale
        var rot = base.rotation
        var alpha = base.opacity
        var wipe = 1f

        val animWindowMs = minOf(650L, clip.durationMs / 3L).coerceAtLeast(180L)

        // IN Animation
        if (localMs < animWindowMs && clip.inAnimationId != "in_none") {
            val t = (localMs.toFloat() / animWindowMs.toFloat()).coerceIn(0f, 1f)
            val ease = easeOutCubic(t)
            when (clip.inAnimationId) {
                "in_fade" -> alpha *= ease
                "in_slide_left" -> { x -= (1f - ease) * 0.65f; alpha *= ease }
                "in_slide_right" -> { x += (1f - ease) * 0.65f; alpha *= ease }
                "in_slide_up" -> { y += (1f - ease) * 0.55f; alpha *= ease }
                "in_slide_down" -> { y -= (1f - ease) * 0.55f; alpha *= ease }
                "in_zoom_pop" -> { scale *= (0.35f + 0.65f * easeOutBack(t)); alpha *= t }
                "in_cinema_reveal" -> { wipe = ease; alpha *= ease }
                "in_drop_bounce" -> { y -= (1f - easeOutBounce(t)) * 0.45f; alpha *= t }
                "in_whip_pan" -> { x -= (1f - ease) * 1.1f; rot -= (1f - ease) * 18f }
                "in_spin_scale" -> { scale *= (0.2f + 0.8f * ease); rot -= (1f - ease) * 180f; alpha *= ease }
                "in_flip_tilt" -> { scale *= (0.7f + 0.3f * ease); rot += (1f - ease) * 35f; alpha *= ease }
                "in_glitch_snap" -> {
                    val jitter = if (t < 0.7f) sin(t * 48f) * 0.08f else 0f
                    x += jitter
                    alpha *= if (t < 0.3f && ((t * 20).toInt() % 2 == 0)) 0.3f else 1f
                }
                "in_doc_stamp" -> { scale *= (1.6f - 0.6f * easeOutCubic(t)); alpha *= ease }
                "in_flash_cut" -> { alpha *= if (t < 0.25f) 0.2f else 1f }
                "in_iris_open" -> { wipe = ease; scale *= (0.85f + 0.15f * ease) }
                "in_drift_rise" -> { y += (1f - ease) * 0.18f; alpha *= ease }
                "in_swing_in" -> { rot -= (1f - ease) * 28f * cos(t * PI.toFloat() * 2f); alpha *= ease }
            }
        }

        // OUT Animation
        val remainingMs = clip.durationMs - localMs
        if (remainingMs < animWindowMs && clip.outAnimationId != "out_none") {
            val t = (1f - remainingMs.toFloat() / animWindowMs.toFloat()).coerceIn(0f, 1f)
            val ease = easeInCubic(t)
            when (clip.outAnimationId) {
                "out_fade" -> alpha *= (1f - ease)
                "out_slide_left" -> { x -= ease * 0.65f; alpha *= (1f - ease) }
                "out_slide_right" -> { x += ease * 0.65f; alpha *= (1f - ease) }
                "out_slide_up" -> { y -= ease * 0.55f; alpha *= (1f - ease) }
                "out_slide_down" -> { y += ease * 0.55f; alpha *= (1f - ease) }
                "out_zoom_shrink" -> { scale *= (1f - 0.7f * ease); alpha *= (1f - ease) }
                "out_zoom_explode" -> { scale *= (1f + 0.9f * ease); alpha *= (1f - ease) }
                "out_whip_away" -> { x += ease * 1.1f; rot += ease * 18f; alpha *= (1f - ease * 0.7f) }
                "out_spin_out" -> { scale *= (1f - 0.75f * ease); rot += ease * 180f; alpha *= (1f - ease) }
                "out_glitch_out" -> { x += sin(t * 50f) * 0.09f; alpha *= (1f - ease) }
                "out_flash_fade" -> { alpha *= if (t in 0.2f..0.4f) 0.35f else (1f - ease) }
                "out_dip_black" -> alpha *= (1f - ease)
                "out_fold_away" -> { scale *= (1f - 0.5f * ease); rot -= ease * 25f; alpha *= (1f - ease) }
                "out_drift_down" -> { y += ease * 0.2f; alpha *= (1f - ease) }
                "out_snap_shrink" -> { scale *= (1f - 0.85f * ease); alpha *= (1f - ease) }
                "out_tilt_exit" -> { rot += ease * 32f; alpha *= (1f - ease) }
                "out_iris_close" -> { wipe = 1f - ease; alpha *= (1f - ease * 0.4f) }
            }
        }

        // LOOP Animation
        if (clip.loopAnimationId != "loop_none") {
            val sec = localMs / 1000f
            when (clip.loopAnimationId) {
                "loop_float" -> y += sin(sec * 2.4f) * 0.025f
                "loop_pulse" -> scale *= (1f + sin(sec * 4.5f) * 0.06f)
                "loop_breathe" -> scale *= (1f + sin(sec * 1.6f) * 0.04f)
                "loop_ken_burns" -> {
                    val progress = (localMs.toFloat() / clip.durationMs.coerceAtLeast(1L).toFloat())
                    scale *= (1f + progress * 0.16f)
                    x += (progress - 0.5f) * 0.06f
                }
                "loop_jitter" -> {
                    x += sin(sec * 29f) * 0.012f
                    y += cos(sec * 23f) * 0.012f
                }
                "loop_flicker" -> alpha *= (0.85f + 0.15f * sin(sec * 34f))
                "loop_pendulum" -> rot += sin(sec * 2.5f) * 7f
                "loop_orbit" -> {
                    x += cos(sec * 1.8f) * 0.03f
                    y += sin(sec * 1.8f) * 0.03f
                }
                "loop_heartbeat" -> {
                    val beat = abs(sin(sec * 4.2f)).pow(6f)
                    scale *= (1f + beat * 0.1f)
                }
                "loop_glitch_pulse" -> if (((sec * 6f).toInt() % 7) == 0) x += sin(sec * 60f) * 0.035f
                "loop_wave" -> {
                    y += sin(sec * 3.2f) * 0.02f
                    rot += cos(sec * 3.2f) * 3f
                }
                "loop_spin_slow" -> rot += sec * 18f
                "loop_zoom_creep" -> scale *= (1f + (localMs / 10_000f) * 0.14f)
                "loop_strobe" -> alpha *= if (((sec * 8f).toInt() % 4) == 0) 0.55f else 1f
                "loop_bounce_idle" -> y -= abs(sin(sec * 3.4f)) * 0.03f
                "loop_vibrate" -> {
                    x += sin(sec * 42f) * 0.008f
                    rot += cos(sec * 38f) * 1.5f
                }
                "loop_tilt_rock" -> rot += sin(sec * 2f) * 4.5f
            }
        }

        return EvaluatedTransform(
            posX = x,
            posY = y,
            scale = scale.coerceIn(0.05f, 5f),
            rotation = rot,
            opacity = alpha.coerceIn(0f, 1f),
            clipWipe = wipe.coerceIn(0f, 1f)
        )
    }

    private fun evaluateKeyframes(clip: TimelineClip, localMs: Long): EvaluatedTransform {
        val sorted = clip.keyframes.sortedBy { it.timeOffsetMs }
        if (sorted.isEmpty()) {
            return EvaluatedTransform(clip.posX, clip.posY, clip.scale, clip.rotation, clip.opacity)
        }
        if (localMs <= sorted.first().timeOffsetMs) {
            val k = sorted.first()
            return EvaluatedTransform(k.posX, k.posY, k.scale, k.rotation, k.opacity)
        }
        if (localMs >= sorted.last().timeOffsetMs) {
            val k = sorted.last()
            return EvaluatedTransform(k.posX, k.posY, k.scale, k.rotation, k.opacity)
        }

        val idx = sorted.indexOfLast { it.timeOffsetMs <= localMs }.coerceIn(0, sorted.lastIndex - 1)
        val a = sorted[idx]
        val b = sorted[idx + 1]
        val span = (b.timeOffsetMs - a.timeOffsetMs).coerceAtLeast(1L)
        val rawT = ((localMs - a.timeOffsetMs).toFloat() / span.toFloat()).coerceIn(0f, 1f)
        val t = applyEasing(rawT, b.easing)

        return EvaluatedTransform(
            posX = lerp(a.posX, b.posX, t),
            posY = lerp(a.posY, b.posY, t),
            scale = lerp(a.scale, b.scale, t),
            rotation = lerp(a.rotation, b.rotation, t),
            opacity = lerp(a.opacity, b.opacity, t)
        )
    }

    private fun applyEasing(t: Float, easing: KeyframeEasing): Float = when (easing) {
        KeyframeEasing.LINEAR -> t
        KeyframeEasing.EASE_IN_OUT -> if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3f) / 2f
        KeyframeEasing.SPRING -> easeOutBack(t)
        KeyframeEasing.HOLD -> 0f
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
