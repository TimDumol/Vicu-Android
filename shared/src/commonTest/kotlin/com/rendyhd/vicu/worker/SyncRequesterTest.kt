package com.rendyhd.vicu.worker

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class SyncRequesterTest {

    private fun TestScope.requester(
        results: MutableList<Boolean>,
        runs: MutableList<Int>,
        signedIn: Boolean = true,
        gate: CompletableDeferred<Unit>? = null,
    ) = SyncRequester(
        scope = backgroundScope,
        sync = {
            runs += runs.size + 1
            gate?.await()
            results.removeFirstOrNull() ?: true
        },
        isSignedIn = { signedIn },
        retryDelays = listOf(10.seconds, 20.seconds),
    )

    @Test
    fun `requests made during a sync fold into one follow-up run`() = runTest {
        val runs = mutableListOf<Int>()
        val gate = CompletableDeferred<Unit>()
        val sync = requester(mutableListOf(), runs, gate = gate)
        sync.request()
        runCurrent()
        repeat(5) { sync.request() }
        gate.complete(Unit)
        runCurrent()
        assertEquals(2, runs.size)
    }

    @Test
    fun `a sync that wants a retry runs again after a growing delay`() = runTest {
        val runs = mutableListOf<Int>()
        val sync = requester(mutableListOf(false, false, false, true), runs)
        sync.request()
        runCurrent()
        assertEquals(1, runs.size)
        advanceTimeBy(10.seconds.inWholeMilliseconds + 1)
        assertEquals(2, runs.size)
        advanceTimeBy(20.seconds.inWholeMilliseconds + 1)
        assertEquals(3, runs.size)
        // The last delay repeats.
        advanceTimeBy(20.seconds.inWholeMilliseconds + 1)
        assertEquals(4, runs.size)
        advanceTimeBy(60.seconds.inWholeMilliseconds)
        assertEquals(4, runs.size)
    }

    @Test
    fun `nothing runs without an account`() = runTest {
        val runs = mutableListOf<Int>()
        val sync = requester(mutableListOf(), runs, signedIn = false)
        sync.request()
        runCurrent()
        assertEquals(0, runs.size)
    }
}
