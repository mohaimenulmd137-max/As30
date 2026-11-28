package com.example.domain.engine.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import com.example.domain.model.ChartPaletteTheme
import com.example.domain.model.ChartVisualStyle
import com.example.domain.model.ComparisonChartConfig
import com.example.domain.model.InvestigativeDocConfig
import com.example.domain.model.InvestigativeToolType
import com.example.domain.model.StudioFontOption
import com.example.domain.model.TimelineClip
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object ExportGraphicsOverlays {

    fun drawComparisonChart(
        canvas: Canvas,
        clip: TimelineClip,
        chart: ComparisonChartConfig,
        width: Int,
        height: Int,
        playheadMs: Long,
        fontCache: Map<String, Typeface>
    ) {
        val activeBars = chart.bars.take(chart.barCount.coerceIn(0, 100)).let {
            if (chart.sortDescending) it.sortedByDescending { b -> b.value } else it
        }
        val resolvedStyle = if (chart.chartStyle == ChartVisualStyle.HORIZONTAL_BAR && !chart.isHorizontal) {
            ChartVisualStyle.VERTICAL_BAR
        } else {
            chart.chartStyle
        }

        val progress = if (chart.animateGrowth && clip.durationMs > 0L) {
            ((playheadMs - clip.startMs).toFloat() / (clip.durationMs * 0.45f).coerceAtLeast(600f)).coerceIn(0.08f, 1f)
        } else 1f
        val tf = fontCache[StudioFontOption.HIND_SILIGURI.id] ?: Typeface.DEFAULT
        val paletteCols = chart.paletteTheme.colors

        val boxRect = RectF(-width * 0.42f, -height * 0.36f, width * 0.42f, height * 0.36f)
        canvas.drawRoundRect(
            boxRect,
            width * 0.025f,
            width * 0.025f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = (0xE80A0F1B).toInt() }
        )
        canvas.drawRoundRect(
            boxRect,
            width * 0.025f,
            width * 0.025f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f
                color = (0x8800E5FF).toInt()
            }
        )

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tf
            textSize = width * 0.032f
            color = (0xFFFFB300).toInt()
        }
        canvas.drawText(chart.title, boxRect.left + width * 0.03f, boxRect.top + width * 0.055f, titlePaint)

        val countPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tf
            textSize = width * 0.020f
            color = (0xFF00E5FF).toInt()
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("${activeBars.size} · ${resolvedStyle.labelBn}", boxRect.right - width * 0.03f, boxRect.top + width * 0.052f, countPaint)

        if (activeBars.isEmpty()) {
            val emptyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = tf
                textSize = width * 0.024f
                color = (0xFF94A3B8).toInt()
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("০টি ডেটা · স্টুডিও থেকে ডেটা যোগ করুন", boxRect.centerX(), boxRect.centerY(), emptyPaint)
            return
        }

        val maxVal = activeBars.maxOfOrNull { it.value }?.coerceAtLeast(1f) ?: 100f
        val plotTop = boxRect.top + width * 0.085f
        val plotBottom = boxRect.bottom - width * 0.03f
        val plotLeft = boxRect.left + width * 0.03f
        val plotRight = boxRect.right - width * 0.03f

        fun resolveBarColor(idx: Int, barColorHex: Long): Int =
            if (chart.paletteTheme == ChartPaletteTheme.DEFAULT_MIX) barColorHex.toInt()
            else paletteCols[idx % paletteCols.size].toInt()

        when (resolvedStyle) {
            ChartVisualStyle.HORIZONTAL_BAR -> {
                val rowH = (plotBottom - plotTop) / activeBars.size.toFloat()
                val barH = (rowH * 0.72f).coerceAtLeast(2f)
                val labelW = if (activeBars.size <= 24) (plotRight - plotLeft) * 0.26f else 0f
                val valW = if (activeBars.size <= 18) width * 0.08f else 0f
                val maxBarW = (plotRight - plotLeft - labelW - valW - width * 0.02f).coerceAtLeast(20f)
                val lblPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = tf
                    textSize = minOf(rowH * 0.65f, width * 0.023f)
                    color = Color.WHITE
                }
                val trackBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = (0xFF151C2C).toInt() }
                val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                val valPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = tf
                    textSize = minOf(rowH * 0.62f, width * 0.020f)
                }

                activeBars.forEachIndexed { i, bar ->
                    val rawCol = resolveBarColor(i, bar.colorHex)
                    val y = plotTop + i * rowH
                    if (labelW > 0f) {
                        val txt = if (activeBars.size <= 14) "#${i + 1} ${bar.label.take(9)}" else bar.label.take(10)
                        canvas.drawText(txt, plotLeft, y + barH * 0.85f, lblPaint)
                    }
                    val barStart = plotLeft + labelW
                    canvas.drawRoundRect(RectF(barStart, y, barStart + maxBarW, y + barH), 4f, 4f, trackBgPaint)

                    val frac = ((bar.value / maxVal) * progress).coerceIn(0.03f, 1f)
                    val endX = barStart + maxBarW * frac
                    barPaint.shader = LinearGradient(
                        barStart, y, endX, y,
                        (rawCol and 0x00FFFFFF) or (0xB8 shl 24),
                        rawCol or (0xFF shl 24),
                        Shader.TileMode.CLAMP
                    )
                    canvas.drawRoundRect(RectF(barStart, y, endX, y + barH), 4f, 4f, barPaint)
                    if (valW > 0f) {
                        valPaint.color = rawCol or (0xFF shl 24)
                        canvas.drawText("${bar.value.toInt()}${chart.unitSuffix}", endX + width * 0.012f, y + barH * 0.84f, valPaint)
                    }
                }
            }

            ChartVisualStyle.VERTICAL_BAR -> {
                val colW = (plotRight - plotLeft) / activeBars.size.toFloat()
                val barW = (colW * 0.74f).coerceAtLeast(2f)
                val maxBarH = (plotBottom - plotTop) * 0.86f
                val lblPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = tf
                    textSize = width * 0.017f
                    color = (0xFFCBD5E1).toInt()
                    textAlign = Paint.Align.CENTER
                }
                val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                activeBars.forEachIndexed { i, bar ->
                    val rawCol = resolveBarColor(i, bar.colorHex)
                    val x = plotLeft + i * colW
                    val frac = ((bar.value / maxVal) * progress).coerceIn(0.04f, 1f)
                    val barH = maxBarH * frac
                    val bottomLine = plotBottom - if (activeBars.size <= 12) width * 0.024f else 0f
                    barPaint.shader = LinearGradient(
                        x, bottomLine - barH, x, bottomLine,
                        rawCol or (0xFF shl 24),
                        (rawCol and 0x00FFFFFF) or (0xAA shl 24),
                        Shader.TileMode.CLAMP
                    )
                    canvas.drawRoundRect(RectF(x, bottomLine - barH, x + barW, bottomLine), 4f, 4f, barPaint)
                    if (activeBars.size <= 12) {
                        canvas.drawText(bar.label.take(6), x + barW / 2f, plotBottom, lblPaint)
                    }
                }
            }

            ChartVisualStyle.PIE_CIRCLE,
            ChartVisualStyle.DONUT_RING -> {
                val isDonut = resolvedStyle == ChartVisualStyle.DONUT_RING
                val sumValues = activeBars.sumOf { it.value.toDouble().coerceAtLeast(0.01) }.toFloat().coerceAtLeast(1f)
                val availW = (plotRight - plotLeft) * 0.54f
                val availH = plotBottom - plotTop
                val diameter = minOf(availW, availH) * 0.88f
                val cx = plotLeft + availW * 0.5f
                val cy = (plotTop + plotBottom) * 0.5f
                val ringStroke = diameter * 0.24f
                val oval = if (isDonut) {
                    val r = (diameter - ringStroke) * 0.5f
                    RectF(cx - r, cy - r, cx + r, cy + r)
                } else {
                    val r = diameter * 0.5f
                    RectF(cx - r, cy - r, cx + r, cy + r)
                }

                var startAngle = -90f
                val maxSweepTotal = 360f * progress.coerceIn(0.04f, 1f)
                var sweptSoFar = 0f
                val slicePaint = Paint(Paint.ANTI_ALIAS_FLAG)
                val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = (0xFF0A0F1A).toInt()
                    style = Paint.Style.STROKE
                    strokeWidth = 2.5f
                }

                activeBars.forEachIndexed { idx, bar ->
                    val rawCol = resolveBarColor(idx, bar.colorHex) or (0xFF shl 24)
                    val sliceFull = (bar.value.coerceAtLeast(0.01f) / sumValues) * 360f
                    val sweep = minOf(sliceFull, (maxSweepTotal - sweptSoFar).coerceAtLeast(0f))
                    if (sweep > 0.4f) {
                        if (isDonut) {
                            slicePaint.color = rawCol
                            slicePaint.style = Paint.Style.STROKE
                            slicePaint.strokeWidth = ringStroke
                            slicePaint.strokeCap = Paint.Cap.BUTT
                            canvas.drawArc(oval, startAngle, (sweep - 1.2f).coerceAtLeast(0.5f), false, slicePaint)
                        } else {
                            slicePaint.color = rawCol
                            slicePaint.style = Paint.Style.FILL
                            canvas.drawArc(oval, startAngle, sweep, true, slicePaint)
                            canvas.drawArc(oval, startAngle, sweep, true, dividerPaint)
                        }
                    }
                    startAngle += sliceFull
                    sweptSoFar += sliceFull
                }

                if (isDonut) {
                    val centerValPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        typeface = tf
                        textSize = diameter * 0.14f
                        color = Color.WHITE
                        textAlign = Paint.Align.CENTER
                    }
                    canvas.drawText("${sumValues.toInt()}${chart.unitSuffix}", cx, cy + diameter * 0.05f, centerValPaint)
                }

                val legendX = plotLeft + availW + width * 0.02f
                val legendRight = plotRight
                val legendRows = minOf(activeBars.size, 8)
                val rowSpacing = (plotBottom - plotTop) / legendRows.coerceAtLeast(1)
                val legendPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = tf
                    textSize = minOf(rowSpacing * 0.48f, width * 0.021f)
                    color = Color.WHITE
                }
                val pctPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = tf
                    textSize = legendPaint.textSize
                    textAlign = Paint.Align.RIGHT
                }
                val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                activeBars.take(8).forEachIndexed { idx, bar ->
                    val rawCol = resolveBarColor(idx, bar.colorHex) or (0xFF shl 24)
                    val rowY = plotTop + (idx + 0.55f) * rowSpacing
                    dotPaint.color = rawCol
                    canvas.drawCircle(legendX + width * 0.01f, rowY - legendPaint.textSize * 0.3f, width * 0.007f, dotPaint)
                    canvas.drawText(bar.label.take(12), legendX + width * 0.025f, rowY, legendPaint)
                    val pct = ((bar.value / sumValues) * 100f).toInt()
                    pctPaint.color = rawCol
                    canvas.drawText("$pct%", legendRight, rowY, pctPaint)
                }
            }

            ChartVisualStyle.LINE_GRAPH,
            ChartVisualStyle.AREA_WAVE -> {
                val isWave = resolvedStyle == ChartVisualStyle.AREA_WAVE
                val primaryCol = paletteCols.first().toInt() or (0xFF shl 24)
                val chartBottom = plotBottom - width * 0.026f
                val chartH = (chartBottom - plotTop).coerceAtLeast(20f)
                val chartW = (plotRight - plotLeft).coerceAtLeast(20f)

                val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = (0x1AFFFFFF)
                    strokeWidth = 1.5f
                }
                for (g in 0..3) {
                    val gy = plotTop + chartH * (g / 3f)
                    canvas.drawLine(plotLeft, gy, plotRight, gy, gridPaint)
                }

                val pts = activeBars.mapIndexed { idx, bar ->
                    val px = if (activeBars.size == 1) plotLeft + chartW * 0.5f
                    else plotLeft + (idx.toFloat() / (activeBars.size - 1)) * chartW
                    val normY = (bar.value / maxVal).coerceIn(0.03f, 1f)
                    val py = chartBottom - normY * chartH * 0.88f
                    Pair(px, py)
                }
                val maxDrawX = plotLeft + chartW * progress.coerceIn(0.04f, 1f)
                val visiblePts = pts.filter { it.first <= maxDrawX + 2f }.ifEmpty { listOf(pts.first()) }

                val path = Path()
                if (visiblePts.size == 1) {
                    path.moveTo(plotLeft, visiblePts[0].second)
                    path.lineTo(visiblePts[0].first, visiblePts[0].second)
                } else {
                    path.moveTo(visiblePts[0].first, visiblePts[0].second)
                    for (i in 1 until visiblePts.size) {
                        val prev = visiblePts[i - 1]
                        val cur = visiblePts[i]
                        if (isWave) {
                            val midX = (prev.first + cur.first) * 0.5f
                            path.cubicTo(midX, prev.second, midX, cur.second, cur.first, cur.second)
                        } else {
                            path.lineTo(cur.first, cur.second)
                        }
                    }
                }

                if (isWave) {
                    val fillPath = Path(path).apply {
                        lineTo(visiblePts.last().first, chartBottom)
                        lineTo(visiblePts.first().first, chartBottom)
                        close()
                    }
                    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        shader = LinearGradient(
                            0f, plotTop, 0f, chartBottom,
                            (primaryCol and 0x00FFFFFF) or (0x80 shl 24),
                            (primaryCol and 0x00FFFFFF) or (0x0A shl 24),
                            Shader.TileMode.CLAMP
                        )
                    }
                    canvas.drawPath(fillPath, fillPaint)
                }

                canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE; strokeWidth = 9f
                    strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
                    color = (primaryCol and 0x00FFFFFF) or (0x4D shl 24)
                })
                canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE; strokeWidth = 4f
                    strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
                    color = primaryCol
                })

                val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                val centerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
                visiblePts.forEachIndexed { idx, pt ->
                    dotPaint.color = resolveBarColor(idx, activeBars[idx].colorHex) or (0xFF shl 24)
                    canvas.drawCircle(pt.first, pt.second, 7f, dotPaint)
                    canvas.drawCircle(pt.first, pt.second, 3f, centerDotPaint)
                }

                val lblPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = tf; textSize = width * 0.017f
                    color = (0xFFCBD5E1).toInt(); textAlign = Paint.Align.CENTER
                }
                val step = (activeBars.size / 6).coerceAtLeast(1)
                activeBars.forEachIndexed { idx, bar ->
                    if (idx % step == 0 || idx == activeBars.lastIndex) {
                        canvas.drawText(bar.label.take(6), pts[idx].first, plotBottom, lblPaint)
                    }
                }
            }

            ChartVisualStyle.RADAR_WEB -> {
                val primaryCol = paletteCols.first().toInt() or (0xFF shl 24)
                val cx = (plotLeft + plotRight) * 0.5f
                val cy = (plotTop + plotBottom) * 0.5f
                val radius = minOf(plotRight - plotLeft, plotBottom - plotTop) * 0.40f
                val spokes = activeBars.size.coerceAtLeast(3)

                val webPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE; strokeWidth = 1.5f; color = (0x26FFFFFF)
                }
                for (ring in 1..3) {
                    val r = radius * (ring / 3f)
                    val ringPath = Path()
                    for (i in 0 until spokes) {
                        val ang = (-PI / 2.0) + (2.0 * PI * i / spokes)
                        val px = cx + (cos(ang) * r).toFloat()
                        val py = cy + (sin(ang) * r).toFloat()
                        if (i == 0) ringPath.moveTo(px, py) else ringPath.lineTo(px, py)
                    }
                    ringPath.close()
                    canvas.drawPath(ringPath, webPaint)
                }
                for (i in 0 until spokes) {
                    val ang = (-PI / 2.0) + (2.0 * PI * i / spokes)
                    canvas.drawLine(cx, cy, cx + (cos(ang) * radius).toFloat(), cy + (sin(ang) * radius).toFloat(), webPaint)
                }

                val polyPath = Path()
                val radarPts = (0 until spokes).map { i ->
                    val bar = activeBars[i % activeBars.size]
                    val norm = ((bar.value / maxVal) * progress).coerceIn(0.08f, 1f)
                    val r = radius * norm
                    val ang = (-PI / 2.0) + (2.0 * PI * i / spokes)
                    Pair(cx + (cos(ang) * r).toFloat(), cy + (sin(ang) * r).toFloat())
                }
                radarPts.forEachIndexed { i, pt ->
                    if (i == 0) polyPath.moveTo(pt.first, pt.second) else polyPath.lineTo(pt.first, pt.second)
                }
                polyPath.close()

                canvas.drawPath(polyPath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = (primaryCol and 0x00FFFFFF) or (0x4D shl 24)
                })
                canvas.drawPath(polyPath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = primaryCol; style = Paint.Style.STROKE; strokeWidth = 3.5f
                })

                val lblPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = tf; textSize = width * 0.018f; color = Color.WHITE; textAlign = Paint.Align.CENTER
                }
                val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                activeBars.forEachIndexed { idx, bar ->
                    dotPaint.color = resolveBarColor(idx, bar.colorHex) or (0xFF shl 24)
                    val pt = radarPts[idx % radarPts.size]
                    canvas.drawCircle(pt.first, pt.second, 6f, dotPaint)
                    if (activeBars.size <= 10) {
                        val ang = (-PI / 2.0) + (2.0 * PI * idx / spokes)
                        val lx = cx + (cos(ang) * (radius * 1.15f)).toFloat()
                        val ly = cy + (sin(ang) * (radius * 1.15f)).toFloat()
                        canvas.drawText(bar.label.take(7), lx, ly, lblPaint)
                    }
                }
            }
        }
    }

    fun drawInvestigativeOverlay(
        canvas: Canvas,
        clip: TimelineClip,
        inv: InvestigativeDocConfig,
        width: Int,
        height: Int,
        playheadMs: Long,
        fontCache: Map<String, Typeface>
    ) {
        val progress = if (clip.durationMs > 0L) ((playheadMs - clip.startMs).toFloat() / clip.durationMs).coerceIn(0f, 1f) else 1f
        val tf = fontCache[StudioFontOption.HIND_SILIGURI.id] ?: Typeface.DEFAULT
        val accent = inv.accentHex.toInt()

        when (inv.toolType) {
            InvestigativeToolType.NONE -> Unit
            InvestigativeToolType.EVIDENCE_HIGHLIGHTER, InvestigativeToolType.NEWSPAPER_LOUPE -> {
                val card = RectF(-width * 0.36f, -height * 0.18f, width * 0.36f, height * 0.18f)
                canvas.drawRoundRect(card, 18f, 18f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F5F1E6") })
                val highlightW = (card.width() * 0.84f) * minOf(1f, progress * 1.4f)
                canvas.drawRect(
                    card.left + card.width() * 0.08f,
                    card.centerY() - height * 0.02f,
                    card.left + card.width() * 0.08f + highlightW,
                    card.centerY() + height * 0.05f,
                    Paint().apply { color = (0x88FFB300).toInt() }
                )
                val headPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = tf; textSize = width * 0.032f; color = Color.BLACK }
                canvas.drawText(inv.headline, card.left + card.width() * 0.08f, card.top + height * 0.08f, headPaint)
                val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = tf; textSize = width * 0.023f; color = Color.DKGRAY }
                canvas.drawText(inv.bodyText.take(44), card.left + card.width() * 0.08f, card.centerY() + height * 0.03f, bodyPaint)
                if (inv.toolType == InvestigativeToolType.NEWSPAPER_LOUPE) {
                    val lx = card.left + card.width() * (0.25f + 0.5f * progress)
                    val ly = card.centerY()
                    canvas.drawCircle(lx, ly, width * 0.085f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE; strokeWidth = width * 0.008f; color = accent
                    })
                }
            }
            InvestigativeToolType.REDACTION_CENSOR -> {
                val rect = RectF(-width * 0.28f, -height * 0.06f, width * 0.28f, height * 0.06f)
                canvas.drawRect(rect, Paint().apply { color = Color.BLACK })
            }
            InvestigativeToolType.MAP_RADAR_ROUTE -> {
                val radarStyle = inv.mapStyle
                val gridCol = radarStyle.gridHex.toInt()
                val routeCol = radarStyle.routeHex.toInt()
                val markerCol = radarStyle.markerHex.toInt()
                val box = RectF(-width * 0.42f, -height * 0.30f, width * 0.42f, height * 0.30f)
                canvas.drawRoundRect(box, 20f, 20f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = radarStyle.bgHex.toInt() })
                canvas.drawRoundRect(box, 20f, 20f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE; strokeWidth = 2.5f; color = (gridCol and 0x00FFFFFF) or (0x88 shl 24)
                })

                val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1.2f; color = (gridCol and 0x00FFFFFF) or (0x24 shl 24) }
                for (c in 1 until 9) {
                    val gx = box.left + box.width() * c / 9f
                    canvas.drawLine(gx, box.top, gx, box.bottom, gridPaint)
                }
                for (r in 1 until 6) {
                    val gy = box.top + box.height() * r / 6f
                    canvas.drawLine(box.left, gy, box.right, gy, gridPaint)
                }

                val ax = box.left + box.width() * inv.pointAX.coerceIn(0.06f, 0.94f)
                val ay = box.top + box.height() * inv.pointAY.coerceIn(0.12f, 0.90f)
                val bx = box.left + box.width() * inv.pointBX.coerceIn(0.06f, 0.94f)
                val by = box.top + box.height() * inv.pointBY.coerceIn(0.12f, 0.90f)
                val ctrlX = if (inv.useWaypointC) box.left + box.width() * inv.pointCX.coerceIn(0.06f, 0.94f) else (ax + bx) / 2f
                val ctrlY = if (inv.useWaypointC) box.top + box.height() * inv.pointCY.coerceIn(0.12f, 0.90f) else maxOf(box.top + box.height() * 0.08f, minOf(ay, by) - box.height() * 0.18f)

                val fullPath = Path().apply { moveTo(ax, ay); quadTo(ctrlX, ctrlY, bx, by) }
                canvas.drawPath(fullPath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE; strokeWidth = width * 0.004f
                    color = (routeCol and 0x00FFFFFF) or (0x44 shl 24)
                    pathEffect = DashPathEffect(floatArrayOf(12f, 10f), 0f)
                })

                val animT = minOf(1f, progress * 1.15f).coerceAtLeast(0.02f)
                val activePath = Path()
                var curX = ax; var curY = ay
                for (s in 0..28) {
                    val st = (s / 28f) * animT
                    val omt = 1f - st
                    val px = omt * omt * ax + 2f * omt * st * ctrlX + st * st * bx
                    val py = omt * omt * ay + 2f * omt * st * ctrlY + st * st * by
                    if (s == 0) activePath.moveTo(px, py) else activePath.lineTo(px, py)
                    if (s == 28) { curX = px; curY = py }
                }
                canvas.drawPath(activePath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE; strokeWidth = width * 0.013f; strokeCap = Paint.Cap.ROUND
                    color = (routeCol and 0x00FFFFFF) or (0x48 shl 24)
                })
                canvas.drawPath(activePath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE; strokeWidth = width * 0.0055f; strokeCap = Paint.Cap.ROUND; color = routeCol
                })

                val pulseR = width * (0.018f + 0.022f * ((progress * 5f) % 1f))
                canvas.drawCircle(ax, ay, pulseR, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = (markerCol and 0x00FFFFFF) or (0x44 shl 24) })
                canvas.drawCircle(ax, ay, width * 0.011f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = markerCol })
                canvas.drawCircle(bx, by, pulseR, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = (routeCol and 0x00FFFFFF) or (0x44 shl 24) })
                canvas.drawCircle(bx, by, width * 0.011f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = routeCol })
                if (inv.useWaypointC) {
                    canvas.drawCircle(ctrlX, ctrlY, width * 0.009f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
                }
                canvas.drawCircle(curX, curY, width * 0.012f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })

                val lblP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = tf; textSize = width * 0.022f; color = Color.WHITE }
                canvas.drawText(inv.pointALabel, ax - width * 0.02f, ay + width * 0.035f, lblP)
                canvas.drawText(inv.pointBLabel, bx - width * 0.03f, by - width * 0.022f, lblP)
                val routeTitle = if (inv.useWaypointC && inv.pointCLabel.isNotBlank()) {
                    "${inv.headline} · ${inv.pointALabel} ➔ ${inv.pointCLabel} ➔ ${inv.pointBLabel}"
                } else {
                    "${inv.headline} · ${inv.pointALabel} ➔ ${inv.pointBLabel}"
                }
                canvas.drawText(routeTitle, box.left + width * 0.025f, box.top + width * 0.042f, lblP)
                if (inv.distanceLabel.isNotBlank()) {
                    val distP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = tf; textSize = width * 0.021f; color = routeCol; textAlign = Paint.Align.RIGHT }
                    canvas.drawText(inv.distanceLabel, box.right - width * 0.025f, box.bottom - width * 0.024f, distP)
                }
            }
            InvestigativeToolType.STAT_YEAR_COUNTER -> {
                val currentVal = (inv.counterStart + (inv.counterEnd - inv.counterStart) * progress).toInt()
                val text = "${inv.counterPrefix}$currentVal${inv.counterSuffix}"
                val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = tf; textSize = width * 0.085f; color = accent; textAlign = Paint.Align.CENTER
                }
                canvas.drawText(text, 0f, 0f, p)
            }
        }
    }
}
