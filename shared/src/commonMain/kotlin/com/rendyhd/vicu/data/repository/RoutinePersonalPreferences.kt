package com.rendyhd.vicu.data.repository

import com.rendyhd.vicu.domain.model.RoutinePersonalSettings
import com.rendyhd.vicu.domain.model.SyncedPrefKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonPrimitive

/**
 * Turning a shared routine's reminders off and hiding it, as synced settings of this account
 * (docs/cross-app-semantics-v1.md, sections 6.9 and 11.3). The defaults (reminders on, shown) write
 * no value, so they reach the other devices as a reset.
 */
class RoutinePersonalPreferences(private val synced: SyncedEntries) {

    val settings: Flow<RoutinePersonalSettings> =
        synced.entries.map { RoutinePersonalSettings.fromEntries(it) }.distinctUntilChanged()

    suspend fun setRemindersEnabled(routineId: String, enabled: Boolean) {
        synced.set(SyncedPrefKeys.routineRemindersOf(routineId), if (enabled) null else JsonPrimitive("off"))
    }

    suspend fun setHidden(routineId: String, hidden: Boolean) {
        synced.set(SyncedPrefKeys.routineHiddenOf(routineId), if (hidden) JsonPrimitive(true) else null)
    }
}
