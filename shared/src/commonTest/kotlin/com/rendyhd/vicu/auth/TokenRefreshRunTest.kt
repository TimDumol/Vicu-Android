package com.rendyhd.vicu.auth

import com.rendyhd.vicu.data.remote.BaseUrlHolder
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock

class TokenRefreshRunTest {

    private suspend fun signedIn(scope: CoroutineScope, calls: Counter, answer: () -> Pair<String, HttpStatusCode>): Pair<AuthHarness, TokenRefreshRun> {
        val h = authHarness(scope) { request ->
            // Only the refresh is under test; the session's other start-up calls get nothing.
            if (request.url.encodedPath.endsWith("user/token/refresh")) {
                calls.increment()
                val (body, status) = answer()
                respondJson(body, status)
            } else {
                respondJson("{}", HttpStatusCode.NotFound)
            }
        }
        h.storage.storeVikunjaUrl("https://vikunja.test")
        h.storage.storeServerIsV2(true)
        h.storage.storeAuthMethod("password")
        // A valid session: start-up signs in without refreshing.
        h.storage.storeJwt(jwtExpiringIn(3_600), Clock.System.now().epochSeconds + 3_600)
        h.storage.storeRefreshToken("r1")
        return h to TokenRefreshRun(h.manager, h.storage, BaseUrlHolder(h.storage))
    }

    @Test
    fun nothingHappensWithoutAServer() = runTest {
        val calls = Counter()
        val h = authHarness(backgroundScope) { calls.increment(); respondJson("{}") }
        h.storage.storeRefreshToken("r1")
        val run = TokenRefreshRun(h.manager, h.storage, BaseUrlHolder(h.storage))
        assertEquals(TokenRefreshRun.Outcome.SKIPPED, run.run("test"))
        assertEquals(0, calls.value)
    }

    @Test
    fun nothingHappensWithoutARefreshToken() = runTest {
        val calls = Counter()
        val h = authHarness(backgroundScope) { calls.increment(); respondJson("{}") }
        h.storage.storeVikunjaUrl("https://vikunja.test")
        val run = TokenRefreshRun(h.manager, h.storage, BaseUrlHolder(h.storage))
        assertEquals(TokenRefreshRun.Outcome.SKIPPED, run.run("test"))
        assertEquals(0, calls.value)
    }

    @Test
    fun aSignedInSessionIsRefreshed() = runTest {
        val calls = Counter()
        val (_, run) = signedIn(backgroundScope, calls) { """{"token":"${jwtExpiringIn(3_600)}"}""" to HttpStatusCode.OK }
        assertEquals(TokenRefreshRun.Outcome.REFRESHED, run.run("test"))
        assertEquals(1, calls.value)
    }

    @Test
    fun aRefusedRefreshIsFinalAndAServerErrorIsRetried() = runTest {
        val refused = signedIn(backgroundScope, Counter()) { "{}" to HttpStatusCode.Unauthorized }.second
        assertEquals(TokenRefreshRun.Outcome.FAILED_FINAL, refused.run("test"))

        val failing = signedIn(backgroundScope, Counter()) { "{}" to HttpStatusCode.InternalServerError }.second
        assertEquals(TokenRefreshRun.Outcome.FAILED_RETRY, failing.run("test"))
    }

    @Test
    fun theBackoffAfterAFailureIsRespected() = runTest {
        val calls = Counter()
        val (_, run) = signedIn(backgroundScope, calls) { "{}" to HttpStatusCode.InternalServerError }
        assertEquals(TokenRefreshRun.Outcome.FAILED_RETRY, run.run("test"))
        assertEquals(TokenRefreshRun.Outcome.SKIPPED, run.run("test"))
        assertEquals(1, calls.value)
    }
}
