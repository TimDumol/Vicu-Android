package com.rendyhd.vicu.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RefreshCookieExtractorTest {

    @Test
    fun `one Set-Cookie header per cookie`() {
        assertEquals(
            "abc",
            RefreshCookieExtractor.extractRefreshToken(
                listOf("other=1; Path=/", "vikunja_refresh_token=abc; Path=/api/v2/user/token/refresh; HttpOnly"),
            ),
        )
    }

    @Test
    fun `Set-Cookie headers folded into one value as the Darwin engine gives them`() {
        val folded = "session=x; Expires=Wed, 21 Oct 2026 07:28:00 GMT; Path=/, " +
            "vikunja_refresh_token=r3fr35h; Path=/api/v2/user/token/refresh; HttpOnly; SameSite=Strict"
        assertEquals("r3fr35h", RefreshCookieExtractor.extractRefreshToken(listOf(folded)))
    }

    @Test
    fun `a cookie whose name only ends with the refresh cookie name is not it`() {
        assertNull(RefreshCookieExtractor.extractRefreshToken(listOf("old_vikunja_refresh_token=nope; Path=/")))
    }

    @Test
    fun `an empty refresh cookie (a sign-out) is no token`() {
        assertNull(RefreshCookieExtractor.extractRefreshToken(listOf("vikunja_refresh_token=; Max-Age=0")))
    }
}
