package com.rendyhd.vicu.ui.components.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IconColorPickerTest {

    @Test
    fun `a colour picked elsewhere gets its own swatch`() {
        assertEquals("#af52de", otherColorHex(normalizedColorHex("#AF52DE")))
    }

    @Test
    fun `no colour or a preset or an unreadable colour gets none`() {
        assertNull(otherColorHex(normalizedColorHex("")))
        assertNull(otherColorHex(normalizedColorHex("E8384F")))
        assertNull(otherColorHex(normalizedColorHex("purple")))
    }
}
