package com.rendyhd.vicu.location

import com.rendyhd.vicu.data.repository.SyncedEntries
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonElement

/**
 * The one place that reads and writes saved places and location reminders.
 *
 * A place's id and name, and a reminder at a saved place on a task the server knows, are in the
 * synced preferences (`place.<id>`, `location_reminder.<taskId>`; docs/cross-app-semantics-v1.md,
 * section 11.7), so every device of the account sees them. Where a place is stays on this phone
 * ([SavedPlacesStore]). A reminder at a spot that is not a saved place, and one of a task created
 * offline until it has its server id, stays on this phone too ([LocationReminderStore]).
 */
class LocationRepository(
    private val synced: SyncedEntries,
    private val positions: SavedPlacesStore,
    private val local: LocationReminderStore,
) {
    /** The location entries of the synced preferences: other settings changing do not replan. */
    private val entries: Flow<Map<String, JsonElement?>> =
        synced.entries.map { all -> all.filterKeys(SyncedLocation::isLocationKey) }.distinctUntilChanged()

    /** The saved places by name, each with its position when this phone knows it. */
    val places: Flow<List<SavedPlace>> =
        combine(entries, positions.places, positions.moved) { entries, located, moved -> join(entries, located, moved) }
            .distinctUntilChanged()

    /** Every location reminder, synced or on this phone; synced ones win for the same task. */
    val reminders: Flow<List<PlaceReminder>> =
        combine(entries, places, local.reminders) { entries, places, kept -> resolve(entries, places, kept) }
            .distinctUntilChanged()

    fun reminderFor(taskId: Long): Flow<PlaceReminder?> =
        reminders.map { all -> all.firstOrNull { it.taskId == taskId } }.distinctUntilChanged()

    suspend fun allPlaces(): List<SavedPlace> = places.first()

    suspend fun allReminders(): List<PlaceReminder> = reminders.first()

    /** A reminder at [place] for [taskId]; the position is only a copy (a place may have none here). */
    fun reminderAt(taskId: Long, place: SavedPlace, trigger: LocationTrigger, radiusMeters: Int = LocationReminder.DEFAULT_RADIUS_METERS) =
        LocationReminder(
            taskId = taskId,
            placeId = place.id,
            placeName = place.name,
            latitude = place.position?.latitude ?: 0.0,
            longitude = place.position?.longitude ?: 0.0,
            radiusMeters = radiusMeters,
            trigger = trigger,
        )

    /** Saves a place called [name] at [point]: the name for every device, the position for this one. */
    suspend fun addPlace(name: String, point: GeoPoint, address: String = ""): SavedPlace {
        val place = positions.add(name, point, address)
        synced.set(SyncedLocation.placeKey(place.id), SyncedLocation.placeValue(place.name))
        return SavedPlace(place.id, place.name, place.point, place.address)
    }

    suspend fun renamePlace(id: String, name: String) {
        if (name.isBlank()) return
        synced.set(SyncedLocation.placeKey(id), SyncedLocation.placeValue(name))
        positions.rename(id, name)
        local.placeRenamed(id, name.trim())
    }

    /** Sets where [place] (saved on another phone, or moved) is on this phone. */
    suspend fun setPosition(place: SavedPlace, point: GeoPoint, address: String = "") {
        positions.put(Place(place.id, place.name, point.latitude, point.longitude, address))
    }

    /** Deletes the place on every device, with its reminders. */
    suspend fun deletePlace(id: String) {
        val entries = synced.entries.first()
        synced.set(SyncedLocation.placeKey(id), null)
        SyncedLocation.reminders(entries).filter { it.placeId == id }.forEach {
            synced.set(SyncedLocation.reminderKey(it.taskId), null)
        }
        local.removeAll(local.all().filter { it.placeId == id }.mapTo(HashSet()) { it.taskId })
        positions.delete(id)
    }

    /**
     * Sets the task's location reminder, replacing the one it had. One at a saved place on a task
     * the server knows syncs; any other stays on this phone.
     */
    suspend fun setReminder(reminder: LocationReminder) {
        val placeId = reminder.placeId
        if (placeId != null && reminder.taskId > 0 && SyncedLocation.isPlaceId(placeId)) {
            synced.set(
                SyncedLocation.reminderKey(reminder.taskId),
                SyncedLocation.reminderValue(placeId, reminder.trigger, reminder.radiusMeters),
            )
            local.remove(reminder.taskId)
        } else {
            local.put(reminder)
            if (reminder.taskId > 0) clearSynced(setOf(reminder.taskId))
        }
    }

    suspend fun removeReminder(taskId: Long) = removeReminders(setOf(taskId))

    /** Removes the reminders of [taskIds] here and on every device. */
    suspend fun removeReminders(taskIds: Set<Long>) {
        if (taskIds.isEmpty()) return
        local.removeAll(taskIds)
        clearSynced(taskIds)
    }

    /** The task created offline as [tempId] is now [realId] on the server: its reminder can sync. */
    suspend fun followTempId(tempId: Long, realId: Long) {
        val reminder = local.all().firstOrNull { it.taskId == tempId } ?: return
        if (reminder.placeId != null && allPlaces().any { it.id == reminder.placeId }) {
            setReminder(reminder.copy(taskId = realId))
            local.remove(tempId)
        } else {
            local.remap(tempId, realId)
        }
    }

    /**
     * Drops what another device made stale: a reminder kept here for a task that now has a synced
     * one, a synced reminder at a place that was deleted, and the position of a deleted place.
     */
    suspend fun tidy() {
        val entries = synced.entries.first().filterKeys(SyncedLocation::isLocationKey)
        val deleted = entries.filter { (key, value) -> value == null && SyncedLocation.placeIdOf(key) != null }
            .keys.mapNotNullTo(HashSet()) { SyncedLocation.placeIdOf(it) }
        val syncedReminders = SyncedLocation.reminders(entries)
        local.removeAll(local.all().filter { kept -> syncedReminders.any { it.taskId == kept.taskId } }.mapTo(HashSet()) { it.taskId })
        syncedReminders.filter { it.placeId in deleted }.forEach { synced.set(SyncedLocation.reminderKey(it.taskId), null) }
        positions.all().filter { it.id in deleted }.forEach { positions.delete(it.id) }
    }

    /**
     * The one-time move after the first sync with this version: places, and reminders at them,
     * that this phone kept before places synced are offered to the synced preferences, each only
     * when they have no entry for it yet.
     */
    suspend fun moveLocalData() {
        if (positions.isMoved()) return
        val entries = synced.entries.first()
        for (place in positions.all()) {
            val key = SyncedLocation.placeKey(place.id)
            if (key !in entries && SyncedLocation.isPlaceId(place.id) && place.name.isNotBlank()) {
                synced.set(key, SyncedLocation.placeValue(place.name))
            }
        }
        val placeIds = positions.all().mapTo(HashSet()) { it.id }
        for (reminder in local.all()) {
            if (reminder.taskId <= 0 || reminder.placeId !in placeIds) continue
            if (SyncedLocation.reminderKey(reminder.taskId) !in entries) setReminder(reminder) else local.remove(reminder.taskId)
        }
        positions.setMoved()
    }

    private suspend fun clearSynced(taskIds: Set<Long>) {
        val entries = synced.entries.first()
        for (taskId in taskIds) {
            val key = SyncedLocation.reminderKey(taskId)
            if (taskId > 0 && entries[key] != null) synced.set(key, null)
        }
    }

    internal companion object {
        /**
         * The synced places with this phone's positions. Before the one-time move, a place only
         * this phone knows is listed too, so it does not disappear until the first sync.
         */
        fun join(entries: Map<String, JsonElement?>, located: List<Place>, moved: Boolean): List<SavedPlace> {
            val byId = located.associateBy { it.id }
            val syncedPlaces = SyncedLocation.places(entries).map { place ->
                val position = byId[place.id]
                SavedPlace(place.id, place.name, position?.point, position?.address.orEmpty())
            }
            val notMoved = if (moved) {
                emptyList()
            } else {
                located.filter { SyncedLocation.placeKey(it.id) !in entries }
                    .map { SavedPlace(it.id, it.name, it.point, it.address) }
            }
            return (syncedPlaces + notMoved).sortedBy { it.name.lowercase() }
        }

        fun resolve(entries: Map<String, JsonElement?>, places: List<SavedPlace>, kept: List<LocationReminder>): List<PlaceReminder> {
            val byId = places.associateBy { it.id }
            val syncedReminders = SyncedLocation.reminders(entries).mapNotNull { reminder ->
                val place = byId[reminder.placeId] ?: return@mapNotNull null
                PlaceReminder(reminder.taskId, place.id, place.name, reminder.trigger, reminder.radiusMeters, place.position, synced = true)
            }
            val syncedTasks = syncedReminders.mapTo(HashSet()) { it.taskId }
            val local = kept.filter { it.taskId !in syncedTasks }.map { reminder ->
                // A reminder at a saved place follows the place: its name, and where this phone
                // has it (none when it does not). A spot that is no saved place keeps its own.
                val place = reminder.placeId?.let(byId::get)
                PlaceReminder(
                    taskId = reminder.taskId,
                    placeId = reminder.placeId,
                    placeName = place?.name ?: reminder.placeName,
                    trigger = reminder.trigger,
                    radiusMeters = reminder.radiusMeters,
                    position = if (reminder.placeId == null) reminder.point else place?.position,
                )
            }
            return syncedReminders + local
        }
    }
}
