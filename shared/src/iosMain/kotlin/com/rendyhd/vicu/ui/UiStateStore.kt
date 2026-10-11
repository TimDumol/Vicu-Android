package com.rendyhd.vicu.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import com.rendyhd.vicu.util.IosPaths
import com.rendyhd.vicu.util.Logger
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import platform.Foundation.NSBundle
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidEnterBackgroundNotification

/**
 * Keeps the UI's saved state when iOS ends the app in the background, as Android does for its
 * instance state: the screen, the open editor with its unsaved draft, a half-written quick add.
 *
 * Compose on iOS keeps `rememberSaveable` only while the process lives. This registry is written to
 * a file each time the app goes to the background and read once at the next start. The file is
 * removed when it is read, so a state that cannot be restored is never tried twice. A state from
 * another build is ignored.
 */
@Composable
internal fun RestorableUiState(content: @Composable () -> Unit) {
    val registry = remember { SaveableStateRegistry(UiStateStore.take()) { true } }
    DisposableEffect(registry) {
        val center = NSNotificationCenter.defaultCenter
        val queue = NSOperationQueue.mainQueue
        val background = center.addObserverForName(UIApplicationDidEnterBackgroundNotification, null, queue) { _ ->
            UiStateStore.write(registry.performSave())
        }
        onDispose { center.removeObserver(background) }
    }
    CompositionLocalProvider(LocalSaveableStateRegistry provides registry, content = content)
}

internal object UiStateStore {
    private const val TAG = "UiStateStore"
    private val json = Json

    private val path: Path get() = Path("${IosPaths.applicationSupport}/ui-state.json")

    private val build: String by lazy {
        val info = NSBundle.mainBundle
        "${info.objectForInfoDictionaryKey("CFBundleShortVersionString")}-${info.objectForInfoDictionaryKey("CFBundleVersion")}"
    }

    /** The state saved when the app last went to the background, once; null when there is none. */
    fun take(): Map<String, List<Any?>>? {
        val file = path
        if (!SystemFileSystem.exists(file)) return null
        return try {
            val text = SystemFileSystem.source(file).buffered().use { it.readString() }
            val root = json.parseToJsonElement(text).jsonObject
            if (root["build"]?.jsonPrimitive?.content != build) null
            else UiStateCodec.decode(root.getValue("values").jsonObject)
        } catch (e: Exception) {
            Logger.w(TAG, "Saved UI state not restored: ${e.message}")
            null
        } finally {
            clear()
        }
    }

    fun write(values: Map<String, List<Any?>>) {
        try {
            val encoded = UiStateCodec.encode(values)
            if (encoded.skipped.isNotEmpty()) Logger.d(TAG, "Not saved: ${encoded.skipped}")
            val root = JsonObject(mapOf("build" to JsonPrimitive(build), "values" to encoded.values))
            val partial = Path("$path.part")
            SystemFileSystem.sink(partial).buffered().use { it.writeString(root.toString()) }
            SystemFileSystem.atomicMove(partial, path)
            IosPaths.excludeFromBackup(path.toString())
        } catch (e: Exception) {
            Logger.w(TAG, "UI state not saved: ${e.message}")
        }
    }

    private fun clear() {
        SystemFileSystem.delete(path, mustExist = false)
    }
}
