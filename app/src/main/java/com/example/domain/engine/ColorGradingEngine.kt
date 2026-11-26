package com.example.domain.engine

import android.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorMatrix as ComposeColorMatrix
import com.example.domain.model.LumetriColorGrade

object ColorGradingEngine {

    /**
     * Combines Lumetri sliders (brightness, contrast, saturation, temperature)
     * plus Master + RGB Curve offsets into an Android ColorMatrix for Canvas/Export
     * and Compose ColorFilter.
     */
    fun buildAndroidColorMatrix(grade: LumetriColorGrade): ColorMatrix {
        val matrix = ColorMatrix()

        // 1. Saturation
        val satMatrix = ColorMatrix()
        satMatrix.setSaturation(grade.saturation.coerceIn(0f, 2.5f))
        matrix.postConcat(satMatrix)

        // 2. Contrast + Brightness + Curve midtone/shadow/highlight lifts
        val masterLift = (grade.masterCurve.getOrElse(0) { 0f }) * 120f
        val masterMidGain = 1f + ((grade.masterCurve.getOrElse(2) { 0.5f } - 0.5f) * 0.8f)
        val contrast = (grade.contrast * masterMidGain).coerceIn(0.3f, 2.5f)
        val translate = ((-0.5f * contrast + 0.5f) + grade.brightness) * 255f + masterLift

        // 3. Temperature + Per-Channel RGB Curve Gain
        val tempWarmth = grade.temperature * 42f
        val rGain = 1f + (grade.redCurve.getOrElse(2) { 0.5f } - 0.5f) * 0.9f
        val gGain = 1f + (grade.greenCurve.getOrElse(2) { 0.5f } - 0.5f) * 0.9f
        val bGain = 1f + (grade.blueCurve.getOrElse(2) { 0.5f } - 0.5f) * 0.9f

        val rLift = (grade.redCurve.getOrElse(0) { 0f }) * 90f
        val gLift = (grade.greenCurve.getOrElse(0) { 0f }) * 90f
        val bLift = (grade.blueCurve.getOrElse(0) { 0f }) * 90f

        val lumetriArray = floatArrayOf(
            contrast * rGain, 0f, 0f, 0f, translate + tempWarmth + rLift,
            0f, contrast * gGain, 0f, 0f, translate + tempWarmth * 0.15f + gLift,
            0f, 0f, contrast * bGain, 0f, translate - tempWarmth + bLift,
            0f, 0f, 0f, 1f, 0f
        )
        matrix.postConcat(ColorMatrix(lumetriArray))
        return matrix
    }

    fun buildComposeColorMatrix(grade: LumetriColorGrade): ComposeColorMatrix {
        return ComposeColorMatrix(buildAndroidColorMatrix(grade).array)
    }

    fun mergeAdjustmentGrades(base: LumetriColorGrade, adjustment: LumetriColorGrade): LumetriColorGrade {
        return LumetriColorGrade(
            brightness = (base.brightness + adjustment.brightness).coerceIn(-0.8f, 0.8f),
            contrast = (base.contrast * adjustment.contrast).coerceIn(0.3f, 2.5f),
            saturation = (base.saturation * adjustment.saturation).coerceIn(0f, 2.5f),
            temperature = (base.temperature + adjustment.temperature).coerceIn(-0.8f, 0.8f),
            glow = maxOf(base.glow, adjustment.glow),
            blurRadius = maxOf(base.blurRadius, adjustment.blurRadius),
            vignette = maxOf(base.vignette, adjustment.vignette),
            masterCurve = adjustment.masterCurve,
            redCurve = adjustment.redCurve,
            greenCurve = adjustment.greenCurve,
            blueCurve = adjustment.blueCurve
        )
    }
}
