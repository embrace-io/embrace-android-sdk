package io.embrace.android.embracesdk.internal.otel.schema

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
    DEFER;

    companion object {
        fun fromString(value: String?): SendMode {
            return when (value?.lowercase()) {
                "immediate" -> IMMEDIATE
                "defer" -> DEFER
                else -> DEFAULT
            }
        }
    }
}
