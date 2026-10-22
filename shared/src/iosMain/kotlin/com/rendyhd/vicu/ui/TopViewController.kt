package com.rendyhd.vicu.ui

import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene

/**
 * The view controller on top of the app's key window, to present system sheets (sign-in, pickers,
 * the share sheet) over the Compose UI. Null before the first scene is connected.
 */
internal fun topViewController(): UIViewController? {
    var controller = keyWindow()?.rootViewController
    while (controller?.presentedViewController != null) {
        controller = controller.presentedViewController
    }
    return controller
}

/** The app's key window (or its first window). Null before the first scene is connected. */
internal fun keyWindow(): UIWindow? {
    val windows = UIApplication.sharedApplication.connectedScenes
        .filterIsInstance<UIWindowScene>()
        .flatMap { scene -> scene.windows.filterIsInstance<UIWindow>() }
    return windows.firstOrNull { it.isKeyWindow() } ?: windows.firstOrNull()
}
