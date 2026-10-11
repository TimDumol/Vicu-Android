package com.rendyhd.vicu.worker

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Runs the sync when asked, for platforms without WorkManager (iOS): requests made while a sync
 * runs fold into one follow-up run, and a sync that reports a retry is due runs again after a
 * growing delay ([retryDelays], the last one repeating) for as long as the app is alive. The
 * background tasks of the platform cover the time it is not.
 */
class SyncRequester(
    private val scope: CoroutineScope,
    private val sync: suspend () -> Boolean,
    private val isSignedIn: suspend () -> Boolean,
    private val retryDelays: List<Duration> = listOf(30.seconds, 1.minutes, 2.minutes, 5.minutes, 15.minutes),
) {
    private val requests = Channel<Unit>(Channel.CONFLATED)
    private var retryJob: Job? = null
    private var failures = 0

    init {
        scope.launch {
            for (request in requests) runOnce()
        }
    }

    /** Asks for a sync; returns at once. */
    fun request() {
        requests.trySend(Unit)
    }

    private suspend fun runOnce() {
        if (!isSignedIn()) return
        val complete = runCatching { sync() }.getOrDefault(false)
        retryJob?.cancel()
        if (complete) {
            failures = 0
            return
        }
        val wait = retryDelays[minOf(failures, retryDelays.lastIndex)]
        failures++
        retryJob = scope.launch {
            delay(wait)
            request()
        }
    }
}
