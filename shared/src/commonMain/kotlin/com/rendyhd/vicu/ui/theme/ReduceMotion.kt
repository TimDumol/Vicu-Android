package com.rendyhd.vicu.ui.theme

import androidx.compose.runtime.Composable

/**
 * Whether the system asks for less motion: Android's animator scale set to off, iOS's Reduce Motion.
 * A component that animates a transform (a scale, a slide, a pulse) uses its reduced variant then:
 * fades only (docs/design-system-v1.md, section 6). Springs and tweens already follow Android's
 * animator scale; this is for what they cannot tell, such as a pulse driven by the voice.
 */
@Composable
expect fun rememberReduceMotion(): Boolean
