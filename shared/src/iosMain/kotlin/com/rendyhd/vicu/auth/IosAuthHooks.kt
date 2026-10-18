package com.rendyhd.vicu.auth

import com.rendyhd.vicu.ios.IosBackgroundTasks
import com.rendyhd.vicu.widget.IosWidgetSnapshots

/**
 * The iOS side of the session: the background refresh keeps the refresh token alive while the
 * app is closed (Android's periodic token worker), and the widgets follow a sign-in or sign-out.
 */
class IosAuthHooks(
    private val backgroundTasks: () -> IosBackgroundTasks,
    private val widgets: () -> IosWidgetSnapshots,
) : PlatformAuthHooks {
    override fun cancelRefreshScheduler() {
        backgroundTasks().cancelRefresh()
    }

    override fun scheduleRefresh() {
        backgroundTasks().scheduleRefresh()
    }

    override fun updateWidgets() {
        widgets().requestWrite()
    }
}
