package com.example.sensors

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.domain.solat.CalculationMethod
import com.example.domain.solat.SolatCalculator
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.TimeZone

data class ResolvedGeoLocation(
    val latitude: Double,
    val longitude: Double,
    val label: String,
    val timeZoneId: String,
    val suggestedMethod: CalculationMethod,
    val jakimZone: String?
)

class GeolocationHelper(private val context: Context) {

    fun hasCoarseLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    suspend fun fetchCurrentLocation(): ResolvedGeoLocation? = withContext(Dispatchers.IO) {
        if (!hasCoarseLocationPermission()) return@withContext null
        val client = LocationServices.getFusedLocationProviderClient(context)
        val location = runCatching {
            val tokenSource = CancellationTokenSource()
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, tokenSource.token).await()
        }.getOrNull() ?: runCatching { client.lastLocation.await() }.getOrNull() ?: return@withContext null

        val lat = location.latitude
        val lon = location.longitude
        val tzId = TimeZone.getDefault().id
        val jakimZone = SolatCalculator.inferDefaultJakimZone(lat, lon)
        val suggestedMethod = when {
            jakimZone != null -> CalculationMethod.JAKIM
            lat in 1.1..1.5 && lon in 103.5..104.1 -> CalculationMethod.SINGAPORE
            lat in 24.0..50.0 && lon in -130.0..-60.0 -> CalculationMethod.ISNA
            else -> CalculationMethod.MWL
        }

        val label = resolveCityName(lat, lon) ?: String.format(Locale.US, "%.2f°, %.2f°", lat, lon)
        ResolvedGeoLocation(
            latitude = lat,
            longitude = lon,
            label = label,
            timeZoneId = tzId,
            suggestedMethod = suggestedMethod,
            jakimZone = jakimZone
        )
    }

    @Suppress("DEPRECATION")
    private fun resolveCityName(lat: Double, lon: Double): String? = runCatching {
        if (!Geocoder.isPresent()) return@runCatching null
        val geocoder = Geocoder(context, Locale.getDefault())
        val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            geocoder.getFromLocation(lat, lon, 1)
        } else {
            geocoder.getFromLocation(lat, lon, 1)
        }
        val addr = addresses?.firstOrNull() ?: return@runCatching null
        val city = addr.locality ?: addr.subAdminArea ?: addr.adminArea
        val country = addr.countryName
        listOfNotNull(city, country).joinToString(", ").ifBlank { null }
    }.getOrNull()
}
