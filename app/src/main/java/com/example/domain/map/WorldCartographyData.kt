package com.example.domain.map

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class GeoRegionDefinition(
    val id: String,
    val nameBn: String,
    val nameEn: String,
    val isHistorical: Boolean = false,
    val centerLat: Float,
    val centerLon: Float,
    val rings: List<List<Pair<Float, Float>>> // lat, lon pairs
)

data class GeoCityEntry(
    val id: String,
    val nameBn: String,
    val nameEn: String,
    val countryBn: String,
    val lat: Float,
    val lon: Float
)

data class EvaluatedMapCamera(
    val centerLat: Float,
    val centerLon: Float,
    val zoom: Float,
    val rotationDeg: Float,
    val tiltDeg: Float
)

data class ProjectedScreenPoint(
    val x: Float,
    val y: Float,
    val visible: Boolean,
    val depthZ: Float = 1f
)

object WorldCartographyData {

    /**
     * Base World Continents & Major Islands (lat, lon) simplified high-accuracy polygons
     * so both 2D Equirectangular and 3D Orthographic Globe render authentic geography.
     */
    val continentPolygons: List<List<Pair<Float, Float>>> = listOf(
        // North America + Central America
        listOf(
            70f to -162f, 71f to -156f, 70f to -140f, 69f to -125f, 72f to -95f,
            68f to -82f, 62f to -76f, 59f to -64f, 52f to -55f, 47f to -53f,
            44f to -66f, 41f to -70f, 35f to -75f, 30f to -81f, 25f to -80f,
            29f to -85f, 29f to -94f, 26f to -97f, 21f to -97f, 21f to -87f,
            18f to -88f, 15f to -83f, 11f to -83f, 9f to -79f, 8f to -82f,
            14f to -92f, 16f to -98f, 20f to -105f, 24f to -107f, 31f to -114f,
            23f to -110f, 32f to -117f, 37f to -122f, 42f to -124f, 48f to -125f,
            54f to -130f, 59f to -140f, 60f to -150f, 55f to -162f, 62f to -166f,
            66f to -168f
        ),
        // Greenland
        listOf(
            60f to -44f, 66f to -36f, 74f to -20f, 82f to -28f, 82f to -55f,
            77f to -70f, 69f to -54f
        ),
        // South America
        listOf(
            12f to -72f, 10f to -62f, 6f to -52f, 0f to -49f, -5f to -35f,
            -13f to -38f, -23f to -42f, -33f to -53f, -41f to -63f, -52f to -68f,
            -55f to -67f, -53f to -74f, -44f to -74f, -35f to -72f, -20f to -70f,
            -12f to -77f, -5f to -81f, 2f to -79f, 8f to -77f
        ),
        // Europe (excluding Scandinavia peninsula tip merged cleanly)
        listOf(
            36f to -9f, 43f to -9f, 44f to -2f, 48f to -5f, 51f to 2f,
            54f to 8f, 57f to 8f, 55f to 13f, 60f to 5f, 65f to 12f,
            71f to 25f, 70f to 31f, 66f to 40f, 69f to 60f, 55f to 60f,
            48f to 44f, 44f to 39f, 46f to 31f, 42f to 28f, 40f to 26f,
            37f to 23f, 40f to 19f, 44f to 13f, 40f to 18f, 38f to 16f,
            41f to 12f, 44f to 8f, 41f to 2f, 37f to -2f
        ),
        // Great Britain & Ireland
        listOf(
            50f to -5f, 52f to -5f, 55f to -5f, 58f to -6f, 58f to -2f,
            53f to 0f, 51f to 1f
        ),
        // Africa
        listOf(
            36f to -6f, 37f to 10f, 32f to 15f, 31f to 32f, 27f to 34f,
            18f to 38f, 12f to 44f, 11f to 51f, 2f to 45f, -5f to 39f,
            -14f to 40f, -25f to 35f, -34f to 26f, -34f to 18f, -22f to 14f,
            -12f to 13f, -2f to 9f, 4f to 8f, 5f to -4f, 8f to -13f,
            15f to -17f, 23f to -16f, 31f to -10f
        ),
        // Madagascar
        listOf(-12f to 49f, -18f to 50f, -25f to 47f, -25f to 44f, -16f to 45f),
        // Asia (Mainland + Arabia + India + SE Asia + Siberia)
        listOf(
            31f to 32f, 36f to 36f, 41f to 29f, 42f to 41f, 46f to 48f,
            55f to 60f, 69f to 60f, 73f to 80f, 76f to 105f, 72f to 135f,
            67f to 176f, 60f to 166f, 53f to 156f, 56f to 140f, 46f to 138f,
            40f to 129f, 35f to 129f, 37f to 126f, 40f to 122f, 35f to 119f,
            31f to 122f, 23f to 117f, 21f to 108f, 16f to 108f, 10f to 105f,
            13f to 101f, 4f to 103f, 2f to 104f, 7f to 99f, 15f to 97f,
            21f to 92f, 22f to 89f, 16f to 81f, 8f to 77f, 15f to 74f,
            23f to 69f, 25f to 62f, 26f to 56f, 29f to 49f, 24f to 52f,
            22f to 59f, 13f to 45f, 20f to 40f, 28f to 35f
        ),
        // Japan
        listOf(31f to 130f, 35f to 136f, 40f to 141f, 45f to 144f, 42f to 140f, 34f to 131f),
        // Sri Lanka
        listOf(9.7f to 80.1f, 7.6f to 81.8f, 6.0f to 80.5f, 7.8f to 79.8f),
        // Sumatra / Java / Borneo / Philippines islands
        listOf(5f to 95f, -5f to 105f, -8f to 114f, -8f to 106f, 2f to 98f),
        listOf(6f to 116f, 1f to 118f, -3f to 115f, -2f to 109f, 3f to 112f),
        listOf(18f to 121f, 11f to 126f, 6f to 125f, 13f to 120f),
        // Australia
        listOf(
            -12f to 131f, -12f to 136f, -15f to 136f, -11f to 142f, -19f to 148f,
            -27f to 153f, -37f to 150f, -38f to 141f, -33f to 134f, -32f to 125f,
            -35f to 116f, -26f to 113f, -20f to 118f, -15f to 125f
        ),
        // New Zealand
        listOf(-35f to 173f, -39f to 178f, -46f to 168f, -44f to 166f)
    )

    val selectableRegions: List<GeoRegionDefinition> = listOf(
        GeoRegionDefinition(
            id = "bgd", nameBn = "বাংলাদেশ", nameEn = "Bangladesh",
            centerLat = 23.7f, centerLon = 90.35f,
            rings = listOf(listOf(
                26.6f to 88.4f, 26.3f to 89.8f, 25.2f to 92.3f, 24.1f to 91.5f,
                23.2f to 91.3f, 20.8f to 92.3f, 21.8f to 90.5f, 21.7f to 89.1f,
                23.6f to 88.6f, 25.3f to 88.1f
            ))
        ),
        GeoRegionDefinition(
            id = "ind", nameBn = "ভারত", nameEn = "India",
            centerLat = 21.5f, centerLon = 78.9f,
            rings = listOf(listOf(
                35.5f to 74.5f, 34.5f to 78.5f, 30.5f to 79.5f, 28.0f to 88.0f,
                28.5f to 96.5f, 24.5f to 94.5f, 22.0f to 88.2f, 19.5f to 85.0f,
                13.0f to 80.3f, 8.1f to 77.5f, 15.0f to 73.9f, 21.5f to 72.5f,
                23.5f to 68.5f, 28.0f to 70.5f, 32.5f to 74.5f
            ))
        ),
        GeoRegionDefinition(
            id = "pak", nameBn = "পাকিস্তান", nameEn = "Pakistan",
            centerLat = 30.0f, centerLon = 69.3f,
            rings = listOf(listOf(
                36.8f to 73.0f, 35.0f to 76.0f, 31.0f to 74.5f, 25.0f to 68.0f,
                25.1f to 61.8f, 29.8f to 61.5f, 31.5f to 66.5f, 34.0f to 70.5f
            ))
        ),
        GeoRegionDefinition(
            id = "chn", nameBn = "চীন", nameEn = "China",
            centerLat = 35.8f, centerLon = 104.1f,
            rings = listOf(listOf(
                53.0f to 122.0f, 48.0f to 134.0f, 40.0f to 124.0f, 35.0f to 119.5f,
                31.0f to 122.0f, 22.0f to 114.0f, 22.5f to 101.0f, 28.0f to 96.0f,
                32.0f to 79.0f, 39.0f to 74.0f, 48.0f to 87.0f, 43.0f to 96.0f,
                49.0f to 116.0f
            ))
        ),
        GeoRegionDefinition(
            id = "rus", nameBn = "রাশিয়া", nameEn = "Russia",
            centerLat = 61.5f, centerLon = 95.0f,
            rings = listOf(listOf(
                69.5f to 31.0f, 68.0f to 60.0f, 73.0f to 80.0f, 76.0f to 105.0f,
                71.0f to 135.0f, 67.0f to 176.0f, 60.0f to 164.0f, 52.0f to 140.0f,
                44.0f to 133.0f, 49.0f to 117.0f, 50.0f to 86.0f, 51.0f to 54.0f,
                44.0f to 40.0f, 52.0f to 33.0f, 60.0f to 29.0f
            ))
        ),
        GeoRegionDefinition(
            id = "ukr", nameBn = "ইউক্রেন", nameEn = "Ukraine",
            centerLat = 49.0f, centerLon = 31.4f,
            rings = listOf(listOf(
                52.2f to 24.0f, 52.0f to 33.0f, 49.5f to 40.0f, 45.2f to 36.5f,
                44.4f to 33.5f, 46.5f to 30.5f, 48.2f to 22.5f
            ))
        ),
        GeoRegionDefinition(
            id = "usa", nameBn = "যুক্তরাষ্ট্র (USA)", nameEn = "United States",
            centerLat = 39.0f, centerLon = -98.5f,
            rings = listOf(listOf(
                49.0f to -124.5f, 49.0f to -95.0f, 45.0f to -83.0f, 47.0f to -67.5f,
                39.0f to -74.5f, 25.5f to -80.2f, 29.5f to -85.5f, 26.0f to -97.2f,
                31.5f to -106.5f, 32.5f to -117.0f, 42.0f to -124.3f
            ))
        ),
        GeoRegionDefinition(
            id = "tur", nameBn = "তুরস্ক", nameEn = "Turkey",
            centerLat = 39.0f, centerLon = 35.2f,
            rings = listOf(listOf(
                42.0f to 27.0f, 41.8f to 41.5f, 37.0f to 44.5f, 36.2f to 36.0f,
                36.5f to 27.5f, 40.5f to 26.2f
            ))
        ),
        GeoRegionDefinition(
            id = "irn", nameBn = "ইরান", nameEn = "Iran",
            centerLat = 32.4f, centerLon = 53.6f,
            rings = listOf(listOf(
                39.5f to 44.5f, 38.0f to 58.0f, 35.5f to 61.2f, 25.3f to 61.4f,
                27.0f to 56.0f, 30.0f to 48.5f, 35.5f to 45.5f
            ))
        ),
        GeoRegionDefinition(
            id = "sau", nameBn = "সৌদি আরব", nameEn = "Saudi Arabia",
            centerLat = 23.8f, centerLon = 45.0f,
            rings = listOf(listOf(
                32.0f to 39.0f, 29.0f to 48.0f, 24.0f to 51.5f, 17.0f to 52.0f,
                16.5f to 42.8f, 22.0f to 39.0f, 29.5f to 35.0f
            ))
        ),
        GeoRegionDefinition(
            id = "egy", nameBn = "মিশর", nameEn = "Egypt",
            centerLat = 26.8f, centerLon = 30.8f,
            rings = listOf(listOf(
                31.5f to 25.0f, 31.2f to 34.2f, 28.0f to 34.5f, 22.0f to 36.8f,
                22.0f to 25.0f
            ))
        ),
        GeoRegionDefinition(
            id = "gbr", nameBn = "যুক্তরাজ্য (UK)", nameEn = "United Kingdom",
            centerLat = 54.0f, centerLon = -2.0f,
            rings = listOf(listOf(
                50.1f to -5.5f, 55.0f to -5.2f, 58.6f to -5.0f, 58.4f to -1.8f,
                53.0f to 0.5f, 51.2f to 1.4f
            ))
        ),
        GeoRegionDefinition(
            id = "fra", nameBn = "ফ্রান্স", nameEn = "France",
            centerLat = 46.5f, centerLon = 2.2f,
            rings = listOf(listOf(
                51.0f to 2.5f, 49.0f to 8.1f, 44.0f to 7.5f, 42.5f to 3.1f,
                43.4f to -1.7f, 48.5f to -4.6f
            ))
        ),
        GeoRegionDefinition(
            id = "deu", nameBn = "জার্মানি", nameEn = "Germany",
            centerLat = 51.1f, centerLon = 10.4f,
            rings = listOf(listOf(
                54.9f to 8.5f, 54.0f to 14.2f, 50.5f to 14.9f, 47.6f to 13.0f,
                47.6f to 7.6f, 51.5f to 6.1f
            ))
        ),
        GeoRegionDefinition(
            id = "jpn", nameBn = "জাপান", nameEn = "Japan",
            centerLat = 36.2f, centerLon = 138.2f,
            rings = listOf(listOf(
                31.2f to 130.5f, 35.5f to 136.5f, 40.5f to 141.5f, 45.2f to 144.5f,
                42.0f to 140.0f, 34.0f to 131.0f
            ))
        ),
        // Historical Empires & Ancient Civilizations
        GeoRegionDefinition(
            id = "emp_bengal", nameBn = "বাংলা সালতানাত (ঐতিহাসিক)", nameEn = "Bengal Sultanate",
            isHistorical = true, centerLat = 24.3f, centerLon = 89.2f,
            rings = listOf(listOf(
                27.2f to 84.5f, 27.0f to 91.5f, 24.5f to 93.2f, 20.5f to 92.5f,
                19.5f to 85.2f, 23.0f to 84.0f
            ))
        ),
        GeoRegionDefinition(
            id = "emp_mughal", nameBn = "মোঘল সাম্রাজ্য (১৭০০)", nameEn = "Mughal Empire",
            isHistorical = true, centerLat = 26.5f, centerLon = 78.0f,
            rings = listOf(listOf(
                36.0f to 67.0f, 35.5f to 77.5f, 27.5f to 89.5f, 22.0f to 92.2f,
                12.5f to 80.2f, 13.0f to 74.5f, 23.5f to 68.5f, 30.0f to 62.0f
            ))
        ),
        GeoRegionDefinition(
            id = "emp_ottoman", nameBn = "উসমানীয় সাম্রাজ্য (১৬৮৩)", nameEn = "Ottoman Empire",
            isHistorical = true, centerLat = 39.5f, centerLon = 31.0f,
            rings = listOf(listOf(
                47.5f to 16.5f, 48.0f to 29.0f, 45.0f to 39.0f, 38.0f to 44.5f,
                30.0f to 48.0f, 16.0f to 43.0f, 22.0f to 36.0f, 32.0f to 13.0f,
                36.5f to 1.0f, 37.0f to 22.0f, 43.0f to 16.0f
            ))
        ),
        GeoRegionDefinition(
            id = "emp_roman", nameBn = "রোমান সাম্রাজ্য (১১৭ খ্রি.)", nameEn = "Roman Empire",
            isHistorical = true, centerLat = 41.9f, centerLon = 12.5f,
            rings = listOf(listOf(
                55.0f to -4.0f, 51.0f to 7.0f, 48.0f to 16.0f, 45.0f to 29.0f,
                38.0f to 42.0f, 30.0f to 36.0f, 26.0f to 32.0f, 35.5f to -6.0f,
                43.0f to -9.0f
            ))
        ),
        GeoRegionDefinition(
            id = "emp_persian", nameBn = "পারস্য সাম্রাজ্য", nameEn = "Persian Empire",
            isHistorical = true, centerLat = 32.0f, centerLon = 52.0f,
            rings = listOf(listOf(
                41.0f to 26.0f, 42.5f to 45.0f, 41.0f to 68.0f, 33.0f to 72.0f,
                25.0f to 66.0f, 26.0f to 48.0f, 30.5f to 31.0f, 37.0f to 27.0f
            ))
        ),
        GeoRegionDefinition(
            id = "civ_indus", nameBn = "সিন্ধু সভ্যতা (প্রাচীন)", nameEn = "Indus Valley Civ",
            isHistorical = true, centerLat = 28.5f, centerLon = 69.5f,
            rings = listOf(listOf(
                33.5f to 70.5f, 32.5f to 76.0f, 27.5f to 74.5f, 22.5f to 70.5f,
                25.0f to 64.5f, 30.5f to 66.0f
            ))
        ),
        GeoRegionDefinition(
            id = "civ_mesopotamia", nameBn = "মেসোপটেমিয়া সভ্যতা", nameEn = "Mesopotamia Civ",
            isHistorical = true, centerLat = 33.2f, centerLon = 44.0f,
            rings = listOf(listOf(
                37.5f to 38.5f, 36.5f to 45.5f, 30.0f to 48.5f, 30.5f to 43.0f, 35.0f to 38.0f
            ))
        )
    )

    val searchableCities: List<GeoCityEntry> = listOf(
        GeoCityEntry("dhaka", "ঢাকা", "Dhaka", "বাংলাদেশ", 23.8103f, 90.4125f),
        GeoCityEntry("ctg", "চট্টগ্রাম", "Chattogram", "বাংলাদেশ", 22.3569f, 91.7832f),
        GeoCityEntry("syl", "সিলেট", "Sylhet", "বাংলাদেশ", 24.8949f, 91.8687f),
        GeoCityEntry("khl", "খুলনা", "Khulna", "বাংলাদেশ", 22.8456f, 89.5403f),
        GeoCityEntry("raj", "রাজশাহী", "Rajshahi", "বাংলাদেশ", 24.3745f, 88.6042f),
        GeoCityEntry("rng", "রংপুর", "Rangpur", "বাংলাদেশ", 25.7439f, 89.2752f),
        GeoCityEntry("bar", "বরিশাল", "Barishal", "বাংলাদেশ", 22.7010f, 90.3535f),
        GeoCityEntry("mym", "ময়মনসিংহ", "Mymensingh", "বাংলাদেশ", 24.7471f, 90.4203f),
        GeoCityEntry("cxb", "কক্সবাজার", "Cox's Bazar", "বাংলাদেশ", 21.4272f, 92.0058f),
        GeoCityEntry("tek", "সেন্ট মার্টিন / টেকনাফ", "Teknaf", "বাংলাদেশ", 20.8644f, 92.2998f),
        GeoCityEntry("bay_bengal", "বঙ্গোপসাগর", "Bay of Bengal", "সাগর", 15.5f, 89.0f),
        GeoCityEntry("kol", "কলকাতা", "Kolkata", "ভারত", 22.5726f, 88.3639f),
        GeoCityEntry("del", "নয়াদিল্লি", "New Delhi", "ভারত", 28.6139f, 77.2090f),
        GeoCityEntry("bom", "মুম্বাই", "Mumbai", "ভারত", 19.0760f, 72.8777f),
        GeoCityEntry("che", "চেন্নাই", "Chennai", "ভারত", 13.0827f, 80.2707f),
        GeoCityEntry("isl", "ইসলামাবাদ", "Islamabad", "পাকিস্তান", 33.6844f, 73.0479f),
        GeoCityEntry("khi", "করাচি", "Karachi", "পাকিস্তান", 24.8607f, 67.0011f),
        GeoCityEntry("kbl", "কাবুল", "Kabul", "আফগানিস্তান", 34.5553f, 69.2075f),
        GeoCityEntry("ktm", "কাঠমান্ডু", "Kathmandu", "নেপাল", 27.7172f, 85.3240f),
        GeoCityEntry("cmb", "কলম্বো", "Colombo", "শ্রীলঙ্কা", 6.9271f, 79.8612f),
        GeoCityEntry("rgn", "ইয়াঙ্গুন", "Yangon", "মিয়ানমার", 16.8409f, 96.1735f),
        GeoCityEntry("bkk", "ব্যাংকক", "Bangkok", "থাইল্যান্ড", 13.7563f, 100.5018f),
        GeoCityEntry("sin", "সিঙ্গাপুর", "Singapore", "সিঙ্গাপুর", 1.3521f, 103.8198f),
        GeoCityEntry("kul", "কুয়ালালামপুর", "Kuala Lumpur", "মালয়েশিয়া", 3.1390f, 101.6869f),
        GeoCityEntry("jkt", "জাকার্তা", "Jakarta", "ইন্দোনেশিয়া", -6.2088f, 106.8456f),
        GeoCityEntry("pek", "বেইজিং", "Beijing", "চীন", 39.9042f, 116.4074f),
        GeoCityEntry("sha", "সাংহাই", "Shanghai", "চীন", 31.2304f, 121.4737f),
        GeoCityEntry("hkg", "হংকং", "Hong Kong", "চীন", 22.3193f, 114.1694f),
        GeoCityEntry("tyo", "টোকিও", "Tokyo", "জাপান", 35.6762f, 139.6503f),
        GeoCityEntry("sel", "সিউল", "Seoul", "দক্ষিণ কোরিয়া", 37.5665f, 126.9780f),
        GeoCityEntry("dxb", "দুবাই", "Dubai", "আমিরাত", 25.2048f, 55.2708f),
        GeoCityEntry("ruh", "রিয়াদ", "Riyadh", "সৌদি আরব", 24.7136f, 46.6753f),
        GeoCityEntry("mak", "মক্কা", "Makkah", "সৌদি আরব", 21.4225f, 39.8262f),
        GeoCityEntry("mad", "মদিনা", "Madinah", "সৌদি আরব", 24.4672f, 39.6112f),
        GeoCityEntry("thr", "তেহরান", "Tehran", "ইরান", 35.6892f, 51.3890f),
        GeoCityEntry("bgw", "বাগদাদ", "Baghdad", "ইরাক", 33.3152f, 44.3661f),
        GeoCityEntry("jrs", "জেরুজালেম", "Jerusalem", "ফিলিস্তিন", 31.7683f, 35.2137f),
        GeoCityEntry("gza", "গাজা", "Gaza", "ফিলিস্তিন", 31.5017f, 34.4668f),
        GeoCityEntry("ist", "ইস্তাম্বুল", "Istanbul", "তুরস্ক", 41.0082f, 28.9784f),
        GeoCityEntry("ank", "আঙ্কারা", "Ankara", "তুরস্ক", 39.9334f, 32.8597f),
        GeoCityEntry("cai", "কায়রো", "Cairo", "মিশর", 30.0444f, 31.2357f),
        GeoCityEntry("suz", "সুয়েজ খাল", "Suez Canal", "মিশর", 30.5852f, 32.2654f),
        GeoCityEntry("ath", "এথেন্স", "Athens", "গ্রিস", 37.9838f, 23.7275f),
        GeoCityEntry("rom", "রোম", "Rome", "ইতালি", 41.9028f, 12.4964f),
        GeoCityEntry("ber", "বার্লিন", "Berlin", "জার্মানি", 52.5200f, 13.4050f),
        GeoCityEntry("par", "প্যারিস", "Paris", "ফ্রান্স", 48.8566f, 2.3522f),
        GeoCityEntry("lon", "লন্ডন", "London", "যুক্তরাজ্য", 51.5074f, -0.1278f),
        GeoCityEntry("mad_es", "মাদ্রিদ", "Madrid", "স্পেন", 40.4168f, -3.7038f),
        GeoCityEntry("iev", "কিভ", "Kyiv", "ইউক্রেন", 50.4501f, 30.5234f),
        GeoCityEntry("mow", "মস্কো", "Moscow", "রাশিয়া", 55.7558f, 37.6173f),
        GeoCityEntry("spb", "সেন্ট পিটার্সবার্গ", "St. Petersburg", "রাশিয়া", 59.9343f, 30.3351f),
        GeoCityEntry("smq", "সমরকন্দ (সিল্ক রোড)", "Samarkand", "উজবেকিস্তান", 39.6542f, 66.9597f),
        GeoCityEntry("nbo", "নাইরোবি", "Nairobi", "কেনিয়া", -1.2921f, 36.8219f),
        GeoCityEntry("add", "আদ্দিস আবাবা", "Addis Ababa", "ইথিওপিয়া", 9.0320f, 38.7469f),
        GeoCityEntry("los", "লাগোস", "Lagos", "নাইজেরিয়া", 6.5244f, 3.3792f),
        GeoCityEntry("cpt", "কেপ টাউন", "Cape Town", "দক্ষিণ আফ্রিকা", -33.9249f, 18.4241f),
        GeoCityEntry("nyc", "নিউ ইয়র্ক", "New York", "যুক্তরাষ্ট্র", 40.7128f, -74.0060f),
        GeoCityEntry("was", "ওয়াশিংটন ডি.সি.", "Washington DC", "যুক্তরাষ্ট্র", 38.9072f, -77.0369f),
        GeoCityEntry("lax", "লস অ্যাঞ্জেলেস", "Los Angeles", "যুক্তরাষ্ট্র", 34.0522f, -118.2437f),
        GeoCityEntry("tor", "টরন্টো", "Toronto", "কানাডা", 43.6532f, -79.3832f),
        GeoCityEntry("mex", "মেক্সিকো সিটি", "Mexico City", "মেক্সিকো", 19.4326f, -99.1332f),
        GeoCityEntry("rio", "রিও ডি জেনিরো", "Rio de Janeiro", "ব্রাজিল", -22.9068f, -43.1729f),
        GeoCityEntry("bue", "বুয়েনস আইরেস", "Buenos Aires", "আর্জেন্টিনা", -34.6037f, -58.3816f),
        GeoCityEntry("syd", "সিডনি", "Sydney", "অস্ট্রেলিয়া", -33.8688f, 151.2093f)
    )

    fun evaluateCameraAt(config: UniversalMapConfig, rawTimeFrac: Float): EvaluatedMapCamera {
        val kfs = config.keyframes.sortedBy { it.timeFraction }
        if (kfs.isEmpty()) {
            return EvaluatedMapCamera(23.81f, 90.41f, 1.8f, 0f, 0f)
        }
        val t = rawTimeFrac.coerceIn(0f, 1f)
        if (t <= kfs.first().timeFraction || kfs.size == 1) {
            val k = kfs.first()
            return EvaluatedMapCamera(k.centerLat, k.centerLon, k.zoom, k.rotationDeg, k.tiltDeg)
        }
        if (t >= kfs.last().timeFraction) {
            val k = kfs.last()
            return EvaluatedMapCamera(k.centerLat, k.centerLon, k.zoom, k.rotationDeg, k.tiltDeg)
        }
        val idx = kfs.indexOfLast { it.timeFraction <= t }.coerceIn(0, kfs.lastIndex - 1)
        val a = kfs[idx]
        val b = kfs[idx + 1]
        val span = (b.timeFraction - a.timeFraction).coerceAtLeast(0.0001f)
        val rawFrac = ((t - a.timeFraction) / span).coerceIn(0f, 1f)
        val eased = when (b.easing) {
            MapCameraEasing.LINEAR -> rawFrac
            MapCameraEasing.EASE_IN_OUT -> {
                if (rawFrac < 0.5f) 4f * rawFrac * rawFrac * rawFrac
                else 1f - (-2f * rawFrac + 2f).pow(3f) / 2f
            }
            MapCameraEasing.CINEMA_FLIGHT -> {
                1f - (1f - rawFrac).pow(3f)
            }
        }
        val baseLat = a.centerLat + (b.centerLat - a.centerLat) * eased
        // Shortest longitudinal wrap (-180..180)
        var dLon = b.centerLon - a.centerLon
        if (dLon > 180f) dLon -= 360f
        if (dLon < -180f) dLon += 360f
        val baseLon = normalizeLon(a.centerLon + dLon * eased)
        var zoom = a.zoom + (b.zoom - a.zoom) * eased
        if (b.easing == MapCameraEasing.CINEMA_FLIGHT) {
            // Smooth parabolic zoom-out mid-flight arc like Google Earth / cinema flyover
            val arc = sin(rawFrac * PI).toFloat()
            val distDeg = sqrt((b.centerLat - a.centerLat).pow(2) + dLon.pow(2))
            val dipFactor = (distDeg / 140f).coerceIn(0.12f, 0.52f)
            zoom *= (1f - arc * dipFactor)
        }
        val rot = a.rotationDeg + (b.rotationDeg - a.rotationDeg) * eased
        val tilt = a.tiltDeg + (b.tiltDeg - a.tiltDeg) * eased
        return EvaluatedMapCamera(
            centerLat = baseLat.coerceIn(-85f, 85f),
            centerLon = baseLon,
            zoom = zoom.coerceIn(0.6f, 18f),
            rotationDeg = rot,
            tiltDeg = tilt.coerceIn(0f, 65f)
        )
    }

    fun normalizeLon(lon: Float): Float {
        var r = ((lon + 180f) % 360f)
        if (r < 0f) r += 360f
        return r - 180f
    }

    fun projectLatLon(
        lat: Float,
        lon: Float,
        camera: EvaluatedMapCamera,
        mode: MapProjectionMode,
        width: Float,
        height: Float
    ): ProjectedScreenPoint {
        val cx = width * 0.5f
        val cy = height * 0.5f
        return when (mode) {
            MapProjectionMode.WORLD_2D -> {
                val scale = (minOf(width, height) / 155f) * camera.zoom
                var dLon = lon - camera.centerLon
                if (dLon > 180f) dLon -= 360f
                if (dLon < -180f) dLon += 360f
                val dLat = camera.centerLat - lat
                var px = dLon * scale
                var py = dLat * scale * (1f - (camera.tiltDeg.coerceIn(0f, 65f) / 140f))
                if (abs(camera.rotationDeg) > 0.05f) {
                    val rad = Math.toRadians(camera.rotationDeg.toDouble())
                    val cr = cos(rad).toFloat()
                    val sr = sin(rad).toFloat()
                    val rx = px * cr - py * sr
                    val ry = px * sr + py * cr
                    px = rx
                    py = ry
                }
                // Perspective keystone subtle compression when tilted in 2D map mode
                val tiltFactor = camera.tiltDeg.coerceIn(0f, 65f) / 65f
                if (tiltFactor > 0.02f) {
                    val depthScale = (1f + (py / height) * 0.38f * tiltFactor).coerceIn(0.55f, 1.45f)
                    px *= depthScale
                }
                ProjectedScreenPoint(cx + px, cy + py, visible = true, depthZ = 1f)
            }
            MapProjectionMode.GLOBE_3D -> {
                val baseRadius = minOf(width, height) * 0.38f * (0.72f + (camera.zoom - 1f) * 0.42f).coerceIn(0.65f, 5.2f)
                val latR = Math.toRadians(lat.toDouble())
                val lonR = Math.toRadians(lon.toDouble())
                val effCenterLat = (camera.centerLat - camera.tiltDeg * 0.35f).coerceIn(-85f, 85f)
                val lat0 = Math.toRadians(effCenterLat.toDouble())
                val lon0 = Math.toRadians(camera.centerLon.toDouble())

                val cosC = sin(lat0) * sin(latR) + cos(lat0) * cos(latR) * cos(lonR - lon0)
                val xNorm = (cos(latR) * sin(lonR - lon0)).toFloat()
                val yNorm = -(cos(lat0) * sin(latR) - sin(lat0) * cos(latR) * cos(lonR - lon0)).toFloat()

                var px = xNorm * baseRadius
                var py = yNorm * baseRadius
                if (abs(camera.rotationDeg) > 0.05f) {
                    val rad = Math.toRadians(camera.rotationDeg.toDouble())
                    val cr = cos(rad).toFloat()
                    val sr = sin(rad).toFloat()
                    val rx = px * cr - py * sr
                    val ry = px * sr + py * cr
                    px = rx
                    py = ry
                }
                ProjectedScreenPoint(
                    x = cx + px,
                    y = cy + py,
                    visible = cosC >= -0.02,
                    depthZ = cosC.toFloat()
                )
            }
        }
    }

    /**
     * Converts a user tap or drag screen offset (x, y) back into exact geographic (Lat, Lon),
     * supporting both rotated/tilted 2D World Map and 3D Orthographic Globe.
     */
    fun unprojectScreenPoint(
        screenX: Float,
        screenY: Float,
        camera: EvaluatedMapCamera,
        mode: MapProjectionMode,
        width: Float,
        height: Float
    ): MapLatLon? {
        if (width <= 0f || height <= 0f) return null
        var px = screenX - width * 0.5f
        var py = screenY - height * 0.5f

        if (abs(camera.rotationDeg) > 0.05f) {
            val rad = Math.toRadians((-camera.rotationDeg).toDouble())
            val cr = cos(rad).toFloat()
            val sr = sin(rad).toFloat()
            val rx = px * cr - py * sr
            val ry = px * sr + py * cr
            px = rx
            py = ry
        }

        return when (mode) {
            MapProjectionMode.WORLD_2D -> {
                val scale = (minOf(width, height) / 155f) * camera.zoom
                val tiltFactor = camera.tiltDeg.coerceIn(0f, 65f) / 65f
                if (tiltFactor > 0.02f) {
                    val depthScale = (1f + (py / height) * 0.38f * tiltFactor).coerceIn(0.55f, 1.45f)
                    px /= depthScale
                }
                val dLon = px / scale.coerceAtLeast(0.01f)
                val dLat = py / (scale * (1f - (camera.tiltDeg.coerceIn(0f, 65f) / 140f))).coerceAtLeast(0.01f)
                MapLatLon(
                    lat = (camera.centerLat - dLat).coerceIn(-85f, 85f),
                    lon = normalizeLon(camera.centerLon + dLon)
                )
            }
            MapProjectionMode.GLOBE_3D -> {
                val baseRadius = minOf(width, height) * 0.38f * (0.72f + (camera.zoom - 1f) * 0.42f).coerceIn(0.65f, 5.2f)
                val xNorm = (px / baseRadius).toDouble()
                val yNorm = -(py / baseRadius).toDouble()
                val rho = sqrt(xNorm * xNorm + yNorm * yNorm)
                if (rho > 1.0) return null
                if (rho < 1e-5) {
                    val effCenterLat = (camera.centerLat - camera.tiltDeg * 0.35f).coerceIn(-85f, 85f)
                    return MapLatLon(effCenterLat, camera.centerLon)
                }
                val c = asin(rho.coerceIn(-1.0, 1.0))
                val effCenterLat = (camera.centerLat - camera.tiltDeg * 0.35f).coerceIn(-85f, 85f)
                val lat0 = Math.toRadians(effCenterLat.toDouble())
                val lon0 = Math.toRadians(camera.centerLon.toDouble())
                val sinC = sin(c)
                val cosC = cos(c)
                val latR = asin((cosC * sin(lat0) + (yNorm * sinC * cos(lat0)) / rho).coerceIn(-1.0, 1.0))
                val lonR = lon0 + atan2(
                    xNorm * sinC,
                    rho * cos(lat0) * cosC - yNorm * sin(lat0) * sinC
                )
                MapLatLon(
                    lat = Math.toDegrees(latR).toFloat().coerceIn(-85f, 85f),
                    lon = normalizeLon(Math.toDegrees(lonR).toFloat())
                )
            }
        }
    }

    /**
     * Generates smooth spherical great-circle (or curved geodesic) intermediate points between A and B.
     */
    fun interpolateGeodesic(a: MapLatLon, b: MapLatLon, steps: Int = 24): List<MapLatLon> {
        val lat1 = Math.toRadians(a.lat.toDouble())
        val lon1 = Math.toRadians(a.lon.toDouble())
        val lat2 = Math.toRadians(b.lat.toDouble())
        val lon2 = Math.toRadians(b.lon.toDouble())

        val x1 = cos(lat1) * cos(lon1)
        val y1 = cos(lat1) * sin(lon1)
        val z1 = sin(lat1)

        val x2 = cos(lat2) * cos(lon2)
        val y2 = cos(lat2) * sin(lon2)
        val z2 = sin(lat2)

        val dot = (x1 * x2 + y1 * y2 + z1 * z2).coerceIn(-1.0, 1.0)
        val omega = kotlin.math.acos(dot)
        if (omega < 0.002) {
            return (0..steps).map { i ->
                val f = i.toFloat() / steps
                MapLatLon(a.lat + (b.lat - a.lat) * f, a.lon + (b.lon - a.lon) * f)
            }
        }
        val sinOmega = sin(omega)
        return (0..steps).map { i ->
            val t = i.toDouble() / steps
            val s1 = sin((1.0 - t) * omega) / sinOmega
            val s2 = sin(t * omega) / sinOmega
            val x = s1 * x1 + s2 * x2
            val y = s1 * y1 + s2 * y2
            val z = s1 * z1 + s2 * z2
            val latOut = Math.toDegrees(atan2(z, sqrt(x * x + y * y))).toFloat()
            val lonOut = Math.toDegrees(atan2(y, x)).toFloat()
            MapLatLon(latOut, lonOut)
        }
    }
}
