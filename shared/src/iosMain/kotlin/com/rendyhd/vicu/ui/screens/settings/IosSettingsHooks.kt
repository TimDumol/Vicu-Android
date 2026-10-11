package com.rendyhd.vicu.ui.screens.settings

import com.rendyhd.vicu.ios.BackgroundActivityLog
import com.rendyhd.vicu.notification.IosNotificationScheduler
import com.rendyhd.vicu.permission.NotificationPermissionPlatform
import com.rendyhd.vicu.worker.SyncRequester

/** The iOS side of Settings. */
class IosSettingsHooks(
    private val syncRequester: () -> SyncRequester,
    private val notifications: () -> IosNotificationScheduler,
    private val permission: NotificationPermissionPlatform,
    private val backgroundLog: BackgroundActivityLog,
) : PlatformSettingsHooks {
    // The Control Center control takes the tile's place on iOS.
    override val supportsQuickAddTile: Boolean = false

    override fun updateWidgets() {
        notifications().requestReplan()
    }

    override fun scheduleSync(enabled: Boolean) {
        if (enabled) syncRequester().request()
    }

    /** The summaries are planned with the other notifications, from the settings as they are now. */
    override fun scheduleDailySummary(slot: String, enabled: Boolean, hour: Int, minute: Int) {
        notifications().requestReplan()
    }

    override fun sendTestNotification(): String? {
        if (!permission.isGranted()) throw Exception("Notification permission not granted")
        notifications().sendTest()
        return "Test notification sent"
    }

    override fun requestQuickAddTile(onResult: (String) -> Unit) {}

    override fun triggerImmediateSync() {
        syncRequester().request()
    }

    override val hasBackgroundActivityLog: Boolean = true

    override suspend fun readBackgroundActivityLog(): String = backgroundLog.read()
}
