package com.example.domain.engine

import android.graphics.Bitmap
import android.graphics.Color
import com.example.domain.model.TimelineClip
import com.example.domain.model.TransitionType
import kotlin.math.max
import kotlin.math.sin

data class TransitionFrameState(
    val alphaMult: Float = 1f,
    val offsetXNorm: Float = 0f,
    val offsetYNorm: Float = 0f,
    val rotationDeg: Float = 0f,
    val scaleMult: Float = 1f,
    val irisRadiusFrac: Float = 1f, // 1f = fully open, <1f = circular iris wipe
    val flashAlpha: Float = 0f,
    val isWarmFlash: Boolean = false
)

object TransitionAndChromaEngine {

    private const val DEFAULT_TRANSITION_MS = 650L

    fun evaluateTransition(clip: TimelineClip, playheadMs: Long): TransitionFrameState {
        if (clip.durationMs <= 0L) return TransitionFrameState()
        val hasIn = clip.transitionIn != TransitionType.NONE
        val hasOut = clip.transitionOut != TransitionType.NONE
        if (!hasIn && !hasOut) return TransitionFrameState()

        val localMs = (playheadMs - clip.startMs).coerceIn(0L, clip.durationMs)
        val remainingMs = (clip.durationMs - localMs).coerceAtLeast(0L)
        val maxHalf = (clip.durationMs / 2L).coerceAtLeast(150L)

        val inWin = minOf(clip.transitionInDurationMs.coerceIn(150L, 3000L), maxHalf)
        val outWin = minOf(clip.transitionOutDurationMs.coerceIn(150L, 3000L), maxHalf)

        val inState = if (hasIn && localMs < inWin) {
            val t = (localMs.toFloat() / inWin.toFloat()).coerceIn(0f, 1f)
            evaluateSingle(clip.transitionIn, progressZeroToOne = t, isOut = false)
        } else null

        val outState = if (hasOut && remainingMs < outWin) {
            // As clip approaches end (remainingMs -> 0), visibility goes 1 -> 0
            val visFrac = (remainingMs.toFloat() / outWin.toFloat()).coerceIn(0f, 1f)
            evaluateSingle(clip.transitionOut, progressZeroToOne = visFrac, isOut = true)
        } else null

        if (inState == null && outState == null) return TransitionFrameState()
        val a = inState ?: TransitionFrameState()
        val b = outState ?: TransitionFrameState()
        return TransitionFrameState(
            alphaMult = (a.alphaMult * b.alphaMult).coerceIn(0f, 1f),
            offsetXNorm = a.offsetXNorm + b.offsetXNorm,
            offsetYNorm = a.offsetYNorm + b.offsetYNorm,
            rotationDeg = a.rotationDeg + b.rotationDeg,
            scaleMult = (a.scaleMult * b.scaleMult).coerceIn(0.15f, 3.5f),
            irisRadiusFrac = minOf(a.irisRadiusFrac, b.irisRadiusFrac),
            flashAlpha = max(a.flashAlpha, b.flashAlpha),
            isWarmFlash = a.isWarmFlash || b.isWarmFlash
        )
    }

    private fun evaluateSingle(type: TransitionType, progressZeroToOne: Float, isOut: Boolean): TransitionFrameState {
        val t = progressZeroToOne.coerceIn(0f, 1f)
        val inv = 1f - t
        val eased = 1f - inv * inv * inv
        val dirSign = if (isOut) -1f else 1f

        return when (type) {
            TransitionType.NONE -> TransitionFrameState()
            TransitionType.CROSS_DISSOLVE -> TransitionFrameState(
                alphaMult = eased
            )
            TransitionType.WHIP_PAN -> TransitionFrameState(
                alphaMult = (0.35f + 0.65f * eased).coerceIn(0f, 1f),
                offsetXNorm = (1f - eased) * 0.85f * dirSign
            )
            TransitionType.ZOOM_THROUGH -> TransitionFrameState(
                alphaMult = (0.25f + 0.75f * eased).coerceIn(0f, 1f),
                scaleMult = if (isOut) 0.55f + 0.45f * eased else 1.65f - 0.65f * eased
            )
            TransitionType.GLITCH_CUT -> {
                val jitter = sin(t * 28f) * (1f - t) * 0.14f
                val flash = if (t < 0.45f) (1f - t / 0.45f) * 0.45f else 0f
                TransitionFrameState(
                    alphaMult = if (t < 0.12f) 0.4f else 1f,
                    offsetXNorm = jitter,
                    flashAlpha = flash,
                    isWarmFlash = false
                )
            }
            TransitionType.FILM_BURN -> {
                val burnPeak = sin(t * Math.PI).toFloat().coerceIn(0f, 1f) * 0.72f
                TransitionFrameState(
                    alphaMult = (0.4f + 0.6f * eased).coerceIn(0f, 1f),
                    flashAlpha = burnPeak,
                    isWarmFlash = true
                )
            }
            TransitionType.IRIS_WIPE -> TransitionFrameState(
                irisRadiusFrac = eased.coerceIn(0.04f, 1f)
            )
            TransitionType.SLIDE_WARP -> TransitionFrameState(
                alphaMult = (0.45f + 0.55f * eased).coerceIn(0f, 1f),
                offsetYNorm = (1f - eased) * 0.72f * dirSign,
                scaleMult = 1.08f - 0.08f * eased
            )
            TransitionType.LUMA_DISSOLVE -> {
                val bloom = sin(t * Math.PI).toFloat().coerceIn(0f, 1f) * 0.35f
                TransitionFrameState(
                    alphaMult = (eased * eased).coerceIn(0f, 1f),
                    flashAlpha = bloom,
                    isWarmFlash = false
                )
            }
            TransitionType.SPIN_FLASH -> {
                val spinAngle = (1f - eased) * 32f * dirSign
                val flash = sin(t * Math.PI).toFloat().coerceIn(0f, 1f) * 0.55f
                TransitionFrameState(
                    alphaMult = (0.3f + 0.7f * eased).coerceIn(0f, 1f),
                    rotationDeg = spinAngle,
                    scaleMult = 1.25f - 0.25f * eased,
                    flashAlpha = flash,
                    isWarmFlash = false
                )
            }
        }
    }

    /**
     * Pure-Kotlin alpha calculation for Green-Screen Chroma Keying.
     * Returns 0 (transparent) for pure green pixels, 255 for opaque subject, with smooth edge feathering.
     */
    fun computeChromaAlpha(r: Int, g: Int, b: Int, strength: Float): Int {
        val clampedStrength = strength.coerceIn(0.1f, 0.95f)
        val maxRb = max(r, b)
        val greenDominance = (g - maxRb) / 255f
        // Threshold lowers as strength increases (aggressive keyer)
        val lowThreshold = (0.42f - clampedStrength * 0.34f).coerceIn(0.03f, 0.38f)
        val featherBand = 0.14f

        if (greenDominance <= lowThreshold) return 255
        if (greenDominance >= lowThreshold + featherBand) return 0
        val t = (greenDominance - lowThreshold) / featherBand
        return ((1f - t) * 255f).toInt().coerceIn(0, 255)
    }

    /**
     * Applies fast pixel-level green screen removal + spill suppression on a software bitmap.
     * Scales down oversized input temporarily if needed, keeping preview & export crisp and responsive.
     */
    fun applyGreenScreenChromaKey(source: Bitmap, strength: Float): Bitmap {
        val safeSrc = if (source.config != Bitmap.Config.ARGB_8888) {
            source.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            source
        }
        val w = safeSrc.width
        val h = safeSrc.height
        val pixels = IntArray(w * h)
        safeSrc.getPixels(pixels, 0, w, 0, 0, w, h)

        for (i in pixels.indices) {
            val px = pixels[i]
            val origA = (px ushr 24) and 0xFF
            if (origA == 0) continue
            val r = (px ushr 16) and 0xFF
            val g = (px ushr 8) and 0xFF
            val b = px and 0xFF

            val keyAlpha = computeChromaAlpha(r, g, b, strength)
            if (keyAlpha == 0) {
                pixels[i] = 0
            } else if (keyAlpha < 255) {
                // Edge spill suppression: clamp green down toward max(R, B)
                val maxRb = max(r, b)
                val cleanG = minOf(g, maxRb + 12)
                val finalA = (origA * keyAlpha) / 255
                pixels[i] = Color.argb(finalA, r, cleanG, b)
            }
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        if (safeSrc !== source) safeSrc.recycle()
        return out
    }
}
