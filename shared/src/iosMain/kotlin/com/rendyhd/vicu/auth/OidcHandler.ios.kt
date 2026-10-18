package com.rendyhd.vicu.auth

import com.rendyhd.vicu.util.randomUuid

private const val HEX = "0123456789ABCDEF"
private const val UNRESERVED_MARKS = "_-!.~'()*"

/** Percent-encodes [value] like Android's `Uri.encode`: letters, digits and `_-!.~'()*` stay as they are. */
actual fun encodeUrl(value: String): String = buildString {
    for (byte in value.encodeToByteArray()) {
        val b = byte.toInt() and 0xFF
        val c = b.toChar()
        if (b < 0x80 && (c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c in UNRESERVED_MARKS)) {
            append(c)
        } else {
            append('%').append(HEX[b shr 4]).append(HEX[b and 0x0F])
        }
    }
}

actual fun generateStateValue(): String = randomUuid()
