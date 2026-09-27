package com.example.location

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.UserPreferences
import com.example.i18n.AppStrings
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.resume

data class LocationInfo(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracy: Float = 0f,
    val address: String = "Определение адреса...",
    val streetOnly: String = "",
    val timestamp: Long = 0L,
    val isLocating: Boolean = false
)

class LocationHelper(
    private val context: Context,
    private val preferences: UserPreferences? = null
) {
    private val TAG = "LocationHelper"

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private val helperScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _locationState = MutableStateFlow(LocationInfo())
    val locationState: StateFlow<LocationInfo> = _locationState.asStateFlow()

    private var fusedLocationCallback: LocationCallback? = null
    private var lastKnownLocation: Location? = null

    private val fallbackLocationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            updateLocation(location)
        }
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    private fun getTargetLocale(): Pair<Locale, String> {
        val langCode = preferences?.getSettings()?.appLanguage ?: "ru"
        return when (langCode.lowercase(Locale.ROOT)) {
            "kk" -> Pair(Locale("kk", "KZ"), "kk")
            "en" -> Pair(Locale.ENGLISH, "en")
            else -> Pair(Locale("ru", "RU"), "ru")
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    fun requestSingleUpdate() {
        val langCode = preferences?.getSettings()?.appLanguage ?: "ru"
        val strings = AppStrings.get(langCode)

        if (!hasLocationPermission()) {
            _locationState.value = _locationState.value.copy(
                address = strings.permissionWarningBanner,
                isLocating = false
            )
            return
        }

        _locationState.value = _locationState.value.copy(
            address = strings.addressSearching,
            isLocating = true
        )

        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    updateLocation(loc)
                }
            }

            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                4000L
            ).setMinUpdateIntervalMillis(2000L)
                .setMaxUpdateDelayMillis(5000L)
                .build()

            fusedLocationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }

            fusedLocationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { location ->
                        updateLocation(location)
                    }
                }
            }

            fusedLocationCallback?.let { cb ->
                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    cb,
                    Looper.getMainLooper()
                )
            }

            locationManager?.let { lm ->
                if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    lm.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        5000L,
                        3f,
                        fallbackLocationListener,
                        Looper.getMainLooper()
                    )
                } else if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    lm.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        5000L,
                        3f,
                        fallbackLocationListener,
                        Looper.getMainLooper()
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting location updates", e)
            _locationState.value = _locationState.value.copy(
                address = strings.addressSearching,
                isLocating = false
            )
        }
    }

    fun stopLocationUpdates() {
        try {
            fusedLocationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
            locationManager?.removeUpdates(fallbackLocationListener)
            _locationState.value = _locationState.value.copy(isLocating = false)
        } catch (_: Exception) {}
    }

    fun refreshAddressForLanguage() {
        val loc = lastKnownLocation
        if (loc != null) {
            updateLocation(loc)
        } else {
            requestSingleUpdate()
        }
    }

    private fun updateLocation(location: Location) {
        lastKnownLocation = location
        val lat = location.latitude
        val lng = location.longitude
        val acc = location.accuracy

        helperScope.launch {
            val (fullAddress, street) = resolveCleanAddress(lat, lng)
            withContext(Dispatchers.Main) {
                _locationState.value = LocationInfo(
                    latitude = lat,
                    longitude = lng,
                    accuracy = acc,
                    address = fullAddress,
                    streetOnly = street,
                    timestamp = System.currentTimeMillis(),
                    isLocating = false
                )
            }
        }
    }

    suspend fun getCurrentAddressDirectly(): String = withContext(Dispatchers.IO) {
        val langCode = preferences?.getSettings()?.appLanguage ?: "ru"
        val strings = AppStrings.get(langCode)
        val state = _locationState.value
        if (state.latitude != 0.0 && state.longitude != 0.0 &&
            state.address.isNotBlank() &&
            !state.address.startsWith("Определение") &&
            !state.address.startsWith("Поиск") &&
            !state.address.startsWith("Мекенжайды") &&
            !state.address.startsWith("Searching")
        ) {
            return@withContext state.address
        }

        if (!hasLocationPermission()) {
            return@withContext when (langCode) {
                "kk" -> "координаттар анықталмады (GPS рұқсаты жоқ)"
                "en" -> "coordinates unavailable (no GPS permission)"
                else -> "координаты не определены (нет доступа к GPS)"
            }
        }

        try {
            val freshLoc = suspendCancellableCoroutine<Location?> { cont ->
                try {
                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                        .addOnSuccessListener { loc ->
                            if (cont.isActive) cont.resume(loc)
                        }
                        .addOnFailureListener {
                            if (cont.isActive) cont.resume(null)
                        }
                } catch (_: Exception) {
                    if (cont.isActive) cont.resume(null)
                }
            }

            if (freshLoc != null) {
                lastKnownLocation = freshLoc
                val (addr, _) = resolveCleanAddress(freshLoc.latitude, freshLoc.longitude)
                return@withContext addr
            }
        } catch (_: Exception) {}

        when (langCode) {
            "kk" -> "координаттар анықталмады"
            "en" -> "coordinates unavailable"
            else -> "координаты не определены"
        }
    }

    suspend fun resolveCleanAddress(lat: Double, lng: Double): Pair<String, String> = withContext(Dispatchers.IO) {
        val (targetLocale, langCode) = getTargetLocale()

        try {
            val geocoder = Geocoder(context, targetLocale)
            val addresses: List<Address>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocation(lat, lng, 3)
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(lat, lng, 3)
            }

            if (!addresses.isNullOrEmpty()) {
                val parsed = formatAddressFromComponents(addresses[0], lat, lng, langCode)
                if (parsed != null && isCleanAddress(parsed.first)) {
                    return@withContext parsed
                }
            }

            if (langCode == "kk") {
                val ruGeocoder = Geocoder(context, Locale("ru", "RU"))
                val ruAddresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ruGeocoder.getFromLocation(lat, lng, 3)
                } else {
                    @Suppress("DEPRECATION")
                    ruGeocoder.getFromLocation(lat, lng, 3)
                }
                if (!ruAddresses.isNullOrEmpty()) {
                    val parsed = formatAddressFromComponents(ruAddresses[0], lat, lng, "kk")
                    if (parsed != null && isCleanAddress(parsed.first)) {
                        return@withContext parsed
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Native Geocoder failed: ${e.message}")
        }

        try {
            val osmResult = fetchOsmReverseGeocode(lat, lng, langCode)
            if (osmResult != null && isCleanAddress(osmResult.first)) {
                return@withContext osmResult
            }
        } catch (e: Exception) {
            Log.w(TAG, "OSM reverse geocode failed: ${e.message}")
        }

        val fallbackCoords = when (langCode) {
            "kk" -> "Ендік %.5f, Бойлық %.5f".format(Locale.US, lat, lng)
            "en" -> "Lat %.5f, Lng %.5f".format(Locale.US, lat, lng)
            else -> "Широта %.5f, Долгота %.5f".format(Locale.US, lat, lng)
        }
        Pair(fallbackCoords, fallbackCoords)
    }

    private fun isCleanAddress(addr: String): Boolean {
        if (addr.isBlank()) return false
        if (addr.startsWith("Широта") || addr.startsWith("Latitude") || addr.startsWith("Ендік") || addr.startsWith("Lat")) return false
        if (addr.length < 5) return false
        return true
    }

    private fun formatAddressFromComponents(address: Address, lat: Double, lng: Double, langCode: String): Pair<String, String>? {
        val thoroughfare = address.thoroughfare
        val subThoroughfare = address.subThoroughfare
        val featureName = address.featureName
        val locality = address.locality ?: address.subAdminArea ?: address.adminArea ?: ""

        val streetPart = StringBuilder()

        if (!thoroughfare.isNullOrBlank()) {
            val cleanThoroughfare = thoroughfare.trim()
            val houseNumber = when {
                !subThoroughfare.isNullOrBlank() -> subThoroughfare.trim()
                !featureName.isNullOrBlank() && featureName.trim() != cleanThoroughfare && featureName.any { it.isDigit() } -> featureName.trim()
                else -> null
            }

            if (houseNumber != null) {
                when (langCode) {
                    "kk" -> {
                        if (cleanThoroughfare.contains("көше", ignoreCase = true) || cleanThoroughfare.contains("даңғылы", ignoreCase = true)) {
                            streetPart.append("$cleanThoroughfare, $houseNumber үй")
                        } else {
                            streetPart.append("$cleanThoroughfare к-сі, $houseNumber үй")
                        }
                    }
                    "en" -> {
                        streetPart.append("$houseNumber $cleanThoroughfare")
                    }
                    else -> {
                        if (cleanThoroughfare.startsWith("ул", ignoreCase = true) || cleanThoroughfare.startsWith("просп", ignoreCase = true)) {
                            streetPart.append("$cleanThoroughfare, д. $houseNumber")
                        } else {
                            streetPart.append("ул. $cleanThoroughfare, д. $houseNumber")
                        }
                    }
                }
            } else {
                streetPart.append(cleanThoroughfare)
            }
        }

        val fullAddressBuilder = StringBuilder()
        if (streetPart.isNotEmpty()) {
            fullAddressBuilder.append(streetPart.toString())
            if (locality.isNotBlank() && !streetPart.contains(locality, ignoreCase = true)) {
                fullAddressBuilder.append(", ").append(locality.trim())
            }
        } else if (address.maxAddressLineIndex >= 0) {
            val line = address.getAddressLine(0)
            if (!line.isNullOrBlank()) {
                fullAddressBuilder.append(cleanAddressLine(line))
            }
        }

        if (fullAddressBuilder.isNotEmpty()) {
            val full = fullAddressBuilder.toString()
            val street = streetPart.toString().ifBlank { full }
            return Pair(full, street)
        }

        return null
    }

    private fun cleanAddressLine(rawLine: String): String {
        var cleaned = rawLine.trim()
        cleaned = cleaned.replace(Regex("[A-Z0-9]{4}\\+[A-Z0-9]{2,8}"), "").trim()
        return cleaned.trimEnd(',', ' ')
    }

    private fun fetchOsmReverseGeocode(lat: Double, lng: Double, langCode: String): Pair<String, String>? {
        var connection: HttpURLConnection? = null
        return try {
            val acceptLangParam = when (langCode) {
                "kk" -> "kk,ru;q=0.9,en;q=0.3"
                "en" -> "en,ru;q=0.8"
                else -> "ru,kk;q=0.8,en;q=0.3"
            }
            val urlString = "https://nominatim.openstreetmap.org/reverse?format=json&lat=$lat&lon=$lng&zoom=18&addressdetails=1&accept-language=$acceptLangParam"
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "QorghanFallDetector/1.0 (Android App; Emergency Fall Assistance)")
            connection.setRequestProperty("Accept-Language", acceptLangParam)
            connection.connectTimeout = 6000
            connection.readTimeout = 6000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream, "UTF-8"))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()

                val json = JSONObject(response.toString())
                val addressObj = json.optJSONObject("address")
                if (addressObj != null) {
                    val road = addressObj.optString("road", "").ifBlank {
                        addressObj.optString("pedestrian", "").ifBlank {
                            addressObj.optString("street", "")
                        }
                    }
                    val houseNumber = addressObj.optString("house_number", "")
                    val city = addressObj.optString("city", "").ifBlank {
                        addressObj.optString("town", "").ifBlank {
                            addressObj.optString("village", "").ifBlank {
                                addressObj.optString("municipality", "")
                            }
                        }
                    }

                    val streetBuilder = StringBuilder()
                    if (road.isNotBlank()) {
                        if (houseNumber.isNotBlank()) {
                            when (langCode) {
                                "kk" -> streetBuilder.append("$road, $houseNumber үй")
                                "en" -> streetBuilder.append("$houseNumber $road")
                                else -> streetBuilder.append("ул. $road, д. $houseNumber")
                            }
                        } else {
                            streetBuilder.append(road)
                        }
                    }

                    val fullBuilder = StringBuilder()
                    if (streetBuilder.isNotEmpty()) {
                        fullBuilder.append(streetBuilder.toString())
                        if (city.isNotBlank()) {
                            fullBuilder.append(", ").append(city)
                        }
                    } else {
                        val displayName = json.optString("display_name", "")
                        if (displayName.isNotBlank()) {
                            val parts = displayName.split(",").map { it.trim() }
                            fullBuilder.append(parts.take(3).joinToString(", "))
                        }
                    }

                    if (fullBuilder.isNotEmpty()) {
                        val full = fullBuilder.toString()
                        val street = streetBuilder.toString().ifBlank { full }
                        return Pair(full, street)
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "OSM reverse geocoding network error", e)
            null
        } finally {
            connection?.disconnect()
        }
    }
}
