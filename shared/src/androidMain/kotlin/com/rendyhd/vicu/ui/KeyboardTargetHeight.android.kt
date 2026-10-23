package com.rendyhd.vicu.ui

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

/** Without an animation running, the target is where the keyboard is. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
actual fun keyboardTargetHeight(): Dp {
    val density = LocalDensity.current
    return with(density) { WindowInsets.imeAnimationTarget.getBottom(density).toDp() }
}
