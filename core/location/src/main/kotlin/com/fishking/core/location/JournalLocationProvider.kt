package com.fishking.core.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

data class JournalLocation(
    val label: String,
    val latitude: Double,
    val longitude: Double,
)

interface JournalLocationProvider {
    /** Reads one foreground location fix. The caller must already hold coarse or fine permission. */
    suspend fun currentLocation(): JournalLocation
}

class AndroidJournalLocationProvider(
    context: Context,
    private val locale: Locale = Locale.SIMPLIFIED_CHINESE,
) : JournalLocationProvider {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(LocationManager::class.java)

    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(): JournalLocation = withTimeout(LOCATION_TIMEOUT_MILLIS) {
        val provider = chooseProvider(manager.getProviders(true))
            ?: error("No enabled location provider")
        val location = awaitCurrentLocation(provider)
        val address = reverseGeocode(location)
        JournalLocation(
            label = formatLocationLabel(
                adminArea = address?.adminArea,
                locality = address?.locality,
                subLocality = address?.subLocality,
                featureName = address?.featureName,
                latitude = location.latitude,
                longitude = location.longitude,
            ),
            latitude = location.latitude,
            longitude = location.longitude,
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun awaitCurrentLocation(provider: String): Location = suspendCancellableCoroutine { continuation ->
        val signal = CancellationSignal()
        continuation.invokeOnCancellation { signal.cancel() }
        LocationManagerCompat.getCurrentLocation(
            manager,
            provider,
            signal,
            ContextCompat.getMainExecutor(appContext),
        ) { location ->
            if (!continuation.isActive) return@getCurrentLocation
            if (location == null) continuation.resumeWithException(IllegalStateException("Location unavailable"))
            else continuation.resume(location)
        }
    }

    private suspend fun reverseGeocode(location: Location): Address? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(appContext, locale)
        return if (Build.VERSION.SDK_INT >= 33) {
            suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocation(location.latitude, location.longitude, 1) { addresses ->
                    if (continuation.isActive) continuation.resume(addresses.firstOrNull())
                }
            }
        } else {
            @Suppress("DEPRECATION")
            withContext(Dispatchers.IO) {
                runCatching { geocoder.getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull() }
                    .getOrNull()
            }
        }
    }

    private fun chooseProvider(enabledProviders: List<String>): String? = when {
        LocationManager.NETWORK_PROVIDER in enabledProviders -> LocationManager.NETWORK_PROVIDER
        LocationManager.GPS_PROVIDER in enabledProviders -> LocationManager.GPS_PROVIDER
        LocationManager.PASSIVE_PROVIDER in enabledProviders -> LocationManager.PASSIVE_PROVIDER
        else -> null
    }

    private companion object {
        const val LOCATION_TIMEOUT_MILLIS = 12_000L
    }
}

internal fun formatLocationLabel(
    adminArea: String?,
    locality: String?,
    subLocality: String?,
    featureName: String?,
    latitude: Double,
    longitude: Double,
): String {
    val readable = listOf(locality, subLocality, featureName, adminArea)
        .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
        .distinct()
        .take(2)
    return readable.takeIf(List<String>::isNotEmpty)?.joinToString(" · ")
        ?: "%.5f, %.5f".format(Locale.ROOT, latitude, longitude)
}
