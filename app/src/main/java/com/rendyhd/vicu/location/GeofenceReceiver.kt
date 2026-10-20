package com.rendyhd.vicu.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * The device entered or left a watched region (Play services geofencing, or a proximity alert
 * without it). Android starts the app for it when it is not running.
 */
class GeofenceReceiver : BroadcastReceiver(), KoinComponent {

    companion object {
        private const val TAG = "GeofenceReceiver"
        const val ACTION_GEOFENCE = "com.rendyhd.vicu.ACTION_GEOFENCE"
        const val ACTION_PROXIMITY = "com.rendyhd.vicu.ACTION_PROXIMITY"
        const val EXTRA_REGION_ID = "region_id"
    }

    private val coordinator: LocationReminderCoordinator by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val (regionIds, entered) = when (intent.action) {
            ACTION_GEOFENCE -> {
                val event = GeofencingEvent.fromIntent(intent) ?: return
                if (event.hasError()) {
                    // GEOFENCE_NOT_AVAILABLE: location was switched off and Play services dropped
                    // the regions. The periodic sync and the next app start hand them over again.
                    Log.w(TAG, "Geofence error ${GeofenceStatusCodes.getStatusCodeString(event.errorCode)}")
                    return
                }
                val entered = when (event.geofenceTransition) {
                    Geofence.GEOFENCE_TRANSITION_ENTER -> true
                    Geofence.GEOFENCE_TRANSITION_EXIT -> false
                    else -> return
                }
                event.triggeringGeofences.orEmpty().map { it.requestId } to entered
            }
            ACTION_PROXIMITY -> {
                val id = intent.getStringExtra(EXTRA_REGION_ID) ?: return
                listOf(id) to intent.getBooleanExtra(LocationManager.KEY_PROXIMITY_ENTERING, false)
            }
            else -> return
        }
        if (regionIds.isEmpty()) return
        Log.d(TAG, "${if (entered) "Entered" else "Left"} ${regionIds.size} region(s)")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                regionIds.forEach { coordinator.onRegionEvent(it, entered) }
            } catch (e: Exception) {
                Log.e(TAG, "Showing a location reminder failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
