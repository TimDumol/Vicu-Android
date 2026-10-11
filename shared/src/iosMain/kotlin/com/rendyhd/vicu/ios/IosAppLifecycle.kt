package com.rendyhd.vicu.ios

import com.rendyhd.vicu.auth.AuthManager
import com.rendyhd.vicu.auth.AuthState
import com.rendyhd.vicu.auth.AuthTransitionAction
import com.rendyhd.vicu.auth.AuthTransitions
import com.rendyhd.vicu.data.remote.BaseUrlHolder
import com.rendyhd.vicu.permission.IosNotificationPermissionPlatform
import com.rendyhd.vicu.util.DayClock
import com.rendyhd.vicu.util.Logger
import com.rendyhd.vicu.util.NetworkMonitor
import com.rendyhd.vicu.util.reconnections
import com.rendyhd.vicu.worker.SyncEngine
import com.rendyhd.vicu.worker.SyncRequester
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundTaskIdentifier
import platform.UIKit.UIBackgroundTaskInvalid
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
    private val appScope: CoroutineScope,
) {
    private val signedIn: Boolean get() = authManager.authState.value == AuthState.Authenticated

    fun start() {
        notificationPermission.refresh()
        appScope.launch {
            val storedUrl = authManager.getVikunjaUrl()
            if (!storedUrl.isNullOrBlank()) baseUrlHolder.baseUrl = storedUrl
            authManager.initialize()
            if (signedIn) syncRequester.request()
        }
        appScope.launch {
            val rules = AuthTransitions()
            authManager.authState.collect { state ->
                for (action in rules.on(state)) {
                    when (action) {
                        AuthTransitionAction.SYNC_NOW -> syncRequester.request()
                        AuthTransitionAction.ENSURE_SUMMARIES,
                        AuthTransitionAction.RESCHEDULE_ACCOUNT_WORK -> Unit
                    }
                }
            }
        }
        // A sync that failed offline waits out its backoff; start it as soon as the network is back.
        appScope.launch {
            networkMonitor.isOnline.reconnections().collect {
                if (!authManager.getVikunjaUrl().isNullOrBlank()) syncRequester.request()
            }
        }
    }

    /** The app came to the front. */
    fun onActive() {
        dayClock.refresh()
        notificationPermission.refresh()
        if (signedIn) syncRequester.request()
    }

    /** The app went to the background: send what is queued while iOS still lets it run. */
    fun onBackground() {
        if (!signedIn) return
        val app = UIApplication.sharedApplication
        var task: UIBackgroundTaskIdentifier = UIBackgroundTaskInvalid
        val job = appScope.launch {
            try {
                withTimeoutOrNull(BACKGROUND_SYNC_TIMEOUT) { syncEngine.performSync() }
            } catch (e: Exception) {
                Logger.w(TAG, "Background sync failed: ${e.message}")
            } finally {
                if (task != UIBackgroundTaskInvalid) app.endBackgroundTask(task)
                task = UIBackgroundTaskInvalid
            }
        }
        task = app.beginBackgroundTaskWithName("Vicu sync") {
            job.cancel()
            if (task != UIBackgroundTaskInvalid) app.endBackgroundTask(task)
            task = UIBackgroundTaskInvalid
        }
    }

    private companion object {
        const val TAG = "IosAppLifecycle"
        val BACKGROUND_SYNC_TIMEOUT = 25.seconds
    }
}
