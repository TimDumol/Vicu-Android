package com.rendyhd.vicu.util

import platform.Foundation.NSUUID

// Lower case like java.util.UUID, so ids look the same whichever platform made them.
actual fun randomUuid(): String = NSUUID.UUID().UUIDString.lowercase()
