package com.rendyhd.vicu.location

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rendyhd.vicu.util.randomUuid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** A JSON list in one DataStore key; an entry that does not read drops the list, not the app. */
private class JsonListPref<T>(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
    element: KSerializer<T>,
    name: String,
) {
    private val key = stringPreferencesKey(name)
    private val serializer = ListSerializer(element)

    private fun decode(raw: String?): List<T> =
        raw?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() } ?: emptyList()

    val values: Flow<List<T>> = dataStore.data.map { decode(it[key]) }.distinctUntilChanged()

    suspend fun all(): List<T> = decode(dataStore.data.first()[key])

    suspend fun update(change: (List<T>) -> List<T>) {
        dataStore.edit { prefs -> prefs[key] = json.encodeToString(serializer, change(decode(prefs[key]))) }
    }
}

/**
 * The places saved on this device. A device setting: kept across sign-out and account switches,
 * never synced and excluded from backups (`saved_places`).
 */
class SavedPlacesStore(dataStore: DataStore<Preferences>, json: Json) {
    private val list = JsonListPref(dataStore, json, Place.serializer(), "places_json")

    val places: Flow<List<Place>> = list.values

    suspend fun all(): List<Place> = list.all()

    suspend fun add(name: String, point: GeoPoint, address: String = ""): Place {
        val place = Place(randomUuid(), name.trim(), point.latitude, point.longitude, address)
        list.update { it + place }
        return place
    }

    suspend fun rename(id: String, name: String) {
        list.update { places -> places.map { if (it.id == id) it.copy(name = name.trim()) else it } }
    }

    suspend fun delete(id: String) {
        list.update { places -> places.filterNot { it.id == id } }
    }
}

/**
 * The location reminders of the signed-in account, one per task (`location_reminders`). Account
 * data: sign-out clears it (LocalDataWiper). It stays on the device: not synced, not in backups.
 */
class LocationReminderStore(dataStore: DataStore<Preferences>, json: Json) {
    private val list = JsonListPref(dataStore, json, LocationReminder.serializer(), "reminders_json")

    val reminders: Flow<List<LocationReminder>> = list.values

    fun forTask(taskId: Long): Flow<LocationReminder?> =
        reminders.map { all -> all.firstOrNull { it.taskId == taskId } }.distinctUntilChanged()

    suspend fun all(): List<LocationReminder> = list.all()

    /** Sets the reminder of its task, replacing the one it had. */
    suspend fun put(reminder: LocationReminder) {
        list.update { all -> all.filterNot { it.taskId == reminder.taskId } + reminder }
    }

    suspend fun remove(taskId: Long) = removeAll(setOf(taskId))

    suspend fun removeAll(taskIds: Set<Long>) {
        if (taskIds.isEmpty()) return
        list.update { all -> all.filterNot { it.taskId in taskIds } }
    }

    /** The task created offline as [tempId] is now [realId] on the server. */
    suspend fun remap(tempId: Long, realId: Long) {
        list.update { all ->
            val moved = all.firstOrNull { it.taskId == tempId } ?: return@update all
            all.filterNot { it.taskId == tempId || it.taskId == realId } + moved.copy(taskId = realId)
        }
    }

    /** A saved place was renamed: its reminders show the new name. */
    suspend fun placeRenamed(place: Place) {
        list.update { all -> all.map { if (it.placeId == place.id) it.copy(placeName = place.name) else it } }
    }

    /** A saved place was deleted: its reminders keep their copy of the position and stop naming it. */
    suspend fun placeDeleted(placeId: String) {
        list.update { all -> all.map { if (it.placeId == placeId) it.copy(placeId = null) else it } }
    }

    suspend fun clear() {
        list.update { emptyList() }
    }
}
