package com.intivoto.flutter_geofence

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority


enum class GeoEvent {
    entry,
    exit
}

data class GeoRegion(
    val id: String,
    val radius: Float,
    val latitude: Double,
    val longitude: Double,
    val events: List<GeoEvent>
)

fun GeoRegion.serialized(): Map<*, *> {
    return hashMapOf(
        "id" to id,
        "radius" to radius,
        "latitude" to latitude,
        "longitude" to longitude
    )
}

fun GeoRegion.convertRegionToGeofence(): Geofence {
    // Support both entry and exit in transition types
    var transitionType = 0
    if (events.contains(GeoEvent.entry)) {
        transitionType = transitionType or Geofence.GEOFENCE_TRANSITION_ENTER
    }
    if (events.contains(GeoEvent.exit)) {
        transitionType = transitionType or Geofence.GEOFENCE_TRANSITION_EXIT
    }

    return Geofence.Builder()
        .setRequestId(id)
        .setCircularRegion(latitude, longitude, radius)
        .setExpirationDuration(Geofence.NEVER_EXPIRE)
        .setTransitionTypes(transitionType)
        .build()
}

class GeofenceManager(
    private val context: Context,
    callback: (GeoRegion) -> Unit,
    private val locationUpdate: (Location) -> Unit,
    private val backgroundUpdate: (Location) -> Unit
) {

    private val geofencingClient: GeofencingClient = LocationServices.getGeofencingClient(context)
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    init {
        GeofenceBroadcastReceiver.callback = callback
    }

    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
        } else {
            PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }

    fun startMonitoring(geoRegion: GeoRegion) {
        geofencingClient.addGeofences(
            getGeofencingRequest(geoRegion.convertRegionToGeofence()),
            geofencePendingIntent
        )?.run {
            addOnSuccessListener {
                Log.d("GeofenceManager", "Geofence added: ${geoRegion.id}")
            }
            addOnFailureListener { e ->
                Log.e("GeofenceManager", "Failed to add geofence: ${e.message}", e)
            }
        }
    }

    fun stopMonitoring(geoRegion: GeoRegion) {
        geofencingClient.removeGeofences(listOf(geoRegion.id))
            .addOnSuccessListener {
                Log.d("GeofenceManager", "Geofence removed: ${geoRegion.id}")
            }
            .addOnFailureListener { e ->
                Log.e("GeofenceManager", "Failed to remove geofence: ${e.message}", e)
            }
    }

    fun stopMonitoringAllRegions() {
        geofencingClient.removeGeofences(geofencePendingIntent)?.run {
            addOnSuccessListener {
                Log.d("GeofenceManager", "All geofences removed")
            }
            addOnFailureListener { e ->
                Log.e("GeofenceManager", "Failed to remove all geofences: ${e.message}", e)
            }
        }
    }

    private fun getGeofencingRequest(geofence: Geofence): GeofencingRequest {
        return GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()
    }

    private fun refreshLocation() {
        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation
                if (location != null) {
                    locationUpdate(location)
                } else {
                    Log.w("GeofenceManager", "refreshLocation() received null location")
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            LocationRequest.create(),
            locationCallback,
            Looper.getMainLooper()
        )
    }

    fun getUserLocation() {
        fusedLocationClient.lastLocation.addOnCompleteListener {
            val location = it.result
            if (location != null) {
                if (System.currentTimeMillis() - location.time > 60 * 1000) {
                    refreshLocation()
                } else {
                    locationUpdate(location)
                }
            } else {
                Log.w("GeofenceManager", "lastLocation is null, refreshing...")
                refreshLocation()
            }
        }
    }

    private val backgroundLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val location = locationResult.lastLocation
            if (location != null) {
                backgroundUpdate(location)
            } else {
                Log.w("GeofenceManager", "background location update received null")
            }
        }
    }

    fun startListeningForLocationChanges() {
        val request = LocationRequest.Builder(
            900_000L // 15 min interval
        ).setMinUpdateIntervalMillis(900_000L)
            .setPriority(Priority.PRIORITY_LOW_POWER)
            .build()

        fusedLocationClient.requestLocationUpdates(
            request,
            backgroundLocationCallback,
            Looper.getMainLooper()
        )
    }

    fun stopListeningForLocationChanges() {
        fusedLocationClient.removeLocationUpdates(backgroundLocationCallback)
    }
}
