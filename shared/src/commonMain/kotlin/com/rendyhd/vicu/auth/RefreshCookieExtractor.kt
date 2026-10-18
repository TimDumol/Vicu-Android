package com.rendyhd.vicu.auth

import com.rendyhd.vicu.data.remote.api.KtorResponse

object RefreshCookieExtractor {
    private const val COOKIE_NAME = "vikunja_refresh_token"

    // The cookie at the start of a Set-Cookie value, or after the comma that joins two of them:
    // the Darwin engine (NSHTTPURLResponse) folds several Set-Cookie headers into one value.
    private val cookieRegex = Regex("""(?:^|,\s*)$COOKIE_NAME=([^;,\s]*)""")

    fun extractRefreshToken(response: KtorResponse<*>): String? =
        extractRefreshToken(
            response.headers.entries
                .filter { it.key.equals("Set-Cookie", ignoreCase = true) }
                .flatMap { it.value },
        )

    /** The refresh token in [setCookieValues] (the values of the Set-Cookie headers), if any. */
    fun extractRefreshToken(setCookieValues: List<String>): String? =
        setCookieValues.firstNotNullOfOrNull { header ->
            cookieRegex.findAll(header).map { it.groupValues[1] }.firstOrNull { it.isNotBlank() }
        }

    fun buildCookieHeader(refreshToken: String): String {
        return "$COOKIE_NAME=$refreshToken"
    }
}
