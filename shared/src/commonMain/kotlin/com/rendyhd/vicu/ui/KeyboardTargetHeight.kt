package com.rendyhd.vicu.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

/**
 * How much of the window the keyboard takes from below, at the keyboard's full height as soon as
 * it starts to open, and following it down while it closes (`imePadding` follows it frame by
 * frame both ways). Zero without a keyboard.
 *
 * For a sheet that opens together with the keyboard: padded by `imePadding`, the sheet grew on
 * every frame of the keyboard's slide, so it climbed after the keyboard instead of with it. Sized
 * by this, it is resized once when the keyboard starts and the two slide up together.
 */
@Composable
expect fun keyboardTargetHeight(): Dp
