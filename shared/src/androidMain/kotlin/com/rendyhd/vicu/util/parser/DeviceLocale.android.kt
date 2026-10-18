package com.rendyhd.vicu.util.parser

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

private val dayFirstByTag = ConcurrentHashMap<String, Boolean>()

internal actual fun isDayFirstLocale(tag: String): Boolean =
    dayFirstByTag.getOrPut(tag) {
        val pattern = runCatching {
            (DateFormat.getDateInstance(DateFormat.SHORT, Locale.forLanguageTag(tag)) as? SimpleDateFormat)?.toPattern()
        }.getOrNull().orEmpty()
        isDayFirstPattern(pattern)
    }
