package com.rendyhd.vicu.location

import kotlinx.coroutines.flow.StateFlow

/** Watches regions with the platform's geofencing (Play services or Android's own on Android, CLMonitor on iOS). */
interface PlatformGeofencer {
    /** How many regions the platform lets one app watch. */
    val capacity: Int

    /** Watches exactly [regions] from now on; regions watched before and not listed stop. */
    suspend fun apply(regions: List<LocationRegion>)
}

/** The platform side of the location permission. */
interface LocationPermissionPlatform {
    val access: StateFlow<LocationAccess>

    /** Reads the permission again (the app came to the front, a prompt was answered). */
    fun refresh()

    /**
     * Asks for location access, up to Always where the platform lets an app ask for it. False when
     * there is no screen to show the prompt on right now.
     */
    fun request(): Boolean

    fun openSettings()
}

/** One reading of where the device is. */
interface DeviceLocation {
    /** The current position, or null without permission or when none came in time. */
    suspend fun current(): GeoPoint?
}

/** Finds places by name or address (Photon on Android, Apple Maps on iOS). */
interface PlaceSearch {
    /** The credit the provider asks to show under its results, or null. */
    val attribution: String?

    /** Places matching [query], those near [near] first when it is known. Throws when the search fails. */
    suspend fun search(query: String, near: GeoPoint?): List<PlaceSuggestion>

    /** What is at [point] (a street or a place name), or null. */
    suspend fun reverse(point: GeoPoint): PlaceSuggestion?
}

/** Shows a fired location reminder, with the same Complete and Snooze actions as a time reminder. */
interface LocationNotifier {
    fun show(taskId: Long, title: String, body: String, sound: Boolean)
}
