package io.embrace.android.embracesdk.internal.arch.schema

import io.embrace.android.embracesdk.internal.arch.schema.SendMode.DEFAULT
import io.embrace.android.embracesdk.internal.arch.schema.SendMode.DEFER
import io.embrace.android.embracesdk.internal.arch.schema.SendMode.IMMEDIATE

public enum class SendMode {
    DEFAULT, IMMEDIATE, DEFER
}

public fun String.toSendMode(): SendMode {
    return when (lowercase()) {
        "immediate" -> IMMEDIATE
        "defer" -> DEFER
        else -> DEFAULT
    }
}
