package io.embrace.android.embracesdk.internal.arch.schema

import io.embrace.android.embracesdk.internal.arch.schema.SendMode.DEFAULT
import io.embrace.android.embracesdk.internal.arch.schema.SendMode.DEFER
import io.embrace.android.embracesdk.internal.arch.schema.SendMode.IMMEDIATE

/**
 * How a given payload should be delivered to the Embrace server
 */
enum class SendMode {
    /**
     * Use the default delivery semantics - no customization required
     */
    DEFAULT,

    /**
     * If supported for that signal/payload type, deliver this as soon as possible and do not batch
     */
    IMMEDIATE,

    /**
     * Queue for delivery at the next convenient time. Used when the delivery environment is unstable, e.g. when an app is about to crash.
     */
    DEFER
}

fun String.toSendMode(): SendMode {
    return when (lowercase()) {
        "immediate" -> IMMEDIATE
        "defer" -> DEFER
        else -> DEFAULT
    }
}
