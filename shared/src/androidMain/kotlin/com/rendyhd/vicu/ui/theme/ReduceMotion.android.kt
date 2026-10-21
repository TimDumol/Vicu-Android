package com.rendyhd.vicu.ui.theme

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

@Composable
actual fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    fun read(): Boolean =
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    var reduce by remember { mutableStateOf(read()) }
    // Changed in the system settings, so look again when the user comes back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { reduce = read() }
    return reduce
}
