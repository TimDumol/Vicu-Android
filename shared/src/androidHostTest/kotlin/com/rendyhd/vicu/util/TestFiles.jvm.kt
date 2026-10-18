package com.rendyhd.vicu.util

import java.io.File
import java.util.TimeZone

actual fun readRepoFile(relativePath: String): String {
    // Gradle runs the tests with the module directory as the working directory; search
    // upward so running them from the repo root or an IDE works too.
    val start: String = System.getProperty("user.dir") ?: "."
    var dir: File? = File(start).absoluteFile
    while (dir != null) {
        val candidate = File(dir, relativePath)
        if (candidate.isFile) return candidate.readText(Charsets.UTF_8)
        dir = dir.parentFile
    }
    error("$relativePath not found above $start")
}

actual fun withSystemTimeZone(zoneId: String, block: () -> Unit): Boolean {
    val original = TimeZone.getDefault()
    try {
        TimeZone.setDefault(TimeZone.getTimeZone(zoneId))
        block()
    } finally {
        TimeZone.setDefault(original)
    }
    return true
}
