package com.rendyhd.vicu.util

/** The text of [relativePath] in this repository (for example `test-fixtures/nlp-corpus-v1.json`). */
expect fun readRepoFile(relativePath: String): String

/**
 * Runs [block] with [zoneId] as the process's default time zone, then puts the old one back.
 * False when the platform cannot change it (the block does not run then).
 */
expect fun withSystemTimeZone(zoneId: String, block: () -> Unit): Boolean
