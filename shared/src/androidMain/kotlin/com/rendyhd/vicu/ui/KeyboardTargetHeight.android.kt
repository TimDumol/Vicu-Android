package com.rendyhd.vicu.ui

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
actual fun keyboardTargetHeight(): Dp {
    val density = LocalDensity.current
    val now = WindowInsets.ime.getBottom(density)
    val target = WindowInsets.imeAnimationTarget.getBottom(density)
    return with(density) { maxOf(now, target).toDp() }
}
