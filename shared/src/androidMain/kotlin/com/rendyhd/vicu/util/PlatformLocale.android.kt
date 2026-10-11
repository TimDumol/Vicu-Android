package com.rendyhd.vicu.util

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.Month
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

actual fun deviceLocaleTag(): String = Locale.getDefault().toLanguageTag()

actual fun localeDateSymbols(localeTag: String): LocaleDateSymbols {
    val locale = Locale.forLanguageTag(localeTag)
    val shortPattern = DateTimeFormatterBuilder.getLocalizedDateTimePattern(
        FormatStyle.SHORT, null, IsoChronology.INSTANCE, locale,
    )
    val marker = DateTimeFormatter.ofPattern("a", locale)
    return LocaleDateSymbols(
        monthFirst = shortPattern.indexOf('M').let { m -> m >= 0 && m < shortPattern.indexOf('d') },
        weekdaysShort = DayOfWeek.entries.map { it.getDisplayName(TextStyle.SHORT, locale) },
        weekdaysLong = DayOfWeek.entries.map { it.getDisplayName(TextStyle.FULL, locale) },
        monthsShort = Month.entries.map { it.getDisplayName(TextStyle.SHORT, locale) },
        monthsLong = Month.entries.map { it.getDisplayName(TextStyle.FULL, locale) },
        am = marker.format(LocalTime.of(1, 0)),
        pm = marker.format(LocalTime.of(13, 0)),
    )
}
