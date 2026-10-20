package com.rendyhd.vicu.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import android.os.CancellationSignal
import com.rendyhd.vicu.notification.TaskReminderNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * The location permission on Android. Geofencing needs precise location and, from Android 10,
 * "Allow all the time", which is asked for separately after the foreground permission (from
 * Android 11 the system shows its settings page for it). The activity registers the launcher while
 * it is on screen ([attach]) and reports the answer ([onResult]).
 */
class AndroidLocationPermissionPlatform(private val context: Context) : LocationPermissionPlatform {

    @Volatile
    private var launcher: ((Array<String>) -> Unit)? = null

    private val _access = MutableStateFlow(read())
    override val access: StateFlow<LocationAccess> = _access.asStateFlow()

    override fun refresh() {
        _access.value = read()
    }

    override fun request(): Boolean {
        val launch = launcher ?: return false
        when {
            !granted(Manifest.permission.ACCESS_FINE_LOCATION) ->
                launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            needsBackground() -> launch(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
            else -> refresh()
        }
        return true
    }

    /** The answer to a request: once precise location is granted, goes on to "all the time". */
    fun onResult(requested: Collection<String>) {
        refresh()
        if (Manifest.permission.ACCESS_FINE_LOCATION in requested &&
            granted(Manifest.permission.ACCESS_FINE_LOCATION) &&
            needsBackground()
        ) {
            launcher?.invoke(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
        }
    }

    override fun openSettings() {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun attach(launch: (Array<String>) -> Unit) {
        launcher = launch
    }

    /** Withdraws [launch] only if it is still the current one (a new activity may have attached already). */
    fun detach(launch: (Array<String>) -> Unit) {
        if (launcher === launch) launcher = null
    }

    private fun read(): LocationAccess = when {
        granted(Manifest.permission.ACCESS_FINE_LOCATION) && !needsBackground() -> LocationAccess.ALWAYS
        granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION) -> LocationAccess.WHILE_USING
        else -> LocationAccess.NONE
    }

    private fun needsBackground(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

/** One position from Android's location manager; the last known one when no fresh one comes in time. */
class AndroidDeviceLocation(private val context: Context) : DeviceLocation {

    @SuppressLint("MissingPermission") // Checked first.
    override suspend fun current(): GeoPoint? {
        val allowed = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (!allowed) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
        }.filter { it in manager.allProviders && manager.isProviderEnabled(it) }
        val provider = providers.firstOrNull() ?: return null
        val fresh = withTimeoutOrNull(TIMEOUT_MS) {
            suspendCancellableCoroutine<Location?> { continuation ->
                val signal = CancellationSignal()
                continuation.invokeOnCancellation { signal.cancel() }
                LocationManagerCompat.getCurrentLocation(manager, provider, signal, ContextCompat.getMainExecutor(context)) {
                    continuation.resume(it)
                }
            }
        }
        val location = fresh ?: providers.firstNotNullOfOrNull { manager.getLastKnownLocation(it) }
        return location?.let { GeoPoint(it.latitude, it.longitude) }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}

/** A fired location reminder as a task reminder notification (Mark Complete, Snooze). */
class AndroidLocationNotifier(private val context: Context) : LocationNotifier {
    override fun show(taskId: Long, title: String, body: String, sound: Boolean) {
        TaskReminderNotification.post(context, taskId, title, body, sound)
    }
}
