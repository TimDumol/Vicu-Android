package com.rendyhd.vicu.ios

import com.rendyhd.vicu.auth.AuthDebugLog
import com.rendyhd.vicu.auth.AuthManager
import com.rendyhd.vicu.auth.TokenRefreshRun
import com.rendyhd.vicu.data.local.dao.PendingActionDao
import com.rendyhd.vicu.data.remote.BaseUrlHolder
import com.rendyhd.vicu.notification.IosNotificationScheduler
import com.rendyhd.vicu.share.IosShareInbox
import com.rendyhd.vicu.util.Logger
import com.rendyhd.vicu.util.TimeSource
import com.rendyhd.vicu.widget.IosWidgetSnapshots
import com.rendyhd.vicu.worker.PeriodicSyncRunner
import com.rendyhd.vicu.worker.SyncEngine
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGProcessingTaskRequest
import platform.BackgroundTasks.BGTask
import platform.BackgroundTasks.BGTaskRequest
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.dateWithTimeIntervalSinceNow
import kotlin.concurrent.AtomicInt
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.minutes

/**
 * The background work iOS runs while the app is closed, in place of Android's WorkManager jobs:
 *
 * - the refresh ([REFRESH], BGAppRefreshTask, asked for every 30 minutes, run when iOS sees fit):
 *   keeps the session alive ([TokenRefreshRun]), syncs, and plans the notifications and widgets
 *   from what arrived ([PeriodicSyncRunner], as Android's periodic sync);
 * - the queue sync ([SYNC], BGProcessingTask with a network): sends changes still queued when the
 *   app went to the background without a connection.
 *
 * Both are registered in IosApp.start, before the app finishes launching, as iOS requires. The
 * identifiers are listed in Info.plist (BGTaskSchedulerPermittedIdentifiers).
 */
class IosBackgroundTasks(
    private val tokenRefreshRun: TokenRefreshRun,
    private val syncEngine: SyncEngine,
    private val notifications: IosNotificationScheduler,
    private val widgets: IosWidgetSnapshots,
    private val shareInbox: IosShareInbox,
    private val authManager: AuthManager,
    private val baseUrlHolder: BaseUrlHolder,
    private val pendingActionDao: PendingActionDao,
    private val activityLog: BackgroundActivityLog,
    private val time: TimeSource,
    private val appScope: CoroutineScope,
) {
    fun register() {
        val scheduler = BGTaskScheduler.sharedScheduler
        scheduler.registerForTaskWithIdentifier(REFRESH, usingQueue = null) { task -> task?.let(::runRefresh) }
        scheduler.registerForTaskWithIdentifier(SYNC, usingQueue = null) { task -> task?.let(::runQueueSync) }
    }

    /** Asks for the next refresh in about 30 minutes (iOS decides when). */
    fun scheduleRefresh() {
        submit(BGAppRefreshTaskRequest(REFRESH).apply { earliestBeginDate = NSDate.dateWithTimeIntervalSinceNow(REFRESH_INTERVAL) })
    }

    /** Asks for a queue sync when the network is there, if changes are waiting to be sent. */
    suspend fun scheduleQueueSyncIfNeeded() {
        if (pendingActionDao.getRetryable().isEmpty()) return
        submit(
            BGProcessingTaskRequest(SYNC).apply {
                requiresNetworkConnectivity = true
                requiresExternalPower = false
            },
        )
    }

    /** Sign-out: nothing runs for an account that is gone. */
    fun cancelAll() {
        BGTaskScheduler.sharedScheduler.cancelAllTaskRequests()
    }

    fun cancelRefresh() {
        BGTaskScheduler.sharedScheduler.cancelTaskRequestWithIdentifier(REFRESH)
    }

    private fun runRefresh(task: BGTask) {
        // The next one first: a run that is cut short still leaves one asked for.
        scheduleRefresh()
        run(task, "refresh") {
            val token = tokenRefreshRun.run("iOS background refresh")
            // What was shared to Vicu and what the widget buttons did go out with this sync.
            shareInbox.import()
            widgets.drainActions()
            val outcome = PeriodicSyncRunner(
                isSignedIn = ::isSignedIn,
                sync = syncEngine::performSync,
                rescheduleAlarms = notifications::replan,
                updateWidgets = widgets::write,
            ).run()
            "session ${token.name.lowercase()}, ${outcome.name.lowercase()}"
        }
    }

    private fun runQueueSync(task: BGTask) {
        run(task, "queue sync") {
            if (!isSignedIn()) return@run "skipped"
            val synced = syncEngine.performSync()
            notifications.replan()
            widgets.write()
            if (!synced) scheduleQueueSyncIfNeeded()
            if (synced) "synced" else "incomplete"
        }
    }

    private suspend fun isSignedIn(): Boolean {
        baseUrlHolder.ensureInitialized()
        return !authManager.getVikunjaUrl().isNullOrBlank()
    }

    /** Runs [work] for [task] and tells iOS once, when it ends or when iOS takes the time back. */
    private fun run(task: BGTask, name: String, work: suspend () -> String) {
        val finished = AtomicInt(0)
        val started = time.now()
        fun finish(success: Boolean) {
            if (finished.compareAndSet(0, 1)) task.setTaskCompletedWithSuccess(success)
        }
        val job = appScope.launch {
            val result = try {
                work()
            } catch (e: CancellationException) {
                activityLog.record("$name: stopped by iOS")
                finish(false)
                throw e
            } catch (e: Exception) {
                Logger.w(TAG, "Background $name failed: ${e.message}")
                "failed"
            }
            val seconds = (time.now() - started).inWholeSeconds
            activityLog.record("$name: $result (${seconds}s)")
            AuthDebugLog.log("BACKGROUND", "$name: $result (${seconds}s)")
            finish(true)
        }
        task.expirationHandler = {
            job.cancel()
            finish(false)
        }
    }

    private fun submit(request: BGTaskRequest) {
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            if (!BGTaskScheduler.sharedScheduler.submitTaskRequest(request, error.ptr)) {
                // The simulator never runs background tasks and refuses them here.
                Logger.d(TAG, "Background task ${request.identifier} not scheduled: ${error.value?.localizedDescription}")
            }
        }
    }

    companion object {
        const val REFRESH = "com.rendyhd.vicu.refresh"
        const val SYNC = "com.rendyhd.vicu.sync"
        private const val TAG = "IosBackgroundTasks"
        private val REFRESH_INTERVAL = 30.minutes.inWholeSeconds.toDouble()
    }
}
