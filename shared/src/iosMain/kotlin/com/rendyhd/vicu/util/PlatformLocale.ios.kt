package com.rendyhd.vicu.util

import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.localeIdentifier

actual fun deviceLocaleTag(): String = NSLocale.currentLocale.localeIdentifier.replace('_', '-')

actual fun localeDateSymbols(localeTag: String): LocaleDateSymbols {
    val formatter = NSDateFormatter().apply {
        locale = NSLocale(localeIdentifier = localeTag.replace('-', '_'))
        dateStyle = NSDateFormatterShortStyle
        timeStyle = NSDateFormatterNoStyle
    }
    val shortPattern = formatter.dateFormat
    return LocaleDateSymbols(
        monthFirst = shortPattern.indexOf('M').let { m -> m >= 0 && m < shortPattern.indexOf('d') },
        // Foundation lists weekdays from Sunday; the contract counts from Monday.
        weekdaysShort = formatter.shortWeekdaySymbols.fromMonday(),
        weekdaysLong = formatter.weekdaySymbols.fromMonday(),
        monthsShort = formatter.shortMonthSymbols.strings(),
        monthsLong = formatter.monthSymbols.strings(),
        am = formatter.AMSymbol,
        pm = formatter.PMSymbol,
    )
}

private fun List<*>?.strings(): List<String> = orEmpty().map { it.toString() }

private fun List<*>?.fromMonday(): List<String> = strings().let { if (it.size == 7) it.drop(1) + it.first() else it }
