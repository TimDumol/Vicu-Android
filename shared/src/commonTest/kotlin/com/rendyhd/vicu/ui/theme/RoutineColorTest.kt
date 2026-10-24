package com.rendyhd.vicu.ui.theme

import androidx.compose.ui.graphics.Color
import com.rendyhd.vicu.util.PRESET_COLORS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoutineColorTest {

    // The phone's colours, the desktop's routine colours, and the extremes.
    private val routineColors = PRESET_COLORS + listOf("#AF52DE", "#007AFF", "#34C759", "#FF9500", "#FF3B30", "#5AC8FA", "#FFFFFF", "#000000")

    @Test
    fun `a routine colour reads at 3 to 1 on the routine card in both themes`() {
        for ((name, scheme) in listOf("light" to VicuLightColorScheme, "dark" to VicuDarkColorScheme)) {
            val card = scheme.surfaceContainerLow
            for (hex in routineColors) {
                val mark = routineMarkColor(hex, scheme.onSurface, listOf(card))!!
                assertTrue(contrastRatio(mark, card) >= 3.0, "$name $hex: ${contrastRatio(mark, card)}")
            }
        }
    }

    @Test
    fun `a colour that already reads is left alone`() {
        val scheme = VicuLightColorScheme
        assertEquals(Color(0xFF007AFF), routineMarkColor("#007AFF", scheme.onSurface, listOf(scheme.surfaceContainerLow)))
    }

    @Test
    fun `no colour or an unreadable one gives no mark`() {
        val scheme = VicuLightColorScheme
        assertNull(routineMarkColor("", scheme.onSurface, listOf(scheme.surface)))
        assertNull(routineMarkColor("purple", scheme.onSurface, listOf(scheme.surface)))
    }

    @Test
    fun `a translucent colour is drawn opaque`() {
        val scheme = VicuLightColorScheme
        assertEquals(1f, routineMarkColor("#80007AFF", scheme.onSurface, listOf(scheme.surface))!!.alpha)
    }
}
