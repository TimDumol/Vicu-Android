package com.rendyhd.vicu.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

@Composable
actual fun rememberReduceMotion(): Boolean {
    var reduce by remember { mutableStateOf(UIAccessibilityIsReduceMotionEnabled()) }
    // Changed in the Settings app, so look again when the user comes back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { reduce = UIAccessibilityIsReduceMotionEnabled() }
    return reduce
}
