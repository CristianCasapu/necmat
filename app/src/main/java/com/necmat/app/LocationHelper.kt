package com.necmat.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

/** Detectarea locației + adresa prin OpenStreetMap (Nominatim). */
object LocationHelper {

    val PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    fun hasPermission(context: Context): Boolean = PERMISSIONS.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /** Locația curentă (GPS sau rețea), cu limită de timp și rezervă pe ultima cunoscută. */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(context: Context): Location? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) return null

        for (provider in providers) {
            val fresh = withTimeoutOrNull(12_000) {
                suspendCancellableCoroutine<Location?> { cont ->
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            lm.getCurrentLocation(
                                provider, null,
                                ContextCompat.getMainExecutor(context)
                            ) { loc -> if (cont.isActive) cont.resume(loc) }
                        } else {
                            @Suppress("DEPRECATION")
                            lm.requestSingleUpdate(
                                provider,
                                { loc -> if (cont.isActive) cont.resume(loc) },
                                context.mainLooper
                            )
                        }
                    } catch (e: Exception) {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            }
            if (fresh != null) return fresh
        }
        // rezervă: ultima locație cunoscută
        return providers
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    /** Extrage „Stradă nr, Localitate" din răspunsul JSON al Nominatim. */
    fun addressFromNominatim(json: String): String? {
        return try {
            val a = JSONObject(json).optJSONObject("address") ?: return null
            fun first(vararg keys: String): String? = keys
                .firstNotNullOfOrNull { k -> a.optString(k).takeIf { it.isNotBlank() } }

            val road = first("road", "pedestrian", "residential", "footway", "square")
            val nr = a.optString("house_number")
            val city = first("city", "town", "village", "municipality", "hamlet", "suburb")

            val street = when {
                road != null && nr.isNotBlank() -> "$road $nr"
                road != null -> road
                else -> null
            }
            listOfNotNull(street, city).joinToString(", ").ifBlank { null }
        } catch (e: Exception) {
            null
        }
    }

    /** Adresa pentru coordonate, prin OpenStreetMap Nominatim. */
    suspend fun reverseGeocode(lat: Double, lon: Double): String? =
        withContext(Dispatchers.IO) {
            try {
                val url = URL(
                    "https://nominatim.openstreetmap.org/reverse?format=jsonv2" +
                        "&lat=$lat&lon=$lon&zoom=18&addressdetails=1&accept-language=ro"
                )
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                // politica Nominatim cere identificarea aplicației
                conn.setRequestProperty("User-Agent", "NecMat-Android (casapucristian@gmail.com)")
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                addressFromNominatim(body)
            } catch (e: Exception) {
                null
            }
        }
}
