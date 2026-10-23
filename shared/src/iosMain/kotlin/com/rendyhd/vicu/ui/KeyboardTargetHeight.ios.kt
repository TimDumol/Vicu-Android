package com.rendyhd.vicu.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSValue
import platform.UIKit.CGRectValue
import platform.UIKit.UIKeyboardFrameEndUserInfoKey
import platform.UIKit.UIKeyboardWillChangeFrameNotification

/**
 * Compose on iOS has no `imeAnimationTarget`, so the target comes from UIKit: it announces the
 * keyboard's end frame before the keyboard moves (also when it hides, with a frame off the screen).
 * Until it announces one, the keyboard stays where it is now (read once, not followed).
 */
@Composable
actual fun keyboardTargetHeight(): Dp {
    val density = LocalDensity.current
    val ime = WindowInsets.ime
    var target by remember {
        mutableStateOf(Snapshot.withoutReadObservation { with(density) { ime.getBottom(density).toDp() } })
    }
    DisposableEffect(Unit) {
        val center = NSNotificationCenter.defaultCenter
        val observer = center.addObserverForName(
            name = UIKeyboardWillChangeFrameNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { notification -> target = keyboardOverlap(notification) }
        onDispose { center.removeObserver(observer) }
    }
    return target
}

/** How far the keyboard's end frame covers the app's window from below (points are dp on iOS). */
@OptIn(ExperimentalForeignApi::class)
private fun keyboardOverlap(notification: NSNotification?): Dp {
    val frame = notification?.userInfo?.get(UIKeyboardFrameEndUserInfoKey) as? NSValue ?: return 0.dp
    val window = keyWindow() ?: return 0.dp
    // The end frame is in screen coordinates; a null window converts from the screen.
    val (top, bottom) = window.convertRect(frame.CGRectValue(), fromWindow = null)
        .useContents { origin.y to origin.y + size.height }
    val windowHeight = window.bounds.useContents { size.height }
    // A floating or undocked keyboard (iPad) does not reach the bottom edge and takes no room.
    if (bottom < windowHeight) return 0.dp
    return (windowHeight - top).coerceAtLeast(0.0).dp
}
