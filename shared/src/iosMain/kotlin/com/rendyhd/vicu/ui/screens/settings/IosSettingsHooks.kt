package com.rendyhd.vicu.ui.screens.settings

import com.rendyhd.vicu.worker.SyncRequester

/** The iOS side of Settings. */
class IosSettingsHooks(
    private val syncRequester: () -> SyncRequester,
) : PlatformSettingsHooks {
    // The Control Center control takes the tile's place on iOS.
    override val supportsQuickAddTile: Boolean = false

    override fun updateWidgets() {}

    override fun scheduleSync(enabled: Boolean) {
        if (enabled) syncRequester().request()
    }

    override fun scheduleDailySummary(slot: String, enabled: Boolean, hour: Int, minute: Int) {}

    override fun sendTestNotification(): String? = "Notifications are not available on iOS yet"

    override fun requestQuickAddTile(onResult: (String) -> Unit) {}

    override fun triggerImmediateSync() {
        syncRequester().request()
    }
}
