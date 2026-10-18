package com.rendyhd.vicu.util

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold

interface NetworkMonitor {
    val isOnline: StateFlow<Boolean>
}

/**
 * Emits once each time the device goes from offline to online, not for the state it starts in.
 * A sync that failed while offline waits out its backoff even when the network is back; this is
 * the moment to start it again.
 */
fun Flow<Boolean>.reconnections(): Flow<Unit> =
    runningFold(Pair<Boolean?, Boolean?>(null, null)) { (_, last), online -> last to online }
        .filter { (before, now) -> before == false && now == true }
        .map { }
