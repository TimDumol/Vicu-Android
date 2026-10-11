package com.rendyhd.vicu.auth

import com.rendyhd.vicu.ios.IosBackgroundTasks

/**
 * The iOS side of the session: the background refresh keeps the refresh token alive while the
 * app is closed (Android's periodic token worker). Widgets come with the widget extension.
 */
class IosAuthHooks(
    private val backgroundTasks: () -> IosBackgroundTasks,
) : PlatformAuthHooks {
    override fun cancelRefreshScheduler() {
        backgroundTasks().cancelRefresh()
    }

    override fun scheduleRefresh() {
        backgroundTasks().scheduleRefresh()
    }

    override fun updateWidgets() {}
}
