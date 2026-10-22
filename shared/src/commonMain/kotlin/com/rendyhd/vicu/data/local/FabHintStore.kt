package com.rendyhd.vicu.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.rendyhd.vicu.ui.components.fab.FabHintState
import kotlinx.coroutines.flow.first

/** What this device remembers for the one-time "Hold + to say a task" hint. */
class FabHintStore(
    private val dataStore: DataStore<Preferences>,
) {
    private companion object {
        val KEY_PLUS_ADDS = intPreferencesKey("plus_adds")
        val KEY_SHOWN = booleanPreferencesKey("hint_shown")
        val KEY_VOICE_USED = booleanPreferencesKey("voice_used")
    }

    suspend fun read(): FabHintState {
        val prefs = dataStore.data.first()
        return FabHintState(
            plusAdds = prefs[KEY_PLUS_ADDS] ?: 0,
            shown = prefs[KEY_SHOWN] ?: false,
            voiceUsed = prefs[KEY_VOICE_USED] ?: false,
        )
    }

    suspend fun write(state: FabHintState) {
        dataStore.edit {
            it[KEY_PLUS_ADDS] = state.plusAdds
            it[KEY_SHOWN] = state.shown
            it[KEY_VOICE_USED] = state.voiceUsed
        }
    }
}
