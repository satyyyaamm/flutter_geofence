package com.intivoto.flutter_geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "GeoBroadcastReceiver"

        // Callback to send geofence region updates
        var callback: ((GeoRegion) -> Unit)? = null
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null) {
            Log.e(TAG, "Received null intent")
            return
        }

        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        if (geofencingEvent == null) {
            Log.e(TAG, "GeofencingEvent is null")
            return
        }

        if (geofencingEvent.hasError()) {
            val errorCode = geofencingEvent.errorCode
            Log.e(TAG, "GeofencingEvent error: $errorCode")
            return
        }

        // Transition type (enter/exit)
        val geofenceTransition = geofencingEvent.geofenceTransition
        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER ||
            geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {

            val event = if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
                GeoEvent.entry
            } else {
                GeoEvent.exit
            }

            val triggeringGeofences = geofencingEvent.triggeringGeofences
            val triggeringLocation = geofencingEvent.triggeringLocation

            if (triggeringGeofences.isNullOrEmpty() || triggeringLocation == null) {
                Log.w(TAG, "No geofences or location found in event")
                return
            }

            for (geofence in triggeringGeofences) {
                val region = GeoRegion(
                    id = geofence.requestId,
                    latitude = triggeringLocation.latitude,
                    longitude = triggeringLocation.longitude,
                    radius = 50f, // Fixed radius (can be parameterized if needed)
                    events = listOf(event)
                )

                callback?.invoke(region)
                Log.i(TAG, "Geofence triggered: $region")
            }
        } else {
            Log.w(TAG, "Ignored geofence transition: $geofenceTransition")
        }
    }
}
