package app.umgangstagebuch.recording

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import app.umgangstagebuch.data.GeoPoint
import java.util.concurrent.Executors

/** Ermittelt ohne Google-Dienste einen Standort (nur Android-Bordmittel, keine Internetverbindung). */
object LocationHelper {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun Location.toGeoPoint() = GeoPoint(latitude, longitude, if (hasAccuracy()) accuracy else null, System.currentTimeMillis())

    @Suppress("MissingPermission")
    fun request(context: Context, onResult: (GeoPoint?) -> Unit) {
        if (!hasPermission(context)) { onResult(null); return }
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (lm == null) { onResult(null); return }

        // Sofort den letzten bekannten Standort liefern, parallel einen frischen anfragen.
        val last = runCatching {
            listOfNotNull(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
                .filter { lm.isProviderEnabled(it) }
                .mapNotNull { lm.getLastKnownLocation(it) }
                .maxByOrNull { it.time }
        }.getOrNull()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val provider = when {
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> null
            }
            if (provider == null) { onResult(last?.toGeoPoint()); return }
            val executor = Executors.newSingleThreadExecutor()
            var answered = false
            runCatching {
                lm.getCurrentLocation(provider, CancellationSignal(), executor) { loc ->
                    if (!answered) { answered = true; onResult((loc ?: last)?.toGeoPoint()) }
                    executor.shutdown()
                }
            }.onFailure { onResult(last?.toGeoPoint()); executor.shutdown() }
        } else {
            onResult(last?.toGeoPoint())
        }
    }
}
