package com.rendyhd.vicu.auth

import com.rendyhd.vicu.util.BuildInfo
import com.rendyhd.vicu.util.IosPaths
import com.rendyhd.vicu.util.ioDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import platform.Foundation.NSLog
import platform.Foundation.NSThread
import kotlin.concurrent.Volatile
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * On-device diagnostics for the auth flow and the background runs, as on Android: debug builds
 * only, a file in Application Support written by one coroutine so callers never wait, trimmed by
 * [AuthDebugLogRetention]. Release builds write nothing and remove a file left behind. Call
 * [init] after [BuildInfo.configure] (IosApp.start does).
 */
actual object AuthDebugLog {
    private const val FILE_NAME = "auth_debug.log"
    private const val QUEUE_CAPACITY = 512

    private sealed interface Op {
        data class Append(val line: String) : Op
        data object Clear : Op
    }

    @Volatile
    private var enabled = false

    @Volatile
    private var initialized = false

    private val queue = Channel<Op>(capacity = QUEUE_CAPACITY, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private var lineCount = -1

    private val path: Path get() = Path("${IosPaths.applicationSupport}/$FILE_NAME")

    actual fun init(context: Any?) {
        if (initialized) return
        initialized = true
        if (!BuildInfo.isDebug) {
            scope.launch { runCatching { SystemFileSystem.delete(path, mustExist = false) } }
            return
        }
        enabled = true
        scope.launch {
            IosPaths.excludeFromBackup(IosPaths.applicationSupport)
            for (op in queue) {
                runCatching {
                    when (op) {
                        is Op.Append -> append(op.line)
                        Op.Clear -> {
                            SystemFileSystem.sink(path).buffered().use { it.writeString("") }
                            lineCount = 0
                        }
                    }
                }
            }
        }
    }

    actual fun log(event: String) = record(event)

    actual fun log(event: String, detail: String) = record("$event | $detail")

    actual fun logError(event: String, error: Throwable) {
        if (!enabled) return
        record("ERROR $event | ${error::class.simpleName}: ${error.message}")
    }

    actual fun readLog(): String {
        if (!BuildInfo.isDebug) return "(auth debug log is disabled in release builds)"
        return runCatching {
            if (SystemFileSystem.exists(path)) SystemFileSystem.source(path).buffered().use { it.readString() } else "(no log entries yet)"
        }.getOrElse { "(error reading log: ${it.message})" }
    }

    actual fun clear() {
        if (enabled) queue.trySend(Op.Clear)
    }

    actual fun tokenState(jwt: Boolean, jwtExpired: Boolean, apiToken: Boolean, refreshToken: Boolean, isV2: Boolean) {
        log("TOKEN_STATE", "jwt=$jwt jwtExpired=$jwtExpired apiToken=$apiToken refreshToken=$refreshToken isV2=$isV2")
    }

    actual fun authStateChanged(old: String, new: String, reason: String) {
        log("AUTH_STATE_CHANGE", "$old → $new ($reason)")
    }

    actual fun jwtExpiry(expiryEpoch: Long) {
        if (!enabled) return
        val remaining = expiryEpoch - Clock.System.now().epochSeconds
        log("JWT_EXPIRY", "expires=${format(Instant.fromEpochSeconds(expiryEpoch))} remaining=${remaining}s (${remaining / 60}min)")
    }

    actual fun refreshAttempt(method: String) {
        log("REFRESH_ATTEMPT", method)
    }

    actual fun refreshResult(method: String, success: Boolean, detail: String) {
        log("REFRESH_RESULT", "$method success=$success $detail".trim())
    }

    actual fun lifecycle(event: String) {
        log("LIFECYCLE", event)
    }

    actual fun interceptor(event: String, path: String) {
        log("INTERCEPTOR", "$event path=$path")
    }

    private fun record(message: String) {
        if (!enabled) return
        val thread = if (NSThread.isMainThread) "main" else "bg"
        val line = "${format(Clock.System.now())} [$thread] $message"
        // The line is the format string (a Kotlin string cannot be an NSLog vararg).
        NSLog("[AuthDebugLog] $line".replace("%", "%%"))
        queue.trySend(Op.Append(line))
    }

    private fun format(instant: Instant): String {
        val t = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        fun two(n: Int) = n.toString().padStart(2, '0')
        return "${two(t.month.ordinal + 1)}-${two(t.day)} ${two(t.hour)}:${two(t.minute)}:${two(t.second)}." +
            (t.nanosecond / 1_000_000).toString().padStart(3, '0')
    }

    private fun append(line: String) {
        if (lineCount < 0) {
            lineCount = if (SystemFileSystem.exists(path)) readLog().lines().count { it.isNotEmpty() } else 0
        }
        SystemFileSystem.sink(path, append = true).buffered().use { it.writeString(line + "\n") }
        lineCount++
        if (AuthDebugLogRetention.shouldTrim(lineCount)) {
            val kept = AuthDebugLogRetention.trim(readLog().lines().filter { it.isNotEmpty() })
            SystemFileSystem.sink(path).buffered().use { it.writeString(kept.joinToString("\n", postfix = "\n")) }
            lineCount = kept.size
        }
    }
}
