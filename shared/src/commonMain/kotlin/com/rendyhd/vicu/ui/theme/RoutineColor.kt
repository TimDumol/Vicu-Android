package com.rendyhd.vicu.ui.theme

import androidx.compose.ui.graphics.Color
import com.rendyhd.vicu.util.parseHexColor

/** A mark in a routine's own colour (its check ring, its icon) must read at this contrast on its surface. */
private const val ROUTINE_MARK_CONTRAST = 3.0

/**
 * A routine's own colour ([hex], as the routine stores it, `definition.color`) as a mark on
 * [surfaces]: moved towards [onSurface] only as far as it takes to read at 3:1 on each of them, so a
 * yellow ring still shows on a light card. It keeps its hue with device colours on, like a label.
 * Null when the routine has no colour or one that cannot be read; the caller falls back to its own.
 */
fun routineMarkColor(hex: String, onSurface: Color, surfaces: List<Color>): Color? =
    parseHexColor(hex)?.copy(alpha = 1f)?.let { ensureContrast(it, onSurface, surfaces, ROUTINE_MARK_CONTRAST) }
