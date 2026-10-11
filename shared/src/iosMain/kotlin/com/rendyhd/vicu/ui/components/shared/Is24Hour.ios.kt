package com.rendyhd.vicu.ui.components.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale

// The "j" template expands to the locale's preferred hour format; a 12-hour one has an AM/PM marker.
private fun is24Hour(): Boolean =
    NSDateFormatter.dateFormatFromTemplate("j", 0u, NSLocale.currentLocale)?.contains("a") != true

@Composable
actual fun rememberIs24HourFormat(): Boolean {
    var value by remember { mutableStateOf(is24Hour()) }
    // The setting is changed in the Settings app, so look again when the user comes back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { value = is24Hour() }
    return value
}
