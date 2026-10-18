package com.rendyhd.vicu.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import com.rendyhd.vicu.util.IosPaths
import com.rendyhd.vicu.util.Logger
import kotlinx.coroutines.delay
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import platform.Foundation.NSBundle
import kotlin.time.Duration.Companion.seconds

/**
 * Keeps the UI's saved state when iOS ends the app in the background, as Android does for its
 * instance state: the screen and its back stack, the open editor with its unsaved draft, a
 * half-written quick add.
 *
 * Compose on iOS keeps `rememberSaveable` only while the process lives, so the root gets this
 * registry. The Swift scene asks for its state ([UiStateStore.save]) whenever it leaves the front
 * and keeps it in scene storage, which iOS hands back at the next launch ([restored]) unless the
 * user closed the app in the app switcher. A state from another build is ignored.
 */
@Composable
internal fun RestorableUiState(restored: String, content: @Composable () -> Unit) {
    val registry = remember { SaveableStateRegistry(UiStateStore.restore(restored)) { true } }
    DisposableEffect(registry) {
        UiStateStore.current = registry
        onDispose { if (UiStateStore.current === registry) UiStateStore.current = null }
    }
    LaunchedEffect(registry) {
        // Still running a few seconds after a restore: the state was fine.
        delay(RESTORE_SETTLE)
        UiStateStore.restoreSucceeded()
    }
    CompositionLocalProvider(LocalSaveableStateRegistry provides registry, content = content)
}

private val RESTORE_SETTLE = 3.seconds

internal object UiStateStore {
    private const val TAG = "UiStateStore"

    /** The registry of the UI on screen; the scene's state is read from it. */
    internal var current: SaveableStateRegistry? = null

    /**
     * Present while a restored state is being applied. Found at a launch, it means that launch
     * crashed: the state is then not restored again, so it cannot crash the app over and over.
     */
    private val guard: Path get() = Path("${IosPaths.caches}/ui-state-restoring")

    private val build: String by lazy {
        val info = NSBundle.mainBundle
        "${info.objectForInfoDictionaryKey("CFBundleShortVersionString")}-${info.objectForInfoDictionaryKey("CFBundleVersion")}"
    }

    /** The UI's state now, for the scene to keep; empty when there is no UI. */
    fun save(): String {
        val registry = current ?: return ""
        return try {
            val encoded = UiStateCodec.encode(registry.performSave())
            if (encoded.skipped.isNotEmpty()) Logger.d(TAG, "Not saved: ${encoded.skipped}")
            JsonObject(mapOf("build" to JsonPrimitive(build), "values" to encoded.values)).toString()
        } catch (e: Exception) {
            Logger.w(TAG, "UI state not saved: ${e.message}")
            ""
        }
    }

    fun restore(text: String): Map<String, List<Any?>>? {
        if (SystemFileSystem.exists(guard)) {
            Logger.w(TAG, "The last restore did not finish; starting fresh")
            restoreSucceeded()
            return null
        }
        if (text.isBlank()) return null
        return try {
            val root = Json.parseToJsonElement(text).jsonObject
            if (root["build"]?.jsonPrimitive?.content != build) return null
            val values = UiStateCodec.decode(root.getValue("values").jsonObject)
            SystemFileSystem.sink(guard).buffered().use { it.writeString(build) }
            values
        } catch (e: Exception) {
            Logger.w(TAG, "Saved UI state not restored: ${e.message}")
            null
        }
    }

    fun restoreSucceeded() {
        runCatching { SystemFileSystem.delete(guard, mustExist = false) }
    }
}
