package com.rendyhd.vicu.permission

import kotlin.concurrent.Volatile
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenNotificationSettingsURLString
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatus
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * The notification permission on iOS. The system answers asynchronously while the shared code
 * asks synchronously, so the answer is cached: [refresh] reads it at start and each time the app
 * comes to the front (IosAppLifecycle). Building this does not touch the notification center, which
 * exists only in a running app. iOS shows its prompt only once; after that the settings page is
 * the way back.
 */
class IosNotificationPermissionPlatform : NotificationPermissionPlatform {

    @Volatile
    private var status: UNAuthorizationStatus = UNAuthorizationStatusNotDetermined

    override val needsRuntimePermission: Boolean = true

    override fun isGranted(): Boolean = status == UNAuthorizationStatusAuthorized ||
        status == UNAuthorizationStatusProvisional ||
        status == UNAuthorizationStatusEphemeral

    override fun launchSystemRequest(): Boolean {
        if (status != UNAuthorizationStatusNotDetermined) {
            openSystemSettings()
            return true
        }
        UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound,
        ) { _, _ -> refresh() }
        return true
    }

    override fun openSystemSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenNotificationSettingsURLString) ?: return
        dispatch_async(dispatch_get_main_queue()) {
            UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any?>(), completionHandler = null)
        }
    }

    /** Reads the permission again; call when the app comes to the front. */
    fun refresh() {
        UNUserNotificationCenter.currentNotificationCenter().getNotificationSettingsWithCompletionHandler { settings ->
            if (settings != null) status = settings.authorizationStatus
        }
    }
}
