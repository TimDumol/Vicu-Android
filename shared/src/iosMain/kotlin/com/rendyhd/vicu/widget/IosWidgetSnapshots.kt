package com.rendyhd.vicu.widget

import com.rendyhd.vicu.data.local.CustomListStore
import com.rendyhd.vicu.data.local.RoutinePrefsStore
import com.rendyhd.vicu.domain.model.OccurrenceStatus
import com.rendyhd.vicu.domain.repository.RoutineRepository
import com.rendyhd.vicu.domain.repository.TaskRepository
import com.rendyhd.vicu.ui.components.shared.deviceUses24Hour
import com.rendyhd.vicu.ui.navigation.AppLink
import com.rendyhd.vicu.ui.navigation.ViewTarget
import com.rendyhd.vicu.util.ClockDay
import com.rendyhd.vicu.util.DateDisplayFormat
import com.rendyhd.vicu.util.DayClock
import com.rendyhd.vicu.util.DueDates
import com.rendyhd.vicu.util.IosPaths
import com.rendyhd.vicu.util.Logger
import com.rendyhd.vicu.worker.SyncRequester
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

/** WidgetKit, which only Swift can call: the app's Swift code implements this and hands it to IosApp.start. */
interface AppleWidgetBridge {
    fun reloadAllTimelines()
}

/**
 * Writes what the iOS widgets show into the App Group (`widgets/`), for the Swift widget
 * extension to read: a snapshot of every view a widget can show (the smart lists, each project
 * and custom list) for today and tomorrow, the catalog the widget configuration offers, the
 * routine day, and the colours. Before writing it takes in the actions the widget buttons left
 * in `widgets/actions/` (a task completed, a routine logged), stores them as the app would and
 * syncs. Writes are debounced; WidgetKit is then asked to reload through [bridge].
 */
class IosWidgetSnapshots(
    private val states: TaskWidgetStates,
    private val customListStore: CustomListStore,
    private val routineRepository: RoutineRepository,
    private val routinePrefs: RoutinePrefsStore,
    private val taskRepository: TaskRepository,
    private val dayClock: DayClock,
    private val syncRequester: () -> SyncRequester,
    private val appScope: CoroutineScope,
    /** The folder shared with the widget extension; null when the build has no App Group. */
    private val folder: () -> String? = { IosPaths.appGroup?.let { IosPaths.subdirectory(it, "widgets") } },
) {
    var bridge: AppleWidgetBridge? = null

    private val mutex = Mutex()
    private val requests = Channel<Unit>(Channel.CONFLATED)
    private var started = false
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    private val root: String? get() = folder()

    fun start() {
        if (started) return
        started = true
        appScope.launch {
            for (request in requests) {
                delay(DEBOUNCE)
                write()
            }
        }
    }

    fun requestWrite() {
        requests.trySend(Unit)
    }

    /**
     * Takes in the widget actions, then writes every snapshot and asks WidgetKit to reload when
     * something changed (reloads from the background count against a daily budget).
     */
    suspend fun write(): Unit = mutex.withLock {
        val root = root ?: return@withLock
        try {
            drainActions(root)
            if (writeAll(root)) bridge?.reloadAllTimelines()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logger.w(TAG, "Widget snapshots not written: ${e.message}")
        }
    }

    /** Stores what the widget buttons did while the app was not running; true when there was something. */
    suspend fun drainActions(): Boolean = mutex.withLock { root?.let { drainActions(it) } ?: false }

    private suspend fun drainActions(root: String): Boolean {
        val dir = Path("$root/${WidgetSnapshots.ACTIONS_DIR}")
        if (!SystemFileSystem.exists(dir)) return false
        val files = SystemFileSystem.list(dir).filter { it.name.endsWith(".json") }.sortedBy { it.name }
        for (file in files) {
            try {
                val action = json.decodeFromString(WidgetAction.serializer(), SystemFileSystem.source(file).buffered().use { it.readString() })
                when (action.kind) {
                    // Explicit completion, as a tap on the Android widget: never reopens a done task.
                    WidgetAction.COMPLETE_TASK -> taskRepository.setDoneInBackground(action.taskId, true)
                    WidgetAction.ROUTINE_STATUS -> OccurrenceStatus.entries.firstOrNull { it.name == action.status }?.let { status ->
                        routineRepository.setOccurrenceStatus(action.routineId, action.date, action.slotId, status)
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Logger.w(TAG, "Widget action ${file.name} not applied: ${e.message}")
            }
            // One attempt: an action that cannot be applied is not tried over and over.
            SystemFileSystem.delete(file, mustExist = false)
        }
        if (files.isNotEmpty()) syncRequester().request()
        return files.isNotEmpty()
    }

    /** True when a file changed. */
    private suspend fun writeAll(root: String): Boolean {
        var changed = false
        val viewsDir = IosPaths.subdirectory(root, WidgetSnapshots.VIEWS_DIR)
        val (light, dark) = WidgetSnapshots.palettes()
        val signedIn = states.isSignedIn()
        val written = mutableSetOf<String>()
        val catalog = mutableListOf<WidgetCatalogEntry>()

        if (signedIn) {
            dayClock.refresh()
            val today = dayClock.day.value
            val tomorrow = ClockDay(today.date.plus(1, DateTimeUnit.DAY), today.zone)
            val todayBatch = states.prepare(today)
            val tomorrowBatch = states.prepare(tomorrow)
            val format = DateDisplayFormat.system(deviceUses24Hour())
            val scheme = IosPaths.urlScheme

            val configs = WidgetSnapshots.smartLists.map { it to "list" } +
                todayBatch.projectNames.entries.sortedBy { it.value.lowercase() }.map { (id, title) ->
                    WidgetConfig(WidgetViewType.PROJECT, id.toString(), title) to "project"
                } +
                customListStore.getAll().first().map { WidgetConfig(WidgetViewType.CUSTOM_LIST, it.id, it.name) to "customList" }

            for ((config, kind) in configs) {
                val snapshot = WidgetSnapshots.snapshot(
                    today = todayBatch.build(config),
                    tomorrow = tomorrowBatch.build(config),
                    days = today to tomorrow,
                    scheme = scheme,
                    format = format,
                )
                changed = writeFile("$viewsDir/${snapshot.key}.json", json.encodeToString(TaskWidgetSnapshot.serializer(), snapshot)) || changed
                written += "${snapshot.key}.json"
                catalog += WidgetCatalogEntry(snapshot.key, config.viewName, kind)
            }

            val routines = RoutineWidgetSnapshot(
                enabled = routinePrefs.enabled.first(),
                openUrl = AppLink.OpenView(ViewTarget.Routines).toUrl(scheme),
                days = listOf(today, tomorrow).map { day ->
                    RoutineWidgetState.from(routineRepository.observeDay(day.date.toString()).first())
                },
                nextDayAtMillis = DueDates.startOfDay(tomorrow.date, tomorrow.zone).toEpochMilliseconds(),
            )
            changed = writeFile("$root/${WidgetSnapshots.ROUTINES_FILE}", json.encodeToString(RoutineWidgetSnapshot.serializer(), routines)) || changed
        } else {
            val routines = Path("$root/${WidgetSnapshots.ROUTINES_FILE}")
            if (SystemFileSystem.exists(routines)) {
                SystemFileSystem.delete(routines)
                changed = true
            }
        }

        // Views that are gone (a deleted project, signed out) leave no stale snapshot behind.
        SystemFileSystem.list(Path(viewsDir)).filter { it.name !in written }.forEach {
            SystemFileSystem.delete(it, mustExist = false)
            changed = true
        }

        val index = WidgetCatalog(signedIn = signedIn, views = catalog, light = light, dark = dark)
        return writeFile("$root/${WidgetSnapshots.CATALOG_FILE}", json.encodeToString(WidgetCatalog.serializer(), index)) || changed
    }

    /**
     * Written whole, then moved into place, so the widget never reads half a file; left alone
     * when it already holds [text]. True when the file changed.
     */
    private fun writeFile(path: String, text: String): Boolean {
        val target = Path(path)
        if (SystemFileSystem.exists(target) && SystemFileSystem.source(target).buffered().use { it.readString() } == text) return false
        val partial = Path("$path.part")
        SystemFileSystem.sink(partial).buffered().use { it.writeString(text) }
        SystemFileSystem.atomicMove(partial, target)
        return true
    }

    private companion object {
        const val TAG = "IosWidgetSnapshots"
        val DEBOUNCE = 1.seconds
    }
}
