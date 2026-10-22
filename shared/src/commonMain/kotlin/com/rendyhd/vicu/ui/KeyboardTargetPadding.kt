package com.rendyhd.vicu.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Room for the keyboard that is at the keyboard's full height as soon as it starts to open, and
 * follows it down while it closes (`imePadding` follows it frame by frame both ways).
 *
 * For a bottom sheet that opens together with the keyboard: padded by `imePadding`, the sheet grew
 * on every frame of the keyboard's slide, which moved its expanded position and restarted its own
 * enter animation each frame, so it stuttered. Padded by this, it is resized once when the keyboard
 * starts and the two slide up together.
 */
@Composable
expect fun Modifier.keyboardTargetPadding(): Modifier
