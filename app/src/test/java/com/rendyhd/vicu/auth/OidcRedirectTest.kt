package com.rendyhd.vicu.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OidcRedirectTest {
    private val callback = "https://tasks.example/base/auth/openid/provider"

    @Test
    fun acceptsOnlyTheCallbackOriginAndPath() {
        assertTrue(OidcRedirect.matches("$callback?code=abc&state=xyz", callback))
        assertTrue(OidcRedirect.matches("https://tasks.example:443/base/auth/openid/provider?code=x", callback))
        for (url in listOf(
            "$callback/extra?code=x",
            "${callback}evil?code=x",
            "https://tasks.example.evil/base/auth/openid/provider?code=x",
            "https://tasks.example:8443/base/auth/openid/provider?code=x",
            "http://tasks.example/base/auth/openid/provider?code=x",
            "https://attacker@tasks.example/base/auth/openid/provider?code=x",
            "$callback?code=x#fragment",
            "not a URL",
        )) assertFalse(url, OidcRedirect.matches(url, callback))
    }
}
