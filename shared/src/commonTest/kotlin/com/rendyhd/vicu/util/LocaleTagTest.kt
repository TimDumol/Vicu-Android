package com.rendyhd.vicu.util

import kotlin.test.Test
import kotlin.test.assertEquals

class LocaleTagTest {

    @Test
    fun `language is the first subtag in lower case`() {
        assertEquals("en", languageOf("en"))
        assertEquals("en", languageOf("EN-gb"))
        assertEquals("nl", languageOf("nl_NL"))
    }

    @Test
    fun `region skips a script subtag and accepts a numeric region`() {
        assertEquals("", regionOf("en"))
        assertEquals("GB", regionOf("en-GB"))
        assertEquals("US", regionOf("en_us"))
        assertEquals("TW", regionOf("zh-Hant-TW"))
        assertEquals("419", regionOf("es-419"))
        assertEquals("", regionOf("en-u-hc-h23"))
    }
}
