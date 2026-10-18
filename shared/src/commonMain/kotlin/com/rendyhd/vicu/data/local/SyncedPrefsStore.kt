package com.rendyhd.vicu.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rendyhd.vicu.domain.model.SyncedPrefsLocalState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/**
 * The device's copy of the synced preferences (docs/cross-app-semantics-v1.md, section 11) and its
 * sync state, like [CustomListStore] for the custom lists. It belongs to the account: cleared by
 * [LocalDataWiper.wipeEverything] and excluded from backups.
 */
class SyncedPrefsStore(private val dataStore: DataStore<Preferences>) {
    private companion object {
        val KEY_STATE = stringPreferencesKey("synced_prefs_v1")

        /** The settings kept on the device before they synced were offered to the carrier (section 11.5). */
        val KEY_MOVED = booleanPreferencesKey("moved_v1")
        val json = Json { ignoreUnknownKeys = true }
    }

    private fun decode(raw: String?): SyncedPrefsLocalState? =
        raw?.let { runCatching { json.decodeFromString(SyncedPrefsLocalState.serializer(), it) }.getOrNull() }

    val state: Flow<SyncedPrefsLocalState?> = dataStore.data.map { decode(it[KEY_STATE]) }

    /** True while the device holds a choice the carrier may not have yet. */
    val hasUnsyncedChanges: Flow<Boolean> = state.map { it != null && it.dirty && it.document.entries.isNotEmpty() }

    suspend fun load(): SyncedPrefsLocalState? = decode(dataStore.data.first()[KEY_STATE])

    suspend fun save(state: SyncedPrefsLocalState) {
        val encoded = json.encodeToString(SyncedPrefsLocalState.serializer(), state)
        dataStore.edit { if (it[KEY_STATE] != encoded) it[KEY_STATE] = encoded }
    }

    suspend fun isMoved(): Boolean = dataStore.data.first()[KEY_MOVED] == true

    suspend fun setMoved() {
        dataStore.edit { it[KEY_MOVED] = true }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }
}
