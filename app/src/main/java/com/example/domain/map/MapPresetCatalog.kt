package com.example.domain.map

data class MapAnimationPreset(
    val id: String,
    val category: MapPresetCategory,
    val titleBn: String,
    val titleEn: String,
    val descriptionBn: String,
    val config: UniversalMapConfig
)

object MapPresetCatalog {

    val presets: List<MapAnimationPreset> = listOf(
        // 1. WAR & STRATEGY
        MapAnimationPreset(
            id = "war_1971_victory",
            category = MapPresetCategory.WAR,
            titleBn = "১৯৭১ যুদ্ধ ও চূড়ান্ত অগ্রযাত্রা",
            titleEn = "1971 War & Advance",
            descriptionBn = "সীমান্ত, ফ্রন্টলাইন, সেনা অগ্রযাত্রা ও রাজধানী অভিমুখে রুট",
            config = UniversalMapConfig(
                title = "১৯৭১ · ঢাকা অভিমুখে চূড়ান্ত অগ্রযাত্রা",
                subtitle = "যুদ্ধ মানচিত্র · সেনা অগ্রযাত্রা ও ফ্রন্টলাইন",
                projectionMode = MapProjectionMode.WORLD_2D,
                themeStyle = MapThemeStyle.MILITARY_RED_OPS,
                durationMs = 9_000L,
                keyframes = listOf(
                    MapCameraKeyframe("w1", "সংক্ষিপ্ত", 0f, 23.6f, 89.8f, 3.8f, 0f, 0f, MapCameraEasing.EASE_IN_OUT),
                    MapCameraKeyframe("w2", "ফোকাস", 0.55f, 23.81f, 90.41f, 6.2f, -3f, 22f, MapCameraEasing.CINEMA_FLIGHT),
                    MapCameraKeyframe("w3", "চূড়ান্ত", 1.0f, 23.81f, 90.41f, 7.0f, 0f, 18f, MapCameraEasing.EASE_IN_OUT)
                ),
                regions = listOf(
                    MapRegionLayer(
                        regionId = "bgd",
                        customLabel = "মুক্তাঞ্চল ও সীমানা",
                        fillColorHex = 0xFF10B981,
                        borderColorHex = 0xFFFFB300,
                        fillOpacity = 0.44f,
                        borderDrawAnimated = true,
                        territoryExpansion = true
                    )
                ),
                routes = listOf(
                    MapRouteLayer(
                        name = "পশ্চিম সেক্টর অগ্রযাত্রা",
                        waypoints = listOf(MapLatLon(23.16f, 89.21f), MapLatLon(23.62f, 89.85f), MapLatLon(23.81f, 90.41f)),
                        routeStyle = MapRouteStyle.ARMY_ADVANCE,
                        tipIcon = MapRouteTipIcon.ARMY_UNIT,
                        colorHex = 0xFFFFB300,
                        strokeWidthDp = 4.6f,
                        startFrac = 0.1f,
                        endFrac = 0.78f
                    ),
                    MapRouteLayer(
                        name = "পূর্ব ফ্রন্ট আক্রমণ",
                        waypoints = listOf(MapLatLon(24.89f, 91.87f), MapLatLon(24.05f, 90.98f), MapLatLon(23.81f, 90.41f)),
                        routeStyle = MapRouteStyle.INVASION_THRUST,
                        tipIcon = MapRouteTipIcon.ARROW,
                        colorHex = 0xFFFF4D6D,
                        strokeWidthDp = 4.6f,
                        startFrac = 0.2f,
                        endFrac = 0.85f
                    ),
                    MapRouteLayer(
                        name = "দক্ষিণ নৌ-অবরোধ",
                        waypoints = listOf(MapLatLon(18.5f, 89.5f), MapLatLon(21.42f, 91.5f), MapLatLon(22.35f, 91.78f)),
                        routeStyle = MapRouteStyle.NAVAL_WAKE,
                        tipIcon = MapRouteTipIcon.SHIP,
                        colorHex = 0xFF00E5FF,
                        strokeWidthDp = 4.0f,
                        startFrac = 0.15f,
                        endFrac = 0.88f
                    )
                ),
                markers = listOf(
                    MapMarkerLayer(label = "ঢাকা", subtitle = "চূড়ান্ত লক্ষ্য", lat = 23.81f, lon = 90.41f, markerType = MapMarkerType.CAPITAL_STAR, colorHex = 0xFFFFB300, showCoordinates = true),
                    MapMarkerLayer(label = "যশোর রণাঙ্গন", lat = 23.16f, lon = 89.21f, markerType = MapMarkerType.BATTLE_CLASH, colorHex = 0xFFFF4D6D, startFrac = 0.1f),
                    MapMarkerLayer(label = "চট্টগ্রাম বন্দর", lat = 22.35f, lon = 91.78f, markerType = MapMarkerType.PORT_ANCHOR, colorHex = 0xFF00E5FF, startFrac = 0.3f)
                ),
                textLabels = listOf(
                    MapTextLayer(text = "১৬ ডিসেম্বর · ঐতিহাসিক বিজয়", lat = 25.3f, lon = 90.3f, colorHex = 0xFFFFE082, startFrac = 0.6f)
                )
            )
        ),
        MapAnimationPreset(
            id = "war_frontline_retreat",
            category = MapPresetCategory.WAR,
            titleBn = "যুদ্ধ ফ্রন্টলাইন, আক্রমণ ও পশ্চাদপসরণ",
            titleEn = "Frontline, Invasion & Retreat",
            descriptionBn = "দুই পক্ষের ফ্রন্টলাইন সংঘাত, আক্রমণ তীর ও পশ্চাদপসরণ রুট",
            config = UniversalMapConfig(
                title = "ফ্রন্টলাইন কৌশলগত বিশ্লেষণ",
                subtitle = "আক্রমণ (Invasion) বনাম পশ্চাদপসরণ (Retreat)",
                projectionMode = MapProjectionMode.WORLD_2D,
                themeStyle = MapThemeStyle.MILITARY_RED_OPS,
                durationMs = 8_500L,
                keyframes = listOf(
                    MapCameraKeyframe("fr1", "বিস্তৃত", 0f, 49.0f, 32.0f, 3.0f, 0f, 0f, MapCameraEasing.EASE_IN_OUT),
                    MapCameraKeyframe("fr2", "ফ্রন্টলাইন", 0.7f, 49.5f, 35.0f, 5.2f, 4f, 18f, MapCameraEasing.CINEMA_FLIGHT)
                ),
                regions = listOf(
                    MapRegionLayer(regionId = "ukr", customLabel = "প্রতিরক্ষা বলয়", fillColorHex = 0xFF38BDF8, borderColorHex = 0xFF00E5FF, fillOpacity = 0.35f, borderDrawAnimated = true)
                ),
                routes = listOf(
                    MapRouteLayer(
                        name = "পূর্ব আক্রমণ (Invasion)",
                        waypoints = listOf(MapLatLon(51.5f, 39.2f), MapLatLon(49.9f, 36.2f)),
                        routeStyle = MapRouteStyle.INVASION_THRUST,
                        tipIcon = MapRouteTipIcon.ARMY_UNIT,
                        colorHex = 0xFFFF4D6D,
                        strokeWidthDp = 4.5f,
                        startFrac = 0.08f,
                        endFrac = 0.65f
                    ),
                    MapRouteLayer(
                        name = "সক্রিয় ফ্রন্টলাইন",
                        waypoints = listOf(MapLatLon(50.2f, 36.8f), MapLatLon(48.1f, 37.5f), MapLatLon(46.6f, 33.4f)),
                        routeStyle = MapRouteStyle.FRONTLINE_CLASH,
                        tipIcon = MapRouteTipIcon.NONE,
                        colorHex = 0xFFFFB300,
                        strokeWidthDp = 5.0f,
                        startFrac = 0.25f,
                        endFrac = 0.9f
                    ),
                    MapRouteLayer(
                        name = "কৌশলগত পশ্চাদপসরণ",
                        waypoints = listOf(MapLatLon(50.45f, 30.52f), MapLatLon(51.6f, 31.3f), MapLatLon(53.2f, 34.3f)),
                        routeStyle = MapRouteStyle.RETREAT_DASH,
                        tipIcon = MapRouteTipIcon.ARROW,
                        colorHex = 0xFF94A3B8,
                        strokeWidthDp = 3.8f,
                        startFrac = 0.45f,
                        endFrac = 0.95f
                    )
                ),
                markers = listOf(
                    MapMarkerLayer(label = "কিভ", lat = 50.45f, lon = 30.52f, markerType = MapMarkerType.CAPITAL_STAR, colorHex = 0xFF38BDF8),
                    MapMarkerLayer(label = "ফ্রন্ট সেক্টর-১", lat = 49.0f, lon = 37.0f, markerType = MapMarkerType.BATTLE_CLASH, colorHex = 0xFFFF4D6D)
                )
            )
        ),

        // 2. HISTORY & ANCIENT CIVILIZATION
        MapAnimationPreset(
            id = "history_ancient_civs",
            category = MapPresetCategory.HISTORY,
            titleBn = "প্রাচীন সভ্যতা ও সিল্ক রোড",
            titleEn = "Ancient Civilizations & Silk Road",
            descriptionBn = "সিন্ধু, মেসোপটেমিয়া, পারস্য ও ঐতিহাসিক সিল্ক রোড রুট",
            config = UniversalMapConfig(
                title = "প্রাচীন সভ্যতা ও সিল্ক রোড",
                subtitle = "সিন্ধু · মেসোপটেমিয়া · বাংলা সালতানাত",
                projectionMode = MapProjectionMode.WORLD_2D,
                themeStyle = MapThemeStyle.HISTORICAL_PARCHMENT,
                durationMs = 10_000L,
                keyframes = listOf(
                    MapCameraKeyframe("h1", "মেসোপটেমিয়া", 0f, 32f, 48f, 2.8f, 0f, 0f, MapCameraEasing.EASE_IN_OUT),
                    MapCameraKeyframe("h2", "সিন্ধু ও বাংলা", 0.55f, 26f, 75f, 3.1f, 0f, 12f, MapCameraEasing.CINEMA_FLIGHT),
                    MapCameraKeyframe("h3", "সিল্ক রোড", 1.0f, 34f, 82f, 2.4f, 0f, 0f, MapCameraEasing.EASE_IN_OUT)
                ),
                regions = listOf(
                    MapRegionLayer(regionId = "civ_mesopotamia", customLabel = "মেসোপটেমিয়া", fillColorHex = 0xFFD97706, borderColorHex = 0xFF78350F, territoryExpansion = true, startFrac = 0f),
                    MapRegionLayer(regionId = "civ_indus", customLabel = "সিন্ধু সভ্যতা", fillColorHex = 0xFF15803D, borderColorHex = 0xFF14532D, territoryExpansion = true, startFrac = 0.25f),
                    MapRegionLayer(regionId = "emp_bengal", customLabel = "বাংলা সালতানাত", fillColorHex = 0xFFB91C1C, borderColorHex = 0xFF7F1D1D, territoryExpansion = true, startFrac = 0.5f)
                ),
                routes = listOf(
                    MapRouteLayer(
                        name = "প্রাচীন সিল্ক ও মসলিন রুট",
                        waypoints = listOf(MapLatLon(34.3f, 108.9f), MapLatLon(39.65f, 66.95f), MapLatLon(35.68f, 51.38f), MapLatLon(41.0f, 28.97f)),
                        routeStyle = MapRouteStyle.TRADE_GOLD,
                        tipIcon = MapRouteTipIcon.ARROW,
                        colorHex = 0xFFFFB300,
                        strokeWidthDp = 4.2f,
                        startFrac = 0.15f,
                        endFrac = 0.92f
                    )
                ),
                markers = listOf(
                    MapMarkerLayer(label = "বাগদাদ (ব্যাবিলন)", lat = 33.31f, lon = 44.36f, markerType = MapMarkerType.FLAG, colorHex = 0xFFD97706),
                    MapMarkerLayer(label = "সমরকন্দ", lat = 39.65f, lon = 66.96f, markerType = MapMarkerType.CITY_DOT, colorHex = 0xFFB45309),
                    MapMarkerLayer(label = "সিন্ধু অববাহিকা", lat = 27.3f, lon = 68.1f, markerType = MapMarkerType.PIN, colorHex = 0xFF15803D)
                )
            )
        ),
        MapAnimationPreset(
            id = "history_ottoman_empire",
            category = MapPresetCategory.HISTORY,
            titleBn = "সাম্রাজ্য বিস্তৃতি (উসমানীয় / মোঘল)",
            titleEn = "Imperial Territory Expansion",
            descriptionBn = "ঐতিহাসিক সাম্রাজ্যের সীমান্ত অঙ্কন ও ভূখণ্ড সম্প্রসারণ",
            config = UniversalMapConfig(
                title = "উসমানীয় ও মোঘল সাম্রাজ্য বিস্তৃতি",
                subtitle = "Territory Expansion & Border Draw",
                projectionMode = MapProjectionMode.GLOBE_3D,
                themeStyle = MapThemeStyle.HISTORICAL_PARCHMENT,
                durationMs = 9_000L,
                keyframes = listOf(
                    MapCameraKeyframe("e1", "আনাতোলিয়া", 0f, 39.5f, 31.0f, 2.0f, 0f, 0f, MapCameraEasing.EASE_IN_OUT),
                    MapCameraKeyframe("e2", "দক্ষিণ এশিয়া ফ্লাইওভার", 0.7f, 28.0f, 65.0f, 2.5f, 0f, 15f, MapCameraEasing.CINEMA_FLIGHT)
                ),
                regions = listOf(
                    MapRegionLayer(regionId = "emp_ottoman", customLabel = "উসমানীয় সাম্রাজ্য", fillColorHex = 0xFFDC2626, borderColorHex = 0xFFFFB300, territoryExpansion = true, startFrac = 0.05f),
                    MapRegionLayer(regionId = "emp_mughal", customLabel = "মোঘল সাম্রাজ্য", fillColorHex = 0xFF059669, borderColorHex = 0xFFFDE047, territoryExpansion = true, startFrac = 0.35f)
                ),
                markers = listOf(
                    MapMarkerLayer(label = "ইস্তাম্বুল", lat = 41.01f, lon = 28.98f, markerType = MapMarkerType.CAPITAL_STAR, colorHex = 0xFFFFB300),
                    MapMarkerLayer(label = "দিল্লি", lat = 28.61f, lon = 77.21f, markerType = MapMarkerType.CAPITAL_STAR, colorHex = 0xFF10B981)
                )
            )
        ),

        // 3. TRAVEL & MIGRATION
        MapAnimationPreset(
            id = "travel_globe_flyover",
            category = MapPresetCategory.TRAVEL,
            titleBn = "৩ডি গ্লোব বিশ্ব ভ্রমণ ফ্লাইওভার",
            titleEn = "3D Globe World Flyover",
            descriptionBn = "ঢাকা ➔ দুবাই ➔ লন্ডন ➔ নিউ ইয়র্ক সিনেম্যাটিক গ্লোব ফ্লাইট",
            config = UniversalMapConfig(
                title = "৩ডি গ্লোব · বিশ্ব ভ্রমণ ফ্লাইওভার",
                subtitle = "ঢাকা ➔ দুবাই ➔ লন্ডন ➔ নিউ ইয়র্ক",
                projectionMode = MapProjectionMode.GLOBE_3D,
                themeStyle = MapThemeStyle.POLITICAL_ATLAS,
                durationMs = 10_000L,
                keyframes = listOf(
                    MapCameraKeyframe("t1", "ঢাকা", 0f, 23.81f, 90.41f, 2.4f, 0f, 5f, MapCameraEasing.EASE_IN_OUT),
                    MapCameraKeyframe("t2", "দুবাই", 0.32f, 25.20f, 55.27f, 2.1f, -4f, 10f, MapCameraEasing.CINEMA_FLIGHT),
                    MapCameraKeyframe("t3", "লন্ডন", 0.65f, 51.50f, -0.12f, 2.3f, 2f, 12f, MapCameraEasing.CINEMA_FLIGHT),
                    MapCameraKeyframe("t4", "নিউ ইয়র্ক", 1.0f, 40.71f, -74.00f, 2.4f, 0f, 8f, MapCameraEasing.CINEMA_FLIGHT)
                ),
                routes = listOf(
                    MapRouteLayer(
                        name = "আন্তর্জাতিক ফ্লাইট রুট",
                        waypoints = listOf(
                            MapLatLon(23.81f, 90.41f),
                            MapLatLon(25.20f, 55.27f),
                            MapLatLon(51.50f, -0.12f),
                            MapLatLon(40.71f, -74.00f)
                        ),
                        routeStyle = MapRouteStyle.AIR_GREAT_CIRCLE,
                        tipIcon = MapRouteTipIcon.PLANE,
                        colorHex = 0xFF00E5FF,
                        strokeWidthDp = 4.2f,
                        startFrac = 0.05f,
                        endFrac = 0.96f
                    )
                ),
                markers = listOf(
                    MapMarkerLayer(label = "ঢাকা", lat = 23.81f, lon = 90.41f, markerType = MapMarkerType.AIRPORT, colorHex = 0xFFFFB300, showCoordinates = true),
                    MapMarkerLayer(label = "দুবাই", lat = 25.20f, lon = 55.27f, markerType = MapMarkerType.AIRPORT, colorHex = 0xFF00E5FF, startFrac = 0.25f),
                    MapMarkerLayer(label = "লন্ডন", lat = 51.50f, lon = -0.12f, markerType = MapMarkerType.AIRPORT, colorHex = 0xFF2CE59B, startFrac = 0.52f),
                    MapMarkerLayer(label = "নিউ ইয়র্ক", lat = 40.71f, lon = -74.00f, markerType = MapMarkerType.PIN, colorHex = 0xFFFACC15, startFrac = 0.8f)
                )
            )
        ),
        MapAnimationPreset(
            id = "travel_migration_flow",
            category = MapPresetCategory.TRAVEL,
            titleBn = "অভিবাসন ও মানবযাত্রা (Migration)",
            titleEn = "Human Migration Flows",
            descriptionBn = "এশিয়া ও আফ্রিকা থেকে বহুমুখী অভিবাসন স্রোত",
            config = UniversalMapConfig(
                title = "বৈশ্বিক অভিবাসন মানচিত্র",
                subtitle = "শ্রম ও অভিবাসন রুট বিশ্লেষণ",
                projectionMode = MapProjectionMode.WORLD_2D,
                themeStyle = MapThemeStyle.SATELLITE_NIGHT,
                durationMs = 8_500L,
                keyframes = listOf(
                    MapCameraKeyframe("m1", "বিশ্ব", 0f, 28f, 45f, 1.35f, 0f, 0f, MapCameraEasing.EASE_IN_OUT),
                    MapCameraKeyframe("m2", "মধ্যপ্রাচ্য-ইউরোপ", 0.75f, 34f, 42f, 2.2f, 0f, 10f, MapCameraEasing.EASE_IN_OUT)
                ),
                routes = listOf(
                    MapRouteLayer(
                        name = "দক্ষিণ এশিয়া ➔ মধ্যপ্রাচ্য",
                        waypoints = listOf(MapLatLon(23.81f, 90.41f), MapLatLon(24.71f, 46.67f)),
                        routeStyle = MapRouteStyle.MIGRATION_FLOW,
                        tipIcon = MapRouteTipIcon.ARROW,
                        colorHex = 0xFF2CE59B,
                        strokeWidthDp = 4.0f,
                        startFrac = 0.1f,
                        endFrac = 0.85f
                    ),
                    MapRouteLayer(
                        name = "ভূমধ্যসাগর ➔ ইউরোপ",
                        waypoints = listOf(MapLatLon(30.04f, 31.23f), MapLatLon(41.90f, 12.49f), MapLatLon(52.52f, 13.40f)),
                        routeStyle = MapRouteStyle.MIGRATION_FLOW,
                        tipIcon = MapRouteTipIcon.ARROW,
                        colorHex = 0xFF00E5FF,
                        strokeWidthDp = 4.0f,
                        startFrac = 0.2f,
                        endFrac = 0.92f
                    )
                ),
                markers = listOf(
                    MapMarkerLayer(label = "ঢাকা", lat = 23.81f, lon = 90.41f, markerType = MapMarkerType.CITY_DOT, colorHex = 0xFF2CE59B),
                    MapMarkerLayer(label = "রিয়াদ", lat = 24.71f, lon = 46.67f, markerType = MapMarkerType.PIN, colorHex = 0xFFFFB300),
                    MapMarkerLayer(label = "বার্লিন", lat = 52.52f, lon = 13.40f, markerType = MapMarkerType.PIN, colorHex = 0xFF00E5FF)
                )
            )
        ),

        // 4. POLITICS & BORDERS
        MapAnimationPreset(
            id = "politics_border_zoom",
            category = MapPresetCategory.POLITICS,
            titleBn = "ভূ-রাজনীতি ও সীমান্ত হাইলাইট",
            titleEn = "Geopolitics & Border Draw",
            descriptionBn = "দেশ হাইলাইট, অ্যানিমেটেড সীমান্ত অঙ্কন ও ক্যামেরা জুম",
            config = UniversalMapConfig(
                title = "দক্ষিণ এশিয়া ভূ-রাজনৈতিক সীমান্ত",
                subtitle = "দেশ হাইলাইট ও সীমানা অ্যানিমেশন",
                projectionMode = MapProjectionMode.WORLD_2D,
                themeStyle = MapThemeStyle.TACTICAL_DARK,
                durationMs = 8_000L,
                keyframes = listOf(
                    MapCameraKeyframe("p1", "এশিয়া", 0f, 26f, 82f, 1.8f, 0f, 0f, MapCameraEasing.EASE_IN_OUT),
                    MapCameraKeyframe("p2", "বাংলাদেশ-ভারত", 0.65f, 24.5f, 86.5f, 3.9f, 0f, 14f, MapCameraEasing.CINEMA_FLIGHT)
                ),
                regions = listOf(
                    MapRegionLayer(regionId = "bgd", customLabel = "বাংলাদেশ", fillColorHex = 0xFF10B981, borderColorHex = 0xFF00E5FF, borderDrawAnimated = true, startFrac = 0.1f),
                    MapRegionLayer(regionId = "ind", customLabel = "ভারত", fillColorHex = 0xFFF59E0B, borderColorHex = 0xFFFFE082, borderDrawAnimated = true, startFrac = 0.35f),
                    MapRegionLayer(regionId = "pak", customLabel = "পাকিস্তান", fillColorHex = 0xFF38BDF8, borderColorHex = 0xFF7DD3FC, borderDrawAnimated = true, startFrac = 0.55f)
                ),
                markers = listOf(
                    MapMarkerLayer(label = "ঢাকা", lat = 23.81f, lon = 90.41f, markerType = MapMarkerType.CAPITAL_STAR, colorHex = 0xFF10B981, showCoordinates = true),
                    MapMarkerLayer(label = "নয়াদিল্লি", lat = 28.61f, lon = 77.21f, markerType = MapMarkerType.CAPITAL_STAR, colorHex = 0xFFFFB300)
                )
            )
        ),

        // 5. ECONOMY, TRADE & POPULATION HEATMAP
        MapAnimationPreset(
            id = "economy_maritime_trade",
            category = MapPresetCategory.ECONOMY,
            titleBn = "বিশ্ব বাণিজ্য ও সমুদ্র করিডোর",
            titleEn = "Global Maritime Trade Route",
            descriptionBn = "সাংহাই ➔ সিঙ্গাপুর ➔ চট্টগ্রাম ➔ সুয়েজ ➔ ইউরোপ বাণিজ্য পথ",
            config = UniversalMapConfig(
                title = "বৈশ্বিক সমুদ্র বাণিজ্য ও অর্থনীতি",
                subtitle = "মেগা সমুদ্র বন্দর ও ট্রানজিট রুট",
                projectionMode = MapProjectionMode.WORLD_2D,
                themeStyle = MapThemeStyle.NAVAL_BLUE,
                durationMs = 9_500L,
                keyframes = listOf(
                    MapCameraKeyframe("ec1", "এশিয়া-ইউরোপ", 0f, 22f, 75f, 1.35f, 0f, 0f, MapCameraEasing.EASE_IN_OUT),
                    MapCameraKeyframe("ec2", "সুয়েজ ও বঙ্গোপসাগর", 0.65f, 21f, 65f, 2.2f, 0f, 10f, MapCameraEasing.CINEMA_FLIGHT)
                ),
                routes = listOf(
                    MapRouteLayer(
                        name = "ম্যারিটাইম সিল্ক করিডোর",
                        waypoints = listOf(
                            MapLatLon(31.23f, 121.47f),
                            MapLatLon(1.35f, 103.82f),
                            MapLatLon(22.35f, 91.78f),
                            MapLatLon(6.92f, 79.86f),
                            MapLatLon(30.58f, 32.26f),
                            MapLatLon(51.50f, -0.12f)
                        ),
                        routeStyle = MapRouteStyle.NAVAL_WAKE,
                        tipIcon = MapRouteTipIcon.SHIP,
                        colorHex = 0xFFFFB300,
                        strokeWidthDp = 4.2f,
                        startFrac = 0.05f,
                        endFrac = 0.94f
                    )
                ),
                markers = listOf(
                    MapMarkerLayer(label = "সাংহাই", lat = 31.23f, lon = 121.47f, markerType = MapMarkerType.PORT_ANCHOR, colorHex = 0xFFFFB300),
                    MapMarkerLayer(label = "সিঙ্গাপুর", lat = 1.35f, lon = 103.82f, markerType = MapMarkerType.PORT_ANCHOR, colorHex = 0xFF00E5FF),
                    MapMarkerLayer(label = "চট্টগ্রাম", lat = 22.35f, lon = 91.78f, markerType = MapMarkerType.PORT_ANCHOR, colorHex = 0xFF2CE59B),
                    MapMarkerLayer(label = "সুয়েজ খাল", lat = 30.58f, lon = 32.26f, markerType = MapMarkerType.HEATMAP_NODE, colorHex = 0xFFFF4D6D)
                )
            )
        ),
        MapAnimationPreset(
            id = "economy_population_heatmap",
            category = MapPresetCategory.ECONOMY,
            titleBn = "জনসংখ্যা ও অর্থনৈতিক হিটম্যাপ",
            titleEn = "Population Density Heatmap",
            descriptionBn = "এশিয়ার মেগাসিটি ঘনত্ব ও হিটম্যাপ পালস ভিজ্যুয়ালাইজেশন",
            config = UniversalMapConfig(
                title = "দক্ষিণ ও পূর্ব এশিয়া জনসংখ্যা হিটম্যাপ",
                subtitle = "মেগাসিটি ঘনত্ব ও অর্থনৈতিক হটস্পট",
                projectionMode = MapProjectionMode.WORLD_2D,
                themeStyle = MapThemeStyle.TACTICAL_DARK,
                durationMs = 8_000L,
                keyframes = listOf(
                    MapCameraKeyframe("pop1", "এশিয়া", 0f, 26f, 96f, 1.9f, 0f, 0f, MapCameraEasing.EASE_IN_OUT),
                    MapCameraKeyframe("pop2", "গাঙ্গেয় ব-দ্বীপ", 0.7f, 24.5f, 88f, 3.5f, 0f, 12f, MapCameraEasing.EASE_IN_OUT)
                ),
                markers = listOf(
                    MapMarkerLayer(label = "ঢাকা হাব", subtitle = "ঘনত্ব সর্বোচ্চ", lat = 23.81f, lon = 90.41f, markerType = MapMarkerType.HEATMAP_NODE, colorHex = 0xFFFF3B30, sizeScale = 1.6f),
                    MapMarkerLayer(label = "দিল্লি হাব", lat = 28.61f, lon = 77.21f, markerType = MapMarkerType.HEATMAP_NODE, colorHex = 0xFFFF7A00, sizeScale = 1.5f),
                    MapMarkerLayer(label = "কলকাতা", lat = 22.57f, lon = 88.36f, markerType = MapMarkerType.HEATMAP_NODE, colorHex = 0xFFFFB300, sizeScale = 1.3f),
                    MapMarkerLayer(label = "মুম্বাই", lat = 19.07f, lon = 72.87f, markerType = MapMarkerType.HEATMAP_NODE, colorHex = 0xFFFF7A00, sizeScale = 1.3f),
                    MapMarkerLayer(label = "সাংহাই", lat = 31.23f, lon = 121.47f, markerType = MapMarkerType.HEATMAP_NODE, colorHex = 0xFFFF3B30, sizeScale = 1.5f),
                    MapMarkerLayer(label = "টোকিও", lat = 35.67f, lon = 139.65f, markerType = MapMarkerType.HEATMAP_NODE, colorHex = 0xFFFFB300, sizeScale = 1.4f)
                )
            )
        ),

        // 6. WEATHER & DISASTERS
        MapAnimationPreset(
            id = "weather_cyclone_flood",
            category = MapPresetCategory.WEATHER,
            titleBn = "ঘূর্ণিঝড়, বন্যা, ভূমিকম্প ও দাবানল",
            titleEn = "Cyclone, Flood, Quake & Fire",
            descriptionBn = "বঙ্গোপসাগরের ঘূর্ণিঝড় ট্র্যাক, উপকূলীয় বন্যা, ভূমিকম্প তরঙ্গ ও অগ্নি-সংকেত",
            config = UniversalMapConfig(
                title = "দুর্যোগ ও আবহাওয়া রাডার ম্যাপ",
                subtitle = "ঘূর্ণিঝড় · বন্যা · ভূমিকম্প · অগ্নিকাণ্ড",
                projectionMode = MapProjectionMode.WORLD_2D,
                themeStyle = MapThemeStyle.WEATHER_RADAR,
                durationMs = 9_000L,
                keyframes = listOf(
                    MapCameraKeyframe("wx1", "বঙ্গোপসাগর", 0f, 17.5f, 89.5f, 3.0f, 0f, 0f, MapCameraEasing.EASE_IN_OUT),
                    MapCameraKeyframe("wx2", "উপকূলে আঘাত", 0.72f, 22.1f, 90.3f, 4.8f, 0f, 14f, MapCameraEasing.CINEMA_FLIGHT)
                ),
                regions = listOf(
                    MapRegionLayer(regionId = "bgd", customLabel = "উপকূলীয় সতর্কতা", fillColorHex = 0xFF0EA5E9, borderColorHex = 0xFF38BDF8, fillOpacity = 0.28f)
                ),
                routes = listOf(
                    MapRouteLayer(
                        name = "সুপার সাইক্লোন গতিপথ",
                        waypoints = listOf(MapLatLon(14.0f, 88.5f), MapLatLon(18.2f, 89.3f), MapLatLon(22.2f, 90.2f)),
                        routeStyle = MapRouteStyle.DASHED_INTEL,
                        tipIcon = MapRouteTipIcon.PULSE_ORB,
                        colorHex = 0xFFF97316,
                        strokeWidthDp = 4.4f,
                        startFrac = 0.05f,
                        endFrac = 0.85f
                    )
                ),
                markers = listOf(
                    MapMarkerLayer(label = "সুপার সাইক্লোন", subtitle = "বেগ ১৬৫ কিমি/ঘ", lat = 18.2f, lon = 89.3f, markerType = MapMarkerType.CYCLONE, colorHex = 0xFFF97316, sizeScale = 1.5f),
                    MapMarkerLayer(label = "উপকূলীয় বন্যা", lat = 22.3f, lon = 90.2f, markerType = MapMarkerType.FLOOD, colorHex = 0xFF38BDF8, sizeScale = 1.3f, startFrac = 0.35f),
                    MapMarkerLayer(label = "ভূমিকম্প কেন্দ্র", lat = 24.89f, lon = 91.87f, markerType = MapMarkerType.EARTHQUAKE, colorHex = 0xFFFACC15, sizeScale = 1.3f),
                    MapMarkerLayer(label = "বনাঞ্চল আগুন", lat = 21.4f, lon = 92.1f, markerType = MapMarkerType.FIRE, colorHex = 0xFFFF3B30, sizeScale = 1.25f)
                )
            )
        )
    )
}
