package com.rendyhd.vicu.util

import kotlin.io.encoding.Base64

actual object Base64Decoder {
    // Like java.util.Base64.getUrlDecoder on Android: URL-safe alphabet, padding optional.
    private val urlSafe = Base64.UrlSafe.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL)

    actual fun decodeUrlSafe(src: String): ByteArray = urlSafe.decode(src)
}
