package com.rendyhd.vicu.ui.components.settings

import androidx.compose.runtime.Composable

/**
 * Banner shown in Settings when reminders cannot fire on time because the platform withholds a
 * permission for exact alarms (Android 12 and later). Platforms without that permission show
 * nothing.
 */
@Composable
expect fun ExactAlarmBanner()
