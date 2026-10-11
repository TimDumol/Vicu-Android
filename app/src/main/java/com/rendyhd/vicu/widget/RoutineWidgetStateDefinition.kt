package com.rendyhd.vicu.widget

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.state.GlanceStateDefinition
import kotlinx.serialization.json.Json
import java.io.File

object RoutineWidgetStateDefinition : GlanceStateDefinition<Preferences> {
    val KEY_STATE = stringPreferencesKey("routine_widget_state")

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getDataStore(
        context: Context,
        fileKey: String,
    ) = androidx.datastore.preferences.core.PreferenceDataStoreFactory.create {
        WidgetStateFiles.locate(context, "routine_widget_state_$fileKey")
    }

    override fun getLocation(context: Context, fileKey: String): File =
        WidgetStateFiles.locate(context, "routine_widget_state_$fileKey")

    fun parseState(prefs: Preferences): RoutineWidgetState {
        val raw = prefs[KEY_STATE] ?: return RoutineWidgetState()
        return runCatching {
            json.decodeFromString(RoutineWidgetState.serializer(), raw)
        }.getOrDefault(RoutineWidgetState())
    }

    fun encodeState(state: RoutineWidgetState): String =
        json.encodeToString(RoutineWidgetState.serializer(), state)
}
