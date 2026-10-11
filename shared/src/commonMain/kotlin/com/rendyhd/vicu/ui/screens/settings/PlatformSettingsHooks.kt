package com.rendyhd.vicu.ui.screens.settings

interface PlatformSettingsHooks {
    val supportsQuickAddTile: Boolean
    fun updateWidgets()
    fun scheduleSync(enabled: Boolean)
    fun scheduleDailySummary(slot: String, enabled: Boolean, hour: Int, minute: Int)
    fun sendTestNotification(): String?
    fun requestQuickAddTile(onResult: (String) -> Unit)
    fun triggerImmediateSync()

    /**
     * A record of the work the platform ran in the background (iOS decides when its background
     * refresh runs), shown in Settings when there is one.
     */
    val hasBackgroundActivityLog: Boolean get() = false

    suspend fun readBackgroundActivityLog(): String = ""
}
