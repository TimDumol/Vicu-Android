package com.rendyhd.vicu.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

/**
 * Where the keyboard is going: how much of the window it takes from below at its full height, as
 * soon as it starts to open, and zero as soon as it starts to close. Zero without a keyboard.
 *
 * It changes once per opening or closing, never during the slide, so reading it in composition does
 * not recompose every frame of the keyboard's animation (on Android that animation runs on the UI
 * thread, so a heavy recomposition per frame made the keyboard itself stutter). Something that
 * follows the keyboard frame by frame reads `WindowInsets.ime` while laying out instead.
 */
@Composable
expect fun keyboardTargetHeight(): Dp
