package com.example.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class IbadahPlaceCategory(val label: String) {
    ALL("All Places"),
    MOSQUE("Mosque / Masjid"),
    SURAU("Surau"),
    MUSALLA("Prayer Room / Musalla")
}

data class IbadahPlace(
    val id: String,
    val name: String,
    val category: IbadahPlaceCategory,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Int,
    val addressLine: String,
    val facilityTags: List<String>
) {
    val formattedDistance: String
        get() = if (distanceMeters < 1000) {
            "$distanceMeters m"
        } else {
            String.format(Locale.US, "%.1f km", distanceMeters / 1000.0)
        }

    fun googleMapsWebUrl(): String {
        val encoded = URLEncoder.encode("$name $addressLine".trim(), "UTF-8")
        return "https://www.google.com/maps/search/?api=1&query=$latitude,$longitude&query_place_id=&q=$encoded"
    }

    fun googleMapsGeoUri(): String {
        val encoded = URLEncoder.encode(name, "UTF-8")
        return "geo:$latitude,$longitude?q=$latitude,$longitude($encoded)"
    }
}

class MosqueFinderRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .build()

    suspend fun findNearbyIbadahPlaces(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int = 5000
    ): Result<List<IbadahPlace>> = withContext(Dispatchers.IO) {
        val safeRadius = radiusMeters.coerceIn(1000, 15000)
        val overpassQuery = """
            [out:json][timeout:15];
            (
              node["amenity"="place_of_worship"]["religion"="muslim"](around:$safeRadius,$latitude,$longitude);
              way["amenity"="place_of_worship"]["religion"="muslim"](around:$safeRadius,$latitude,$longitude);
              relation["amenity"="place_of_worship"]["religion"="muslim"](around:$safeRadius,$latitude,$longitude);
              node["building"="mosque"](around:$safeRadius,$latitude,$longitude);
              way["building"="mosque"](around:$safeRadius,$latitude,$longitude);
              node["amenity"="prayer_room"]["religion"="muslim"](around:$safeRadius,$latitude,$longitude);
            );
            out center tags 40;
        """.trimIndent()

        val endpoints = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter"
        )

        var lastError: Throwable? = null
        for (endpoint in endpoints) {
            try {
                val formBody = FormBody.Builder()
                    .add("data", overpassQuery)
                    .build()
                val request = Request.Builder()
                    .url(endpoint)
                    .header("User-Agent", "NoorCompanionAndroid/1.0")
                    .post(formBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        lastError = IllegalStateException("Directory returned HTTP ${response.code}")
                        return@use
                    }
                    val body = response.body?.string().orEmpty()
                    if (body.isBlank()) {
                        lastError = IllegalStateException("Empty directory response")
                        return@use
                    }
                    val parsed = parseOverpassElements(
                        jsonText = body,
                        originLat = latitude,
                        originLon = longitude
                    )
                    return@withContext Result.success(parsed)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Overpass endpoint $endpoint failed: ${t.message}")
                lastError = t
            }
        }

        Result.failure(lastError ?: IllegalStateException("Unable to load nearby mosques right now."))
    }

    private fun parseOverpassElements(
        jsonText: String,
        originLat: Double,
        originLon: Double
    ): List<IbadahPlace> {
        val root = JSONObject(jsonText)
        val elements = root.optJSONArray("elements") ?: return emptyList()
        val places = mutableListOf<IbadahPlace>()
        val seenKeys = mutableSetOf<String>()

        for (i in 0 until elements.length()) {
            val item = elements.optJSONObject(i) ?: continue
            val tags = item.optJSONObject("tags") ?: continue
            val center = item.optJSONObject("center")
            val lat = when {
                item.has("lat") -> item.optDouble("lat", Double.NaN)
                center != null -> center.optDouble("lat", Double.NaN)
                else -> Double.NaN
            }
            val lon = when {
                item.has("lon") -> item.optDouble("lon", Double.NaN)
                center != null -> center.optDouble("lon", Double.NaN)
                else -> Double.NaN
            }
            if (lat.isNaN() || lon.isNaN()) continue

            val rawName = tags.optString("name:en")
                .ifBlank { tags.optString("name") }
                .ifBlank { tags.optString("official_name") }
                .ifBlank { "Nearby Prayer Place" }

            val dedupeKey = "${rawName.lowercase(Locale.US)}_${(lat * 1000).toInt()}_${(lon * 1000).toInt()}"
            if (!seenKeys.add(dedupeKey)) continue

            val category = classifyCategory(rawName, tags)
            val street = listOf(
                tags.optString("addr:street"),
                tags.optString("addr:suburb").ifBlank { tags.optString("addr:city") }
            ).filter { it.isNotBlank() }.joinToString(", ")
                .ifBlank { tags.optString("addr:full") }

            val facilities = buildList {
                if (tags.optString("wheelchair") == "yes") add("Wheelchair Access")
                if (tags.optString("female") == "yes" || tags.optString("women") == "yes") add("Women's Section")
                val hours = tags.optString("opening_hours")
                if (hours.isNotBlank()) add(hours)
                val denom = tags.optString("denomination")
                if (denom.isNotBlank()) add(denom.replaceFirstChar { it.uppercaseChar() })
            }

            val dist = haversineMeters(originLat, originLon, lat, lon)
            places += IbadahPlace(
                id = "${item.optString("type")}_${item.optLong("id")}",
                name = rawName,
                category = category,
                latitude = lat,
                longitude = lon,
                distanceMeters = dist,
                addressLine = street,
                facilityTags = facilities
            )
        }

        return places.sortedBy { it.distanceMeters }
    }

    private fun classifyCategory(name: String, tags: JSONObject): IbadahPlaceCategory {
        val lower = name.lowercase(Locale.US)
        val amenity = tags.optString("amenity").lowercase(Locale.US)
        val building = tags.optString("building").lowercase(Locale.US)
        return when {
            "surau" in lower -> IbadahPlaceCategory.SURAU
            "musalla" in lower || "musolla" in lower || "prayer room" in lower || amenity == "prayer_room" ->
                IbadahPlaceCategory.MUSALLA
            "masjid" in lower || "mosque" in lower || building == "mosque" ->
                IbadahPlaceCategory.MOSQUE
            else -> IbadahPlaceCategory.MOSQUE
        }
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Int {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return (r * c).toInt().coerceAtLeast(1)
    }

    private companion object {
        const val TAG = "MosqueFinderRepo"
    }
}
