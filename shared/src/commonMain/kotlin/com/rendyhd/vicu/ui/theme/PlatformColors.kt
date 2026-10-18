package com.rendyhd.vicu.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/**
 * The device's own colour scheme (Material You on Android 12 and later), or null where the
 * platform has none; the Vicu scheme is used then.
 */
@Composable
expect fun platformDynamicColorScheme(darkTheme: Boolean): ColorScheme?

/** Whether Settings offers "Use device colours" on this platform. */
expect val supportsDeviceColors: Boolean
