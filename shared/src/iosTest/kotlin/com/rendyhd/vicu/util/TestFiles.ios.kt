package com.rendyhd.vicu.util

import kotlinx.cinterop.toKString
import platform.Foundation.NSString
import platform.Foundation.NSTimeZone
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.resetSystemTimeZone
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.timeZoneWithName
import platform.posix.getenv
import platform.posix.setenv
import platform.posix.unsetenv

// The simulator runs the test binary on this Mac, so the repository is readable at the path
// Gradle wrote into TestPaths.REPO_ROOT.
actual fun readRepoFile(relativePath: String): String =
    NSString.stringWithContentsOfFile("${TestPaths.REPO_ROOT}/$relativePath", NSUTF8StringEncoding, null)
        ?: error("$relativePath not found under ${TestPaths.REPO_ROOT}")

// kotlinx-datetime reads NSTimeZone.systemTimeZone (after clearing Foundation's cache), which
// follows the TZ environment variable.
actual fun withSystemTimeZone(zoneId: String, block: () -> Unit): Boolean {
    if (NSTimeZone.timeZoneWithName(zoneId) == null) return false
    val original = getenv("TZ")?.toKString()
    try {
        setenv("TZ", zoneId, 1)
        NSTimeZone.resetSystemTimeZone()
        block()
    } finally {
        if (original != null) setenv("TZ", original, 1) else unsetenv("TZ")
        NSTimeZone.resetSystemTimeZone()
    }
    return true
}
