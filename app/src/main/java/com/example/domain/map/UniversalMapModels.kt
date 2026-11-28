package com.example.domain.map

import java.util.UUID

enum class MapProjectionMode(val labelBn: String, val labelEn: String) {
    WORLD_2D("২ডি ওয়ার্ল্ড ম্যাপ", "2D World Map"),
    GLOBE_3D("৩ডি গ্লোব", "3D Globe")
}

enum class MapThemeStyle(
    val labelBn: String,
    val labelEn: String,
    val oceanHex: Long,
    val oceanDeepHex: Long,
    val landHex: Long,
    val landBorderHex: Long,
    val gridHex: Long,
    val textHex: Long,
    val accentHex: Long
) {
    TACTICAL_DARK(
        "ট্যাকটিক্যাল ডার্ক", "Tactical Dark",
        0xFF06101E, 0xFF030811, 0xFF1A2538, 0xFF3A5173, 0xFF00E5FF, 0xFFEAF2FF, 0xFFFFB300
    ),
    HISTORICAL_PARCHMENT(
        "ইতিহাস ও প্রাচীন", "Historical Atlas",
        0xFF2B241A, 0xFF1B1610, 0xFFB89B6E, 0xFF5D4629, 0xFF8B6B43, 0xFF2B1D0E, 0xFFD97706
    ),
    SATELLITE_NIGHT(
        "স্যাটেলাইট নাইট", "Satellite Night",
        0xFF040A14, 0xFF02050A, 0xFF152922, 0xFF2CE59B, 0xFF10B981, 0xFFF0FDF4, 0xFF00E5FF
    ),
    POLITICAL_ATLAS(
        "আধুনিক ভূগোল", "Modern Atlas",
        0xFF0F2D4A, 0xFF091D33, 0xFF2D4A3E, 0xFF6EE7B7, 0xFF60A5FA, 0xFFFFFFFF, 0xFFFACC15
    ),
    MILITARY_RED_OPS(
        "যুদ্ধ ও স্ট্র্যাটেজি", "War Ops Dark",
        0xFF0D080B, 0xFF050204, 0xFF26181E, 0xFF7F1D1D, 0xFFEF4444, 0xFFFEE2E2, 0xFFFF4D6D
    ),
    WEATHER_RADAR(
        "আবহাওয়া ও দুর্যোগ", "Weather Radar",
        0xFF051322, 0xFF020A14, 0xFF172B3A, 0xFF38BDF8, 0xFF0EA5E9, 0xFFE0F2FE, 0xFFF97316
    ),
    NAVAL_BLUE(
        "নৌ ও বাণিজ্য", "Naval & Trade",
        0xFF041C32, 0xFF020F1C, 0xFF1E3A5F, 0xFF7DD3FC, 0xFF38BDF8, 0xFFFFFFFF, 0xFFFFB300
    )
}

enum class MapPresetCategory(val labelBn: String, val labelEn: String) {
    HISTORY("ইতিহাস ও সভ্যতা", "History"),
    WAR("যুদ্ধ ও রণনীতি", "War & Ops"),
    TRAVEL("ভ্রমণ ও অভিবাসন", "Travel"),
    POLITICS("রাজনীতি ও সীমানা", "Politics"),
    ECONOMY("অর্থনীতি ও জনসংখ্যা", "Economy"),
    WEATHER("আবহাওয়া ও দুর্যোগ", "Weather")
}

enum class MapCameraEasing(val labelBn: String, val labelEn: String) {
    LINEAR("সরল (Linear)", "Linear"),
    EASE_IN_OUT("স্মুথ (Ease)", "Ease In-Out"),
    CINEMA_FLIGHT("ফ্লাইওভার আর্চ (Flyover)", "Cinema Flyover")
}

enum class MapRouteStyle(val labelBn: String, val labelEn: String) {
    SOLID_GLOW("উজ্জ্বল লাইন", "Solid Glow"),
    DASHED_INTEL("ড্যাশড ইন্টেল", "Dashed Intel"),
    ARMY_ADVANCE("সেনা অগ্রযাত্রা", "Army Advance"),
    INVASION_THRUST("আক্রমণ তীর (Invasion)", "Invasion Thrust"),
    FRONTLINE_CLASH("ফ্রন্টলাইন (Frontline)", "Frontline"),
    RETREAT_DASH("পশ্চাদপসরণ (Retreat)", "Retreat"),
    NAVAL_WAKE("নৌ রুট (Naval)", "Naval Route"),
    AIR_GREAT_CIRCLE("বিমান রুট (Air)", "Air Route"),
    TRADE_GOLD("বাণিজ্য রুট (Trade)", "Trade Route"),
    MIGRATION_FLOW("অভিবাসন (Migration)", "Migration")
}

enum class MapRouteTipIcon(val labelBn: String, val labelEn: String) {
    NONE("নেই", "None"),
    ARROW("তীর (Arrow)", "Arrow"),
    PLANE("বিমান (Plane)", "Plane"),
    SHIP("জাহাজ (Ship)", "Ship"),
    ARMY_UNIT("সেনা/ট্যাংক", "Army"),
    VEHICLE("গাড়ি/ট্রেন", "Vehicle"),
    PULSE_ORB("পালস বিন্দু", "Pulse")
}

enum class MapMarkerType(val labelBn: String, val labelEn: String) {
    PIN("লোকেশন পিন", "Location Pin"),
    CAPITAL_STAR("রাজধানী তারকা", "Capital Star"),
    CITY_DOT("শহর বিন্দু", "City Marker"),
    ARMY_BASE("সামরিক ঘাঁটি", "Army Base"),
    BATTLE_CLASH("যুদ্ধক্ষেত্র", "Battle Zone"),
    FLAG("পতাকা", "Flag"),
    PORT_ANCHOR("নৌবন্দর", "Naval Port"),
    AIRPORT("বিমানবন্দর", "Airport"),
    CYCLONE("ঘূর্ণিঝড় (Cyclone)", "Cyclone"),
    FLOOD("বন্যা (Flood)", "Flood"),
    EARTHQUAKE("ভূমিকম্প (Quake)", "Earthquake"),
    FIRE("আগুন/দাবানল (Fire)", "Fire"),
    HEATMAP_NODE("হিটম্যাপ (Heatmap)", "Heatmap")
}

data class MapLatLon(
    val lat: Float,
    val lon: Float
)

data class MapCameraKeyframe(
    val id: String = UUID.randomUUID().toString(),
    val label: String = "Cam",
    val timeFraction: Float = 0f,
    val centerLat: Float = 23.81f,
    val centerLon: Float = 90.41f,
    val zoom: Float = 2.2f,
    val rotationDeg: Float = 0f,
    val tiltDeg: Float = 0f,
    val easing: MapCameraEasing = MapCameraEasing.EASE_IN_OUT
)

data class MapRegionLayer(
    val id: String = UUID.randomUUID().toString(),
    val regionId: String,
    val customLabel: String = "",
    val fillColorHex: Long = 0xFFFFB300,
    val borderColorHex: Long = 0xFFFFE082,
    val fillOpacity: Float = 0.42f,
    val borderDrawAnimated: Boolean = true,
    val territoryExpansion: Boolean = false,
    val startFrac: Float = 0.0f,
    val endFrac: Float = 1.0f,
    val isVisible: Boolean = true
)

data class MapRouteLayer(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "রুট ১",
    val waypoints: List<MapLatLon> = listOf(MapLatLon(23.81f, 90.41f), MapLatLon(22.35f, 91.78f)),
    val routeStyle: MapRouteStyle = MapRouteStyle.AIR_GREAT_CIRCLE,
    val tipIcon: MapRouteTipIcon = MapRouteTipIcon.PLANE,
    val colorHex: Long = 0xFF00E5FF,
    val strokeWidthDp: Float = 4.0f,
    val startFrac: Float = 0.1f,
    val endFrac: Float = 0.9f,
    val isVisible: Boolean = true
)

data class MapMarkerLayer(
    val id: String = UUID.randomUUID().toString(),
    val label: String = "ঢাকা",
    val subtitle: String = "",
    val lat: Float = 23.81f,
    val lon: Float = 90.41f,
    val markerType: MapMarkerType = MapMarkerType.PIN,
    val colorHex: Long = 0xFFFFB300,
    val sizeScale: Float = 1.0f,
    val pulseAnimated: Boolean = true,
    val showCoordinates: Boolean = false,
    val startFrac: Float = 0.0f,
    val endFrac: Float = 1.0f,
    val isVisible: Boolean = true
)

data class MapTextLayer(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "গুরুত্বপূর্ণ তথ্য",
    val lat: Float = 24.5f,
    val lon: Float = 90.0f,
    val colorHex: Long = 0xFFFFFFFF,
    val bgColorHex: Long = 0xCC0A101C,
    val fontSizeScale: Float = 1.0f,
    val showArrowPointer: Boolean = true,
    val startFrac: Float = 0.0f,
    val endFrac: Float = 1.0f,
    val isVisible: Boolean = true
)

data class UniversalMapConfig(
    val title: String = "বিশ্ব মানচিত্র অ্যানিমেশন",
    val subtitle: String = "ইতিহাস · ভূগোল · রুট স্টুডিও",
    val projectionMode: MapProjectionMode = MapProjectionMode.WORLD_2D,
    val themeStyle: MapThemeStyle = MapThemeStyle.TACTICAL_DARK,
    val durationMs: Long = 8_000L,
    val speedMultiplier: Float = 1.0f,
    val showGraticuleGrid: Boolean = true,
    val showCountryBorders: Boolean = true,
    val showCountryNames: Boolean = true,
    val showCoordinatesHud: Boolean = true,
    val showCompassRose: Boolean = true,
    val showScaleBar: Boolean = true,
    val keyframes: List<MapCameraKeyframe> = listOf(
        MapCameraKeyframe(
            id = "kf_start",
            label = "শুরু",
            timeFraction = 0f,
            centerLat = 20f,
            centerLon = 50f,
            zoom = 1.4f,
            rotationDeg = 0f,
            tiltDeg = 0f,
            easing = MapCameraEasing.EASE_IN_OUT
        ),
        MapCameraKeyframe(
            id = "kf_focus",
            label = "জুম",
            timeFraction = 0.65f,
            centerLat = 23.81f,
            centerLon = 90.41f,
            zoom = 4.2f,
            rotationDeg = 0f,
            tiltDeg = 14f,
            easing = MapCameraEasing.CINEMA_FLIGHT
        )
    ),
    val regions: List<MapRegionLayer> = emptyList(),
    val routes: List<MapRouteLayer> = emptyList(),
    val markers: List<MapMarkerLayer> = emptyList(),
    val textLabels: List<MapTextLayer> = emptyList()
)
