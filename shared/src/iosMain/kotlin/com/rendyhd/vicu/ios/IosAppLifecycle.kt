package com.rendyhd.vicu.ios

import com.rendyhd.vicu.auth.AuthManager
import com.rendyhd.vicu.auth.AuthState
import com.rendyhd.vicu.auth.AuthTransitionAction
import com.rendyhd.vicu.auth.AuthTransitions
import com.rendyhd.vicu.data.remote.BaseUrlHolder
import com.rendyhd.vicu.notification.IosNotificationScheduler
import com.rendyhd.vicu.permission.IosNotificationPermissionPlatform
import com.rendyhd.vicu.share.IosShareInbox
import com.rendyhd.vicu.util.DayClock
import com.rendyhd.vicu.util.Logger
import com.rendyhd.vicu.util.NetworkMonitor
import com.rendyhd.vicu.util.reconnections
import com.rendyhd.vicu.widget.IosWidgetSnapshots
import com.rendyhd.vicu.worker.SyncEngine
import com.rendyhd.vicu.worker.SyncRequester
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/**
 * What the Android activity and application do at start, on resume and when the session changes,
 * for the iOS app process. [start] runs once from the app delegate, so it also runs when iOS
 * starts the app in the background (a notification action, a background task).
 */
class IosAppLifecycle(
    private val authManager: AuthManager,
    private val baseUrlHolder: BaseUrlHolder,
    private val syncRequester: SyncRequester,
    private val syncEngine: SyncEngine,
    private val networkMonitor: NetworkMonitor,
    private val dayClock: DayClock,
    private val notificationPermission: IosNotificationPermissionPlatform,
    private val notifications: IosNotificationScheduler,
    private val backgroundTasks: IosBackgroundTasks,
    private val widgets: IosWidgetSnapshots,
    private val shareInbox: IosShareInbox,
    private val appScope: CoroutineScope,
) {
    private val signedIn: Boolean get() = authManager.authState.value == AuthState.Authenticated

    fun start() {
        notificationPermission.refresh()
        notifications.start()
        widgets.start()
        appScope.launch {
            val storedUrl = authManager.getVikunjaUrl()
            if (!storedUrl.isNullOrBlank()) baseUrlHolder.baseUrl = storedUrl
            authManager.initialize()
            if (signedIn) {
                shareInbox.import()
                syncRequester.request()
            }
            // From the data on the device, before the sync: an app started for a notification
            // action or a background task plans at once.
            if (!storedUrl.isNullOrBlank()) {
                notifications.requestReplan()
                backgroundTasks.scheduleRefresh()
            }
            // Also takes in what the widget buttons did while the app was not running.
            widgets.requestWrite()
        }
        // The summaries and the widgets' days are per local day: a new day (or zone) plans again.
        appScope.launch {
            dayClock.day.drop(1).collect {
                notifications.requestReplan()
                widgets.requestWrite()
            }
        }
        appScope.launch {
            val rules = AuthTransitions()
            authManager.authState.collect { state ->
                for (action in rules.on(state)) {
                    when (action) {
                        AuthTransitionAction.SYNC_NOW -> syncRequester.request()
                        // The summaries are part of the planned notifications.
                        AuthTransitionAction.ENSURE_SUMMARIES -> notifications.requestReplan()
                        AuthTransitionAction.RESCHEDULE_ACCOUNT_WORK -> {
                            notifications.requestReplan()
                            backgroundTasks.scheduleRefresh()
                        }
                    }
                }
            }
        }
        // A sync that failed offline waits out its backoff; start it as soon as the network is back.
        appScope.launch {
            networkMonitor.isOnline.reconnections().collect {
                if (!authManager.getVikunjaUrl().isNullOrBlank()) {
                    // Shares with files wait for a connection.
                    shareInbox.import()
                    syncRequester.request()
                }
            }
        }
    }

    /** The app came to the front. */
    fun onActive() {
        dayClock.refresh()
        notificationPermission.refresh()
        if (signedIn) {
            syncRequester.request()
            // Something shared to Vicu from another app while it was away.
            appScope.launch { shareInbox.import() }
        }
        // Also after the permission prompt: requests added before it was answered are refused.
        notifications.requestReplan()
        // A widget button tapped while the app was in the background.
        widgets.requestWrite()
    }

    /** The app went to the background: send what is queued while iOS still lets it run. */
    fun onBackground() {
        if (!signedIn) return
        backgroundTasks.scheduleRefresh()
        appScope.launchInBackgroundTask("Vicu sync", BACKGROUND_SYNC_TIMEOUT) {
            syncEngine.performSync()
            notifications.replan()
            widgets.write()
            // What could not be sent goes out with the next connection, even if the app stays closed.
            backgroundTasks.scheduleQueueSyncIfNeeded()
        }
    }

    private companion object {
        const val TAG = "IosAppLifecycle"
        val BACKGROUND_SYNC_TIMEOUT = 25.seconds
    }
}
