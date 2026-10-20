package com.rendyhd.vicu.location

import kotlinx.serialization.Serializable

/** A point on the map, in degrees. */
@Serializable
data class GeoPoint(val latitude: Double, val longitude: Double)

/**
 * A place the user saved on this device (Home, Work, ...), offered first when a location reminder
 * is set. Places never leave the device: they are not synced and not in backups.
 */
@Serializable
data class Place(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    /** The address it was found under, for the list; may be blank. */
    val address: String = "",
) {
    val point: GeoPoint get() = GeoPoint(latitude, longitude)
}

@Serializable
enum class LocationTrigger { ARRIVE, LEAVE }

/**
 * "Remind me when I arrive at (or leave) a place" for one task. A task has at most one. It is kept
 * on this device only, never sent to the Vikunja server, and removed once it has fired.
 *
 * A reminder at a saved place names it ([placeId]) and keeps a copy of its position, so it still
 * works when the place is deleted; renaming or moving the place updates its reminders.
 */
@Serializable
data class LocationReminder(
    val taskId: Long,
    val placeId: String? = null,
    val placeName: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int = DEFAULT_RADIUS_METERS,
    val trigger: LocationTrigger = LocationTrigger.ARRIVE,
) {
    val point: GeoPoint get() = GeoPoint(latitude, longitude)

    companion object {
        const val DEFAULT_RADIUS_METERS = 200
        val RADIUS_OPTIONS = listOf(100, 200, 500)
    }
}

/** How much location access the app has. Location reminders need [ALWAYS] to work while the app is closed. */
enum class LocationAccess { NONE, WHILE_USING, ALWAYS }

/** A place found by a search or at the current position. */
data class PlaceSuggestion(
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
) {
    val point: GeoPoint get() = GeoPoint(latitude, longitude)
}

/** "Arrive: Home" or "Leave: Work", for the reminder chip and lists. */
fun LocationReminder.label(): String = when (trigger) {
    LocationTrigger.ARRIVE -> "Arrive: $placeName"
    LocationTrigger.LEAVE -> "Leave: $placeName"
}
