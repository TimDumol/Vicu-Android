package com.rendyhd.vicu.auth

import java.net.URI

/** Match the registered callback itself, never a textual prefix or a subframe. */
internal object OidcRedirect {
    fun matches(candidate: String, redirect: String): Boolean = try {
        val actual = URI(candidate)
        val expected = URI(redirect)
        fun port(uri: URI): Int = when {
            uri.port != -1 -> uri.port
            uri.scheme.equals("https", ignoreCase = true) -> 443
            else -> 80
        }
        expected.host != null &&
            (expected.scheme.equals("https", true) || expected.scheme.equals("http", true)) &&
            actual.scheme.equals(expected.scheme, true) &&
            actual.host.equals(expected.host, true) &&
            port(actual) == port(expected) &&
            actual.rawUserInfo == null && actual.rawFragment == null &&
            actual.rawPath == expected.rawPath
    } catch (_: Exception) {
        false
    }
}
