package com.rendyhd.vicu.location

import kotlinx.serialization.Serializable

/** A point on the map, in degrees. */
@Serializable
data class GeoPoint(val latitude: Double, val longitude: Double)

/**
 * Where this phone found a saved place: its position and address, by place id. Positions never
 * leave the device (not synced, not in backups); the place's name syncs (section 11.7 of the
 * cross-app contract), and [name] here is the name it had when this phone saved it.
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

/**
 * A saved place as the app shows it: the synced name, and the position when this phone knows it
 * (a place saved on another phone has none until it is located here).
 */
data class SavedPlace(
    val id: String,
    val name: String,
    val position: GeoPoint? = null,
    val address: String = "",
)

@Serializable
enum class LocationTrigger { ARRIVE, LEAVE }

/**
 * "Remind me when I arrive at (or leave) a place" for one task, with the position to watch. A task
 * has at most one, and it is removed once it has fired. This is what the coordinator watches and
 * what [LocationReminderStore] keeps on this phone; [LocationRepository] syncs a reminder at a
 * saved place ([placeId]) without its position.
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

/**
 * A task's location reminder as the app shows it, synced or kept on this phone. [position] is
 * null when this phone does not know where the place is; such a reminder is not watched here.
 */
data class PlaceReminder(
    val taskId: Long,
    val placeId: String?,
    val placeName: String,
    val trigger: LocationTrigger,
    val radiusMeters: Int = LocationReminder.DEFAULT_RADIUS_METERS,
    val position: GeoPoint? = null,
    /** True when it is in the synced preferences, false when it stays on this phone. */
    val synced: Boolean = false,
) {
    /** The reminder to watch, or null without a position. */
    fun watched(): LocationReminder? = position?.let {
        LocationReminder(taskId, placeId, placeName, it.latitude, it.longitude, radiusMeters, trigger)
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
fun LocationReminder.label(): String = locationLabel(trigger, placeName)

fun PlaceReminder.label(): String = locationLabel(trigger, placeName)

private fun locationLabel(trigger: LocationTrigger, placeName: String): String = when (trigger) {
    LocationTrigger.ARRIVE -> "Arrive: $placeName"
    LocationTrigger.LEAVE -> "Leave: $placeName"
}
