package com.rendyhd.vicu.location

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** A place as the synced preferences hold it: its id and the name the user gave it, never where it is. */
data class SyncedPlace(val id: String, val name: String)

/** A task's location reminder as the synced preferences hold it. */
data class SyncedLocationReminder(
    val taskId: Long,
    val placeId: String,
    val trigger: LocationTrigger,
    val radiusMeters: Int,
)

/**
 * The `place.<placeId>` and `location_reminder.<taskId>` entries of the synced preferences
 * (docs/cross-app-semantics-v1.md, section 11.7). A value without the right shape reads as none.
 */
object SyncedLocation {
    const val PLACE_PREFIX = "place."
    const val REMINDER_PREFIX = "location_reminder."
    const val MIN_RADIUS = 100
    const val MAX_RADIUS = 1000

    private val PLACE_ID = Regex("[A-Za-z0-9_-]{1,64}")

    fun placeKey(placeId: String): String = "$PLACE_PREFIX$placeId"

    fun reminderKey(taskId: Long): String = "$REMINDER_PREFIX$taskId"

    fun isLocationKey(key: String): Boolean = key.startsWith(PLACE_PREFIX) || key.startsWith(REMINDER_PREFIX)

    /** The place id of a `place.<id>` key, or null. */
    fun placeIdOf(key: String): String? =
        key.takeIf { it.startsWith(PLACE_PREFIX) }?.removePrefix(PLACE_PREFIX)?.takeIf { PLACE_ID.matches(it) }

    /** The task id of a `location_reminder.<id>` key (a server id, above 0), or null. */
    fun taskIdOf(key: String): Long? =
        key.takeIf { it.startsWith(REMINDER_PREFIX) }?.removePrefix(REMINDER_PREFIX)?.toLongOrNull()?.takeIf { it > 0 }

    fun isPlaceId(id: String): Boolean = PLACE_ID.matches(id)

    /** The name in a `place.<id>` value, or null when it has none. */
    fun placeName(value: JsonElement?): String? {
        val name = (value as? JsonObject)?.get("name") as? JsonPrimitive ?: return null
        if (!name.isString) return null
        return name.content.takeIf { it.isNotBlank() }
    }

    /** The place, trigger and radius in a `location_reminder.<id>` value, or null. */
    fun reminder(taskId: Long, value: JsonElement?): SyncedLocationReminder? {
        val obj = value as? JsonObject ?: return null
        val place = (obj["place"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf(::isPlaceId) ?: return null
        val trigger = when ((obj["trigger"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull) {
            "arrive" -> LocationTrigger.ARRIVE
            "leave" -> LocationTrigger.LEAVE
            else -> return null
        }
        val radiusValue = obj["radius_m"] as? JsonPrimitive ?: return null
        if (radiusValue.isString) return null
        val radius = radiusValue.longOrNull?.takeIf { it in MIN_RADIUS..MAX_RADIUS } ?: return null
        return SyncedLocationReminder(taskId, place, trigger, radius.toInt())
    }

    fun placeValue(name: String): JsonElement = buildJsonObject { put("name", name.trim()) }

    fun reminderValue(placeId: String, trigger: LocationTrigger, radiusMeters: Int): JsonElement = buildJsonObject {
        put("place", placeId)
        put("trigger", if (trigger == LocationTrigger.ARRIVE) "arrive" else "leave")
        put("radius_m", radiusMeters.coerceIn(MIN_RADIUS, MAX_RADIUS))
    }

    /** The places named in [entries] (deleted and malformed ones left out), by name. */
    fun places(entries: Map<String, JsonElement?>): List<SyncedPlace> =
        entries.mapNotNull { (key, value) ->
            val id = placeIdOf(key) ?: return@mapNotNull null
            placeName(value)?.let { SyncedPlace(id, it) }
        }.sortedBy { it.name.lowercase() }

    /** The location reminders in [entries] (removed and malformed ones left out). */
    fun reminders(entries: Map<String, JsonElement?>): List<SyncedLocationReminder> =
        entries.mapNotNull { (key, value) -> taskIdOf(key)?.let { reminder(it, value) } }

    /**
     * The value an app uses for [key] in [entries], as the fixture's `syncedPrefs.read` vectors give
     * it: the canonical object, or null for none.
     */
    fun read(entries: Map<String, JsonElement?>, key: String): JsonElement {
        placeIdOf(key)?.let { return placeName(entries[key])?.let { name -> buildJsonObject { put("name", name) } } ?: JsonNull }
        taskIdOf(key)?.let { taskId ->
            return reminder(taskId, entries[key])?.let { reminderValue(it.placeId, it.trigger, it.radiusMeters) } ?: JsonNull
        }
        return JsonNull
    }
}
