package com.rendyhd.vicu.ios

import com.rendyhd.vicu.util.IosPaths
import com.rendyhd.vicu.util.TimeSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.toLocalDateTime
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString

/**
 * The last background runs (when iOS ran the refresh or the queue sync, and how it ended), shown
 * in Settings so it can be checked on a device that iOS really runs them: the simulator never
 * does. Times and outcomes only, nothing about the account or its tasks.
 */
class BackgroundActivityLog(private val time: TimeSource) {
    private val mutex = Mutex()
    private val path get() = Path("${IosPaths.applicationSupport}/background-activity.log")

    suspend fun record(line: String) = mutex.withLock {
        val stamp = time.now().toLocalDateTime(time.zone()).let { local ->
            "${local.date} ${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
        }
        val lines = (readLines() + "$stamp  $line").takeLast(MAX_LINES)
        runCatching {
            SystemFileSystem.sink(path).buffered().use { it.writeString(lines.joinToString("\n", postfix = "\n")) }
            IosPaths.excludeFromBackup(path.toString())
        }
    }

    suspend fun read(): String = mutex.withLock {
        readLines().reversed().joinToString("\n").ifEmpty { "No background runs yet. iOS runs them when it sees fit, more often for apps that are used often." }
    }

    private fun readLines(): List<String> = runCatching {
        if (!SystemFileSystem.exists(path)) return emptyList()
        SystemFileSystem.source(path).buffered().use { it.readString() }.lines().filter { it.isNotBlank() }
    }.getOrDefault(emptyList())

    private companion object {
        const val MAX_LINES = 50
    }
}
