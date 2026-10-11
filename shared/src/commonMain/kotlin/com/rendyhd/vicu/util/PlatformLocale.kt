package com.rendyhd.vicu.util

/** The language tag of the device's current locale ("en-GB", "nl-NL"). */
expect fun deviceLocaleTag(): String

/**
 * What [DateDisplay] takes from the platform for a locale other than English: the order of month
 * and day in the locale's short date, the weekday and month names (weekdays from Monday) and the
 * AM/PM markers. English never comes here; its names are fixed by the contract.
 */
class LocaleDateSymbols(
    val monthFirst: Boolean,
    val weekdaysShort: List<String>,
    val weekdaysLong: List<String>,
    val monthsShort: List<String>,
    val monthsLong: List<String>,
    val am: String,
    val pm: String,
)

expect fun localeDateSymbols(localeTag: String): LocaleDateSymbols

/** The language subtag of [tag], lower case ("en" for "en-GB"). */
fun languageOf(tag: String): String = tag.split('-', '_').first().lowercase()

/** The region subtag of [tag], upper case, or "" when it has none ("GB" for "en-GB", "" for "en"). */
fun regionOf(tag: String): String =
    tag.split('-', '_').drop(1)
        .firstOrNull { (it.length == 2 && it.all(Char::isLetter)) || (it.length == 3 && it.all(Char::isDigit)) }
        ?.uppercase()
        .orEmpty()
