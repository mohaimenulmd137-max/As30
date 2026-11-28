package com.example.domain.map

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object UniversalMapRenderer {

    fun renderFrame(
        canvas: Canvas,
        width: Float,
        height: Float,
        config: UniversalMapConfig,
        playheadMs: Long,
        typeface: Typeface = Typeface.DEFAULT,
        isClipFrame: Boolean = false
    ) {
        if (width <= 2f || height <= 2f) return
        val effectiveDur = config.durationMs.coerceAtLeast(1000L)
        val rawNorm = ((playheadMs.toFloat() * config.speedMultiplier.coerceIn(0.25f, 3.0f)) / effectiveDur.toFloat())
            .coerceIn(0f, 1f)

        val camera = WorldCartographyData.evaluateCameraAt(config, rawNorm)
        val theme = config.themeStyle

        canvas.save()
        if (!isClipFrame) {
            canvas.clipRect(0f, 0f, width, height)
        } else {
            canvas.clipRect(-width / 2f, -height / 2f, width / 2f, height / 2f)
            canvas.translate(-width / 2f, -height / 2f)
        }

        // 1. Background Ocean / Space Backdrop
        drawBackdrop(canvas, width, height, config, camera, theme)

        // Clip globe interior if GLOBE_3D
        val globeCenterX = width * 0.5f
        val globeCenterY = height * 0.5f
        val globeRadius = minOf(width, height) * 0.38f * (0.72f + (camera.zoom - 1f) * 0.42f).coerceIn(0.65f, 5.2f)

        canvas.save()
        if (config.projectionMode == MapProjectionMode.GLOBE_3D) {
            val spherePath = Path().apply { addCircle(globeCenterX, globeCenterY, globeRadius, Path.Direction.CW) }
            canvas.clipPath(spherePath)
            // Illuminated sphere ocean gradient
            val oceanShader = RadialGradient(
                globeCenterX - globeRadius * 0.28f,
                globeCenterY - globeRadius * 0.28f,
                globeRadius * 1.45f,
                intArrayOf(theme.oceanHex.toInt(), theme.oceanDeepHex.toInt(), 0xFF01040A.toInt()),
                floatArrayOf(0f, 0.72f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(globeCenterX, globeCenterY, globeRadius, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = oceanShader })
        }

        // 2. Graticule Lat/Lon Grid
        if (config.showGraticuleGrid) {
            drawGraticule(canvas, width, height, config.projectionMode, camera, theme)
        }

        // 3. Base World Landmasses & Coastlines
        drawContinents(canvas, width, height, config.projectionMode, camera, theme, config.showCountryBorders)

        // 4. Country / Empire Highlight Regions (with Animated Border Draw & Territory Expansion)
        drawRegions(canvas, width, height, config, camera, rawNorm, typeface)

        // 5. Base Country Name Labels (if enabled & zoomed cleanly)
        if (config.showCountryNames) {
            drawCountryLabels(canvas, width, height, config, camera, theme, typeface)
        }

        // 6. Heatmap Layer Pass (drawn under routes/markers so labels remain sharp)
        drawHeatmaps(canvas, width, height, config, camera, rawNorm)

        // 7. Animated Routes, War Frontlines, Invasions, Trade, Air & Naval Paths
        drawRoutes(canvas, width, height, config, camera, rawNorm)

        if (config.projectionMode == MapProjectionMode.GLOBE_3D) {
            // Subtle inner limb shading on 3D Globe
            val limbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    globeCenterX, globeCenterY, globeRadius,
                    intArrayOf(0x00000000, 0x00000000, 0x55000000),
                    floatArrayOf(0f, 0.78f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawCircle(globeCenterX, globeCenterY, globeRadius, limbPaint)
        }
        canvas.restore() // end globe clip if 3D

        // 3D Globe Atmosphere Outer Rim Glow
        if (config.projectionMode == MapProjectionMode.GLOBE_3D) {
            val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = minOf(width, height) * 0.014f
                color = (theme.gridHex.toInt() and 0x00FFFFFF) or (0x55 shl 24)
            }
            canvas.drawCircle(globeCenterX, globeCenterY, globeRadius + 1.5f, haloPaint)
        }

        // 8. Markers, Capitals, Hazards (Cyclone, Flood, Earthquake, Fire) & Text Callouts
        drawMarkersAndHazards(canvas, width, height, config, camera, rawNorm, playheadMs, theme, typeface)
        drawTextLabels(canvas, width, height, config, camera, rawNorm, typeface)

        // 9. Documentary HUD (Coordinates, Compass Rose, Scale Bar, Title Plate)
        drawMapHud(canvas, width, height, config, camera, theme, typeface)

        canvas.restore()
    }

    private fun drawBackdrop(
        canvas: Canvas,
        width: Float,
        height: Float,
        config: UniversalMapConfig,
        camera: EvaluatedMapCamera,
        theme: MapThemeStyle
    ) {
        if (config.projectionMode == MapProjectionMode.GLOBE_3D) {
            canvas.drawColor(0xFF020408.toInt())
            val spacePaint = Paint().apply {
                shader = RadialGradient(
                    width * 0.5f, height * 0.5f, maxOf(width, height) * 0.75f,
                    intArrayOf(0xFF0B1628.toInt(), 0xFF040812.toInt(), 0xFF020408.toInt()),
                    floatArrayOf(0f, 0.65f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, width, height, spacePaint)
            // Subtle starfield drifting gently with lon rotation
            val starPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val lonShift = (camera.centerLon / 360f) * width * 0.25f
            for (i in 0 until 45) {
                val sx = (((i * 73.1f + 19f) % 100f) / 100f * width - lonShift + width * 2f) % width
                val sy = (((i * 41.7f + 11f) % 100f) / 100f * height)
                val alpha = 70 + (i % 5) * 32
                starPaint.color = Color.argb(alpha, 210, 232, 255)
                canvas.drawCircle(sx, sy, 0.8f + (i % 3) * 0.5f, starPaint)
            }
        } else {
            canvas.drawColor(theme.oceanDeepHex.toInt())
            val oceanPaint = Paint().apply {
                shader = LinearGradient(
                    0f, 0f, 0f, height,
                    theme.oceanHex.toInt(), theme.oceanDeepHex.toInt(),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, width, height, oceanPaint)
        }
    }

    private fun drawGraticule(
        canvas: Canvas,
        width: Float,
        height: Float,
        mode: MapProjectionMode,
        camera: EvaluatedMapCamera,
        theme: MapThemeStyle
    ) {
        val gridCol = (theme.gridHex.toInt() and 0x00FFFFFF) or (0x24 shl 24)
        val equatorCol = (theme.gridHex.toInt() and 0x00FFFFFF) or (0x48 shl 24)
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.1f
            color = gridCol
        }
        val stepDeg = if (camera.zoom > 4.0f) 10 else if (camera.zoom > 2.0f) 20 else 30

        // Parallels (Latitude lines)
        var lat = -60
        while (lat <= 80) {
            val path = Path()
            var started = false
            var lon = -180
            while (lon <= 180) {
                val p = WorldCartographyData.projectLatLon(lat.toFloat(), lon.toFloat(), camera, mode, width, height)
                if (p.visible) {
                    if (!started) {
                        path.moveTo(p.x, p.y); started = true
                    } else {
                        path.lineTo(p.x, p.y)
                    }
                } else {
                    started = false
                }
                lon += 10
            }
            gridPaint.color = if (lat == 0) equatorCol else gridCol
            gridPaint.strokeWidth = if (lat == 0) 1.6f else 1.0f
            canvas.drawPath(path, gridPaint)
            lat += stepDeg
        }

        // Meridians (Longitude lines)
        var lon = -180
        while (lon < 180) {
            val path = Path()
            var started = false
            var pLat = -80
            while (pLat <= 80) {
                val p = WorldCartographyData.projectLatLon(pLat.toFloat(), lon.toFloat(), camera, mode, width, height)
                if (p.visible) {
                    if (!started) {
                        path.moveTo(p.x, p.y); started = true
                    } else {
                        path.lineTo(p.x, p.y)
                    }
                } else {
                    started = false
                }
                pLat += 8
            }
            gridPaint.color = if (lon == 0) equatorCol else gridCol
            gridPaint.strokeWidth = if (lon == 0) 1.6f else 1.0f
            canvas.drawPath(path, gridPaint)
            lon += stepDeg
        }
    }

    private fun drawContinents(
        canvas: Canvas,
        width: Float,
        height: Float,
        mode: MapProjectionMode,
        camera: EvaluatedMapCamera,
        theme: MapThemeStyle,
        showBorders: Boolean
    ) {
        val landPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = theme.landHex.toInt()
        }
        val coastlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.6f
            strokeJoin = Paint.Join.ROUND
            color = theme.landBorderHex.toInt()
        }

        val lonOffsets = if (mode == MapProjectionMode.WORLD_2D) floatArrayOf(-360f, 0f, 360f) else floatArrayOf(0f)

        WorldCartographyData.continentPolygons.forEach { ring ->
            for (lonOffset in lonOffsets) {
                val path = buildProjectedRingPath(ring, camera, mode, width, height, lonOffset)
                if (path != null) {
                    canvas.drawPath(path, landPaint)
                    if (showBorders) {
                        canvas.drawPath(path, coastlinePaint)
                    }
                }
            }
        }

        // Faint baseline borders of all selectable modern nations for geographic realism
        if (showBorders) {
            val subBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.0f
                color = (theme.landBorderHex.toInt() and 0x00FFFFFF) or (0x66 shl 24)
            }
            WorldCartographyData.selectableRegions.filter { !it.isHistorical }.forEach { reg ->
                reg.rings.forEach { ring ->
                    val path = buildProjectedRingPath(ring, camera, mode, width, height, 0f, unwrap = true)
                    if (path != null) canvas.drawPath(path, subBorderPaint)
                }
            }
        }
    }

    private fun drawRegions(
        canvas: Canvas,
        width: Float,
        height: Float,
        config: UniversalMapConfig,
        camera: EvaluatedMapCamera,
        normTime: Float,
        typeface: Typeface
    ) {
        val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = Typeface.create(typeface, Typeface.BOLD)
            textSize = minOf(width, height) * 0.028f
            textAlign = Paint.Align.CENTER
            setShadowLayer(4f, 0f, 1.5f, Color.BLACK)
        }

        config.regions.filter { it.isVisible }.forEach { regionLayer ->
            val def = WorldCartographyData.selectableRegions.firstOrNull { it.id == regionLayer.regionId } ?: return@forEach
            val span = (regionLayer.endFrac - regionLayer.startFrac).coerceAtLeast(0.05f)
            val localT = ((normTime - regionLayer.startFrac) / span).coerceIn(0f, 1f)
            if (normTime < regionLayer.startFrac || normTime > regionLayer.endFrac + 0.02f) return@forEach

            val reveal = (localT * 2.2f).coerceIn(0f, 1f)
            val fillAlpha = (regionLayer.fillOpacity.coerceIn(0.08f, 0.9f) * reveal * 255).toInt().coerceIn(0, 235)
            val fillCol = (regionLayer.fillColorHex.toInt() and 0x00FFFFFF) or (fillAlpha shl 24)
            val borderCol = regionLayer.borderColorHex.toInt()

            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = fillCol
            }
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 3.2f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                color = borderCol
            }
            val glowBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 8.5f
                strokeCap = Paint.Cap.ROUND
                color = (borderCol and 0x00FFFFFF) or (0x44 shl 24)
            }

            def.rings.forEach { ring ->
                val scaledRing = if (regionLayer.territoryExpansion) {
                    val expScale = 0.25f + 0.85f * localT
                    ring.map { (lat, lon) ->
                        (def.centerLat + (lat - def.centerLat) * expScale) to
                            (def.centerLon + (lon - def.centerLon) * expScale)
                    }
                } else ring

                val path = buildProjectedRingPath(scaledRing, camera, config.projectionMode, width, height, 0f, unwrap = true)
                if (path != null) {
                    canvas.drawPath(path, fillPaint)
                    if (regionLayer.borderDrawAnimated && reveal < 0.99f) {
                        val pm = PathMeasure(path, false)
                        val partial = Path()
                        pm.getSegment(0f, pm.length * reveal, partial, true)
                        canvas.drawPath(partial, glowBorderPaint)
                        canvas.drawPath(partial, borderPaint)
                    } else {
                        canvas.drawPath(path, glowBorderPaint)
                        canvas.drawPath(path, borderPaint)
                    }
                }
            }

            val centerPt = WorldCartographyData.projectLatLon(def.centerLat, def.centerLon, camera, config.projectionMode, width, height)
            if (centerPt.visible) {
                val lbl = regionLayer.customLabel.ifBlank { def.nameBn }
                labelPaint.color = borderCol
                labelPaint.alpha = (reveal * 255).toInt().coerceIn(0, 255)
                canvas.drawText(lbl, centerPt.x, centerPt.y, labelPaint)
            }
        }
    }

    private fun drawCountryLabels(
        canvas: Canvas,
        width: Float,
        height: Float,
        config: UniversalMapConfig,
        camera: EvaluatedMapCamera,
        theme: MapThemeStyle,
        typeface: Typeface
    ) {
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = minOf(width, height) * 0.021f
            color = (theme.textHex.toInt() and 0x00FFFFFF) or (0xAA shl 24)
            textAlign = Paint.Align.CENTER
            setShadowLayer(3f, 0f, 1f, Color.BLACK)
        }
        val highlightedIds = config.regions.filter { it.isVisible }.map { it.regionId }.toSet()
        WorldCartographyData.selectableRegions
            .filter { !it.isHistorical && it.id !in highlightedIds }
            .forEach { reg ->
                val p = WorldCartographyData.projectLatLon(reg.centerLat, reg.centerLon, camera, config.projectionMode, width, height)
                if (p.visible && p.x in 24f..(width - 24f) && p.y in 24f..(height - 24f)) {
                    canvas.drawText(reg.nameBn, p.x, p.y, textPaint)
                }
            }
    }

    private fun drawHeatmaps(
        canvas: Canvas,
        width: Float,
        height: Float,
        config: UniversalMapConfig,
        camera: EvaluatedMapCamera,
        normTime: Float
    ) {
        config.markers
            .filter { it.isVisible && it.markerType == MapMarkerType.HEATMAP_NODE }
            .forEach { marker ->
                if (normTime < marker.startFrac || normTime > marker.endFrac) return@forEach
                val p = WorldCartographyData.projectLatLon(marker.lat, marker.lon, camera, config.projectionMode, width, height)
                if (!p.visible) return@forEach
                val pulse = if (marker.pulseAnimated) 0.9f + 0.16f * sin(normTime * PI * 6).toFloat() else 1f
                val r = minOf(width, height) * 0.11f * marker.sizeScale * (0.6f + camera.zoom * 0.14f).coerceAtMost(1.8f) * pulse
                val baseRgb = marker.colorHex.toInt() and 0x00FFFFFF
                val heatPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(
                        p.x, p.y, r.coerceAtLeast(8f),
                        intArrayOf((0xCC shl 24) or baseRgb, (0x66 shl 24) or 0xFFB300, (0x22 shl 24) or 0x00E5FF, 0x00000000),
                        floatArrayOf(0f, 0.35f, 0.72f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawCircle(p.x, p.y, r, heatPaint)
            }
    }

    private fun drawRoutes(
        canvas: Canvas,
        width: Float,
        height: Float,
        config: UniversalMapConfig,
        camera: EvaluatedMapCamera,
        normTime: Float
    ) {
        config.routes.filter { it.isVisible && it.waypoints.size >= 2 }.forEach { route ->
            if (normTime < route.startFrac) return@forEach
            val span = (route.endFrac - route.startFrac).coerceAtLeast(0.05f)
            val progress = ((normTime - route.startFrac) / span).coerceIn(0.01f, 1f)

            // Build subdivided geodesic coordinates
            val denseLatLons = mutableListOf<MapLatLon>()
            for (i in 0 until route.waypoints.lastIndex) {
                val seg = WorldCartographyData.interpolateGeodesic(route.waypoints[i], route.waypoints[i + 1], steps = 18)
                if (i == 0) denseLatLons.addAll(seg) else denseLatLons.addAll(seg.drop(1))
            }

            // Project visible points
            val projected = denseLatLons.map { pt ->
                WorldCartographyData.projectLatLon(pt.lat, pt.lon, camera, config.projectionMode, width, height)
            }

            val routeCol = route.colorHex.toInt()
            val strokeScale = (minOf(width, height) / 420f).coerceIn(0.75f, 3.2f)
            val strokePx = route.strokeWidthDp * strokeScale

            // Faint full preview guide track
            val guidePath = Path()
            var guideStarted = false
            projected.forEach { p ->
                if (p.visible) {
                    if (!guideStarted) { guidePath.moveTo(p.x, p.y); guideStarted = true }
                    else guidePath.lineTo(p.x, p.y)
                } else guideStarted = false
            }
            val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = strokePx * 0.55f
                color = (routeCol and 0x00FFFFFF) or (0x3A shl 24)
                pathEffect = DashPathEffect(floatArrayOf(10f * strokeScale, 8f * strokeScale), 0f)
            }
            canvas.drawPath(guidePath, guidePaint)

            // Active animated segment up to progress
            val maxIdx = ((projected.lastIndex) * progress).toInt().coerceIn(0, projected.lastIndex)
            val fracTail = ((projected.lastIndex) * progress) - maxIdx
            val activePts = mutableListOf<ProjectedScreenPoint>()
            for (i in 0..maxIdx) {
                activePts.add(projected[i])
            }
            if (maxIdx < projected.lastIndex && projected[maxIdx].visible && projected[maxIdx + 1].visible) {
                val p1 = projected[maxIdx]
                val p2 = projected[maxIdx + 1]
                activePts.add(
                    ProjectedScreenPoint(
                        x = p1.x + (p2.x - p1.x) * fracTail,
                        y = p1.y + (p2.y - p1.y) * fracTail,
                        visible = true
                    )
                )
            }

            val activePath = Path()
            var activeStarted = false
            activePts.forEach { p ->
                if (p.visible) {
                    if (!activeStarted) { activePath.moveTo(p.x, p.y); activeStarted = true }
                    else activePath.lineTo(p.x, p.y)
                } else activeStarted = false
            }

            // Glow understroke
            canvas.drawPath(activePath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = strokePx * 2.4f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                color = (routeCol and 0x00FFFFFF) or (0x45 shl 24)
            })

            val mainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = strokePx
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                color = routeCol
            }
            when (route.routeStyle) {
                MapRouteStyle.DASHED_INTEL, MapRouteStyle.RETREAT_DASH -> {
                    mainPaint.pathEffect = DashPathEffect(floatArrayOf(14f * strokeScale, 9f * strokeScale), -normTime * 60f)
                }
                MapRouteStyle.NAVAL_WAKE -> {
                    mainPaint.pathEffect = DashPathEffect(floatArrayOf(8f * strokeScale, 8f * strokeScale), -normTime * 45f)
                }
                MapRouteStyle.MIGRATION_FLOW -> {
                    mainPaint.pathEffect = DashPathEffect(floatArrayOf(18f * strokeScale, 6f * strokeScale), -normTime * 55f)
                }
                MapRouteStyle.FRONTLINE_CLASH -> {
                    canvas.drawPath(activePath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = strokePx * 1.4f
                        color = 0xAAFF4D6D.toInt()
                        pathEffect = DashPathEffect(floatArrayOf(6f * strokeScale, 12f * strokeScale), 0f)
                    })
                }
                else -> Unit
            }
            canvas.drawPath(activePath, mainPaint)

            // Tip Icon at leading edge
            val visibleTail = activePts.filter { it.visible }
            if (visibleTail.size >= 2 && route.tipIcon != MapRouteTipIcon.NONE) {
                val tip = visibleTail.last()
                val prev = visibleTail[visibleTail.size - 2]
                val angleDeg = Math.toDegrees(atan2((tip.y - prev.y).toDouble(), (tip.x - prev.x).toDouble())).toFloat()
                drawRouteTipIcon(canvas, tip.x, tip.y, angleDeg, route.tipIcon, routeCol, strokeScale)
            }
        }
    }

    private fun drawRouteTipIcon(
        canvas: Canvas,
        x: Float,
        y: Float,
        angleDeg: Float,
        icon: MapRouteTipIcon,
        colorInt: Int,
        scale: Float
    ) {
        canvas.save()
        canvas.translate(x, y)
        val s = 11f * scale
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorInt }
        val whiteOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f * scale
            color = Color.WHITE
        }
        when (icon) {
            MapRouteTipIcon.NONE -> Unit
            MapRouteTipIcon.PULSE_ORB -> {
                canvas.drawCircle(0f, 0f, s * 1.15f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = (colorInt and 0x00FFFFFF) or (0x4D shl 24)
                })
                canvas.drawCircle(0f, 0f, s * 0.58f, fillPaint)
                canvas.drawCircle(0f, 0f, s * 0.58f, whiteOutline)
            }
            MapRouteTipIcon.ARROW -> {
                canvas.rotate(angleDeg)
                val p = Path().apply {
                    moveTo(s * 1.1f, 0f)
                    lineTo(-s * 0.7f, -s * 0.72f)
                    lineTo(-s * 0.35f, 0f)
                    lineTo(-s * 0.7f, s * 0.72f)
                    close()
                }
                canvas.drawPath(p, fillPaint)
                canvas.drawPath(p, whiteOutline)
            }
            MapRouteTipIcon.PLANE -> {
                canvas.rotate(angleDeg)
                val plane = Path().apply {
                    moveTo(s * 1.25f, 0f)
                    lineTo(s * 0.25f, -s * 0.26f)
                    lineTo(-s * 0.25f, -s * 1.05f)
                    lineTo(-s * 0.55f, -s * 1.05f)
                    lineTo(-s * 0.22f, -s * 0.22f)
                    lineTo(-s * 0.9f, -s * 0.16f)
                    lineTo(-s * 1.1f, -s * 0.45f)
                    lineTo(-s * 1.2f, 0f)
                    lineTo(-s * 1.1f, s * 0.45f)
                    lineTo(-s * 0.9f, s * 0.16f)
                    lineTo(-s * 0.22f, s * 0.22f)
                    lineTo(-s * 0.55f, s * 1.05f)
                    lineTo(-s * 0.25f, s * 1.05f)
                    lineTo(s * 0.25f, s * 0.26f)
                    close()
                }
                canvas.drawPath(plane, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
                canvas.drawPath(plane, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE; strokeWidth = 1.4f * scale; color = colorInt
                })
            }
            MapRouteTipIcon.SHIP -> {
                canvas.rotate(angleDeg)
                val hull = Path().apply {
                    moveTo(s * 1.15f, 0f)
                    lineTo(s * 0.4f, -s * 0.5f)
                    lineTo(-s * 0.9f, -s * 0.45f)
                    lineTo(-s * 0.9f, s * 0.45f)
                    lineTo(s * 0.4f, s * 0.5f)
                    close()
                }
                canvas.drawPath(hull, fillPaint)
                canvas.drawPath(hull, whiteOutline)
            }
            MapRouteTipIcon.ARMY_UNIT -> {
                canvas.rotate(angleDeg)
                val tank = RectF(-s * 0.7f, -s * 0.52f, s * 0.55f, s * 0.52f)
                canvas.drawRoundRect(tank, 3f * scale, 3f * scale, fillPaint)
                canvas.drawRoundRect(tank, 3f * scale, 3f * scale, whiteOutline)
                canvas.drawLine(0f, 0f, s * 1.25f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE; strokeWidth = 2.6f * scale; strokeCap = Paint.Cap.ROUND
                })
            }
            MapRouteTipIcon.VEHICLE -> {
                canvas.rotate(angleDeg)
                val body = RectF(-s * 0.7f, -s * 0.42f, s * 0.8f, s * 0.42f)
                canvas.drawRoundRect(body, 4f * scale, 4f * scale, fillPaint)
                canvas.drawRoundRect(body, 4f * scale, 4f * scale, whiteOutline)
            }
        }
        canvas.restore()
    }

    private fun drawMarkersAndHazards(
        canvas: Canvas,
        width: Float,
        height: Float,
        config: UniversalMapConfig,
        camera: EvaluatedMapCamera,
        normTime: Float,
        playheadMs: Long,
        theme: MapThemeStyle,
        typeface: Typeface
    ) {
        val unitScale = (minOf(width, height) / 420f).coerceIn(0.75f, 3.0f)
        val lblPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = Typeface.create(typeface, Typeface.BOLD)
            textSize = 11.5f * unitScale
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            setShadowLayer(4f, 0f, 1.5f, Color.BLACK)
        }
        val coordPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = 8.5f * unitScale
            color = theme.accentHex.toInt()
            textAlign = Paint.Align.CENTER
        }

        config.markers.filter { it.isVisible }.forEach { marker ->
            if (normTime < marker.startFrac || normTime > marker.endFrac) return@forEach
            val p = WorldCartographyData.projectLatLon(marker.lat, marker.lon, camera, config.projectionMode, width, height)
            if (!p.visible) return@forEach

            val mScale = unitScale * marker.sizeScale.coerceIn(0.5f, 2.5f)
            val mCol = marker.colorHex.toInt()
            val pulsePhase = ((playheadMs % 1500L) / 1500f)

            canvas.save()
            canvas.translate(p.x, p.y)

            when (marker.markerType) {
                MapMarkerType.CYCLONE -> {
                    val rot = (playheadMs * 0.25f) % 360f
                    canvas.save()
                    canvas.rotate(rot)
                    val armPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = 3.2f * mScale
                        strokeCap = Paint.Cap.ROUND
                        color = mCol
                    }
                    for (arm in 0 until 3) {
                        canvas.rotate(120f)
                        val arcRect = RectF(-4f * mScale, -18f * mScale, 28f * mScale, 14f * mScale)
                        canvas.drawArc(arcRect, -20f, 150f, false, armPaint)
                    }
                    canvas.restore()
                    canvas.drawCircle(0f, 0f, 4.5f * mScale, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
                }
                MapMarkerType.FLOOD -> {
                    val r1 = (10f + 16f * pulsePhase) * mScale
                    val r2 = (10f + 16f * ((pulsePhase + 0.5f) % 1f)) * mScale
                    canvas.drawCircle(0f, 0f, r1, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = (mCol and 0x00FFFFFF) or (((1f - pulsePhase) * 160).toInt() shl 24)
                    })
                    canvas.drawCircle(0f, 0f, r2, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE; strokeWidth = 2.5f * mScale; color = mCol
                    })
                    canvas.drawCircle(0f, 0f, 6f * mScale, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
                }
                MapMarkerType.EARTHQUAKE -> {
                    for (ringIdx in 0 until 3) {
                        val ph = (pulsePhase + ringIdx * 0.33f) % 1f
                        val rr = (6f + ph * 28f) * mScale
                        val al = ((1f - ph) * 230).toInt().coerceIn(0, 255)
                        canvas.drawCircle(0f, 0f, rr, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            style = Paint.Style.STROKE
                            strokeWidth = 2.8f * mScale
                            color = (mCol and 0x00FFFFFF) or (al shl 24)
                        })
                    }
                    canvas.drawCircle(0f, 0f, 6f * mScale, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF3B30.toInt() })
                }
                MapMarkerType.FIRE -> {
                    val flicker = 0.9f + 0.22f * sin(playheadMs * 0.015f)
                    val outerFlame = Path().apply {
                        moveTo(0f, -18f * mScale * flicker)
                        quadTo(13f * mScale, -3f * mScale, 8f * mScale, 8f * mScale)
                        quadTo(0f, 13f * mScale, -8f * mScale, 8f * mScale)
                        quadTo(-13f * mScale, -3f * mScale, 0f, -18f * mScale * flicker)
                        close()
                    }
                    canvas.drawPath(outerFlame, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF4500.toInt() })
                    val innerFlame = Path().apply {
                        moveTo(0f, -10f * mScale * flicker)
                        quadTo(6f * mScale, 0f, 4f * mScale, 6f * mScale)
                        quadTo(0f, 9f * mScale, -4f * mScale, 6f * mScale)
                        quadTo(-6f * mScale, 0f, 0f, -10f * mScale * flicker)
                        close()
                    }
                    canvas.drawPath(innerFlame, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFD54F.toInt() })
                }
                MapMarkerType.BATTLE_CLASH -> {
                    if (marker.pulseAnimated) {
                        canvas.drawCircle(0f, 0f, (10f + 12f * pulsePhase) * mScale, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            style = Paint.Style.STROKE; strokeWidth = 2f * mScale
                            color = (mCol and 0x00FFFFFF) or (((1f - pulsePhase) * 200).toInt() shl 24)
                        })
                    }
                    val swordP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = mCol; strokeWidth = 3f * mScale; strokeCap = Paint.Cap.ROUND
                    }
                    val d = 9f * mScale
                    canvas.drawLine(-d, -d, d, d, swordP)
                    canvas.drawLine(d, -d, -d, d, swordP)
                    canvas.drawCircle(0f, 0f, 3.5f * mScale, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
                }
                MapMarkerType.CAPITAL_STAR -> {
                    val star = buildStarPath(outerR = 11f * mScale, innerR = 4.6f * mScale)
                    canvas.drawCircle(0f, 0f, 15f * mScale, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = (mCol and 0x00FFFFFF) or (0x40 shl 24)
                    })
                    canvas.drawPath(star, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = mCol })
                    canvas.drawPath(star, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE; strokeWidth = 1.4f * mScale; color = Color.WHITE
                    })
                }
                MapMarkerType.HEATMAP_NODE -> {
                    canvas.drawCircle(0f, 0f, 4.5f * mScale, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
                }
                MapMarkerType.ARMY_BASE -> {
                    val rect = RectF(-9f * mScale, -7f * mScale, 9f * mScale, 7f * mScale)
                    canvas.drawRect(rect, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = mCol })
                    canvas.drawRect(rect, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE; strokeWidth = 1.8f * mScale; color = Color.WHITE
                    })
                }
                MapMarkerType.FLAG -> {
                    canvas.drawLine(-4f * mScale, 10f * mScale, -4f * mScale, -12f * mScale, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.WHITE; strokeWidth = 2.2f * mScale
                    })
                    val flag = Path().apply {
                        moveTo(-4f * mScale, -12f * mScale)
                        lineTo(11f * mScale, -7f * mScale)
                        lineTo(-4f * mScale, -2f * mScale)
                        close()
                    }
                    canvas.drawPath(flag, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = mCol })
                }
                MapMarkerType.PIN, MapMarkerType.CITY_DOT, MapMarkerType.PORT_ANCHOR, MapMarkerType.AIRPORT -> {
                    if (marker.pulseAnimated) {
                        val pr = (8f + 13f * pulsePhase) * mScale
                        val pa = ((1f - pulsePhase) * 190).toInt().coerceIn(0, 255)
                        canvas.drawCircle(0f, 0f, pr, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            style = Paint.Style.STROKE; strokeWidth = 2f * mScale
                            color = (mCol and 0x00FFFFFF) or (pa shl 24)
                        })
                    }
                    canvas.drawCircle(0f, 0f, 6.8f * mScale, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = mCol })
                    canvas.drawCircle(0f, 0f, 6.8f * mScale, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE; strokeWidth = 1.8f * mScale; color = Color.WHITE
                    })
                }
            }

            if (marker.label.isNotBlank()) {
                val lblY = -16f * mScale
                canvas.drawText(marker.label, 0f, lblY, lblPaint)
                if (marker.subtitle.isNotBlank()) {
                    canvas.drawText(marker.subtitle, 0f, 19f * mScale, coordPaint)
                } else if (marker.showCoordinates) {
                    canvas.drawText("${"%.1f".format(marker.lat)}°, ${"%.1f".format(marker.lon)}°", 0f, 18f * mScale, coordPaint)
                }
            }
            canvas.restore()
        }
    }

    private fun drawTextLabels(
        canvas: Canvas,
        width: Float,
        height: Float,
        config: UniversalMapConfig,
        camera: EvaluatedMapCamera,
        normTime: Float,
        typeface: Typeface
    ) {
        val unitScale = (minOf(width, height) / 420f).coerceIn(0.75f, 3.0f)
        config.textLabels.filter { it.isVisible && it.text.isNotBlank() }.forEach { lbl ->
            if (normTime < lbl.startFrac || normTime > lbl.endFrac) return@forEach
            val p = WorldCartographyData.projectLatLon(lbl.lat, lbl.lon, camera, config.projectionMode, width, height)
            if (!p.visible) return@forEach

            val tPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                this.typeface = Typeface.create(typeface, Typeface.BOLD)
                textSize = 12.5f * unitScale * lbl.fontSizeScale.coerceIn(0.6f, 2.2f)
                color = lbl.colorHex.toInt()
                textAlign = Paint.Align.CENTER
            }
            val textW = tPaint.measureText(lbl.text)
            val padX = 10f * unitScale
            val padY = 6f * unitScale
            val boxY = if (lbl.showArrowPointer) p.y - 24f * unitScale else p.y
            val rect = RectF(
                p.x - textW * 0.5f - padX,
                boxY - tPaint.textSize - padY,
                p.x + textW * 0.5f + padX,
                boxY + padY
            )
            canvas.drawRoundRect(rect, 8f * unitScale, 8f * unitScale, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = lbl.bgColorHex.toInt()
            })
            canvas.drawRoundRect(rect, 8f * unitScale, 8f * unitScale, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 1.5f * unitScale; color = lbl.colorHex.toInt()
            })
            if (lbl.showArrowPointer) {
                canvas.drawLine(p.x, rect.bottom, p.x, p.y, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = lbl.colorHex.toInt(); strokeWidth = 2f * unitScale
                })
                canvas.drawCircle(p.x, p.y, 3.5f * unitScale, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = lbl.colorHex.toInt() })
            }
            canvas.drawText(lbl.text, p.x, boxY - padY * 0.3f, tPaint)
        }
    }

    private fun drawMapHud(
        canvas: Canvas,
        width: Float,
        height: Float,
        config: UniversalMapConfig,
        camera: EvaluatedMapCamera,
        theme: MapThemeStyle,
        typeface: Typeface
    ) {
        val unitScale = (minOf(width, height) / 420f).coerceIn(0.75f, 3.0f)

        // Top Title / Subtitle documentary plate
        if (config.title.isNotBlank()) {
            val titleP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                this.typeface = Typeface.create(typeface, Typeface.BOLD)
                textSize = 13.5f * unitScale
                color = theme.accentHex.toInt()
            }
            val subP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                this.typeface = typeface
                textSize = 9.8f * unitScale
                color = theme.textHex.toInt()
            }
            val boxW = maxOf(titleP.measureText(config.title), subP.measureText(config.subtitle)) + 20f * unitScale
            val boxH = if (config.subtitle.isNotBlank()) 36f * unitScale else 24f * unitScale
            val rect = RectF(10f * unitScale, 10f * unitScale, (10f * unitScale + boxW).coerceAtMost(width * 0.72f), 10f * unitScale + boxH)
            canvas.drawRoundRect(rect, 8f * unitScale, 8f * unitScale, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xBB070B14.toInt()
            })
            canvas.drawRoundRect(rect, 8f * unitScale, 8f * unitScale, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 1.2f * unitScale; color = theme.accentHex.toInt()
            })
            canvas.drawText(config.title, rect.left + 9f * unitScale, rect.top + 15f * unitScale, titleP)
            if (config.subtitle.isNotBlank()) {
                canvas.drawText(config.subtitle, rect.left + 9f * unitScale, rect.top + 29f * unitScale, subP)
            }
        }

        // Compass Rose (Top-Right)
        if (config.showCompassRose) {
            val cx = width - 26f * unitScale
            val cy = 26f * unitScale
            val cr = 14f * unitScale
            canvas.save()
            canvas.translate(cx, cy)
            canvas.rotate(camera.rotationDeg)
            canvas.drawCircle(0f, 0f, cr, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xAA070B14.toInt() })
            canvas.drawCircle(0f, 0f, cr, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 1.4f * unitScale; color = theme.gridHex.toInt()
            })
            val northNeedle = Path().apply { moveTo(0f, -cr * 0.78f); lineTo(cr * 0.28f, 0f); lineTo(-cr * 0.28f, 0f); close() }
            canvas.drawPath(northNeedle, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF4D6D.toInt() })
            val southNeedle = Path().apply { moveTo(0f, cr * 0.78f); lineTo(cr * 0.28f, 0f); lineTo(-cr * 0.28f, 0f); close() }
            canvas.drawPath(southNeedle, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
            canvas.restore()
        }

        // Bottom Coordinates HUD
        if (config.showCoordinatesHud) {
            val hudPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                this.typeface = Typeface.MONOSPACE
                textSize = 9.5f * unitScale
                color = theme.gridHex.toInt()
            }
            val latStr = "%.2f°%s".format(kotlin.math.abs(camera.centerLat), if (camera.centerLat >= 0f) "N" else "S")
            val lonStr = "%.2f°%s".format(kotlin.math.abs(camera.centerLon), if (camera.centerLon >= 0f) "E" else "W")
            val hudText = "$latStr · $lonStr · ${"%.1f".format(camera.zoom)}x"
            val tw = hudPaint.measureText(hudText)
            val rect = RectF(10f * unitScale, height - 26f * unitScale, 24f * unitScale + tw, height - 8f * unitScale)
            canvas.drawRoundRect(rect, 6f * unitScale, 6f * unitScale, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xBB070B14.toInt() })
            canvas.drawText(hudText, rect.left + 7f * unitScale, rect.bottom - 5.5f * unitScale, hudPaint)
        }

        // Bottom-Right Scale Bar
        if (config.showScaleBar) {
            val barW = 54f * unitScale
            val bx = width - barW - 12f * unitScale
            val by = height - 13f * unitScale
            val barP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = theme.textHex.toInt(); strokeWidth = 2f * unitScale
            }
            canvas.drawLine(bx, by, bx + barW, by, barP)
            canvas.drawLine(bx, by - 4f * unitScale, bx, by + 2f * unitScale, barP)
            canvas.drawLine(bx + barW, by - 4f * unitScale, bx + barW, by + 2f * unitScale, barP)
            val approxKm = (3400f / camera.zoom.coerceAtLeast(0.5f)).toInt()
            val kmPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                this.typeface = typeface; textSize = 8.5f * unitScale; color = theme.textHex.toInt(); textAlign = Paint.Align.RIGHT
            }
            canvas.drawText("~${approxKm} km", bx + barW, by - 5f * unitScale, kmPaint)
        }
    }

    private fun buildProjectedRingPath(
        ring: List<Pair<Float, Float>>,
        camera: EvaluatedMapCamera,
        mode: MapProjectionMode,
        width: Float,
        height: Float,
        lonOffset: Float,
        unwrap: Boolean = false
    ): Path? {
        if (ring.size < 3) return null
        val path = Path()
        var started = false
        var anyVisible = false
        var prevX = 0f
        for (i in ring.indices) {
            val (lat, rawLon) = ring[i]
            val lon = if (unwrap) {
                var d = rawLon + lonOffset - camera.centerLon
                if (d > 180f) d -= 360f
                if (d < -180f) d += 360f
                camera.centerLon + d
            } else {
                rawLon + lonOffset
            }
            val p = WorldCartographyData.projectLatLon(lat, lon, camera, mode, width, height)
            if (mode == MapProjectionMode.GLOBE_3D && !p.visible) {
                // Clamp horizon points gracefully instead of breaking polygon fill
                val cx = width * 0.5f
                val cy = height * 0.5f
                val dx = p.x - cx
                val dy = p.y - cy
                val d = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                val baseRadius = minOf(width, height) * 0.38f * (0.72f + (camera.zoom - 1f) * 0.42f).coerceIn(0.65f, 5.2f)
                val clampedX = cx + (dx / d) * baseRadius
                val clampedY = cy + (dy / d) * baseRadius
                if (!started) {
                    path.moveTo(clampedX, clampedY); started = true
                } else {
                    path.lineTo(clampedX, clampedY)
                }
                prevX = clampedX
            } else {
                if (p.visible) anyVisible = true
                if (!started) {
                    path.moveTo(p.x, p.y); started = true
                } else if (kotlin.math.abs(p.x - prevX) > width * 0.75f && mode == MapProjectionMode.WORLD_2D) {
                    path.moveTo(p.x, p.y)
                } else {
                    path.lineTo(p.x, p.y)
                }
                prevX = p.x
            }
        }
        if (!anyVisible) return null
        path.close()
        return path
    }

    private fun buildStarPath(outerR: Float, innerR: Float): Path {
        val path = Path()
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) outerR else innerR
            val a = (-PI / 2.0) + i * (PI / 5.0)
            val x = (cos(a) * r).toFloat()
            val y = (sin(a) * r).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }
}
