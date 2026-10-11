package com.rendyhd.vicu.util.parser

/**
 * True when slash dates in the locale [tag] are day/month ("15/10"), false when month/day
 * ("10/15"). It is decided from the locale's own short date format, like desktop, so a locale whose
 * short date starts with the year (ja, sv, hu) counts as month/day for the part after the year. An
 * unknown tag falls back to month/day.
 */
internal expect fun isDayFirstLocale(tag: String): Boolean

/** Day before month in a short date pattern such as "dd/MM/y". */
internal fun isDayFirstPattern(pattern: String): Boolean {
    val day = pattern.indexOf('d')
    val month = pattern.indexOf('M')
    return day >= 0 && month >= 0 && day < month
}
