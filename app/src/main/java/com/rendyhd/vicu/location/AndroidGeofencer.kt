package com.rendyhd.vicu.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.net.toUri
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Watches the location reminders' regions. With Google Play services it uses its geofencing, which
 * is battery-friendly and keeps working while the app is closed; without them (a phone without
 * Google) it falls back to Android's own proximity alerts.
 *
 * Every plan replaces all regions: there are few, and Play services forgets them after a reboot or
 * when location was switched off, so the boot receiver, the periodic sync and app start plan again.
 * Transitions only fire on a change (no initial trigger), so a reminder set while already at the
 * place waits for the next arrival.
 */
class AndroidGeofencer(private val context: Context) : PlatformGeofencer {

    override val capacity: Int = LocationRegionPlanner.ANDROID_CAPACITY

    private val prefs by lazy { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    private val hasPlayServices: Boolean
        get() = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

    override suspend fun apply(regions: List<LocationRegion>) {
        if (!hasFineLocation()) {
            Log.d(TAG, "No precise location permission; ${regions.size} regions not watched")
            return
        }
        if (hasPlayServices) applyPlay(regions) else applyProximity(regions)
        Log.d(TAG, "Watching ${regions.size} regions")
    }

    @SuppressLint("MissingPermission") // Checked in apply.
    private suspend fun applyPlay(regions: List<LocationRegion>) {
        val client = LocationServices.getGeofencingClient(context)
        client.removeGeofences(geofencePendingIntent()).awaitOrNull()
        if (regions.isEmpty()) return
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(0)
            .addGeofences(
                regions.map { region ->
                    Geofence.Builder()
                        .setRequestId(region.id)
                        .setCircularRegion(region.latitude, region.longitude, region.radiusMeters.toFloat())
                        .setExpirationDuration(Geofence.NEVER_EXPIRE)
                        .setTransitionTypes(
                            if (region.trigger == LocationTrigger.ARRIVE) Geofence.GEOFENCE_TRANSITION_ENTER else Geofence.GEOFENCE_TRANSITION_EXIT,
                        )
                        .build()
                },
            )
            .build()
        client.addGeofences(request, geofencePendingIntent()).await()
    }

    @SuppressLint("MissingPermission") // Checked in apply.
    private fun applyProximity(regions: List<LocationRegion>) {
        val manager = context.getSystemService(LocationManager::class.java) ?: return
        prefs.getStringSet(KEY_PROXIMITY_IDS, emptySet()).orEmpty().forEach { id ->
            manager.removeProximityAlert(proximityPendingIntent(id))
        }
        regions.forEach { region ->
            manager.addProximityAlert(
                region.latitude,
                region.longitude,
                region.radiusMeters.toFloat(),
                -1,
                proximityPendingIntent(region.id),
            )
        }
        prefs.edit { putStringSet(KEY_PROXIMITY_IDS, regions.mapTo(HashSet()) { it.id }) }
    }

    private fun hasFineLocation(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    // Mutable: Play services and the location manager add the event to the intent.
    private fun geofencePendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, GeofenceReceiver::class.java).setAction(GeofenceReceiver.ACTION_GEOFENCE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )

    private fun proximityPendingIntent(regionId: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        regionId.hashCode(),
        Intent(context, GeofenceReceiver::class.java)
            .setAction(GeofenceReceiver.ACTION_PROXIMITY)
            // Tells the intents of two regions apart, so each has its own pending intent.
            .setData("vicu-region:$regionId".toUri())
            .putExtra(GeofenceReceiver.EXTRA_REGION_ID, regionId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )

    private companion object {
        const val TAG = "AndroidGeofencer"
        const val PREFS = "location_regions"
        const val KEY_PROXIMITY_IDS = "proximity_ids"
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}

private suspend fun <T> Task<T>.awaitOrNull(): T? = runCatching { await() }.getOrNull()
