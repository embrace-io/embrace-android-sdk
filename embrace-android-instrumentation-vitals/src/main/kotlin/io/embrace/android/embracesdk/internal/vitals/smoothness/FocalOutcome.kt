package io.embrace.android.embracesdk.internal.vitals.smoothness

/**
 * How a smoothness focal moment ended.
 */
internal enum class FocalOutcome {
    /**
     * The screen settled: no redraw and no touch for the idle threshold.
     */
    SETTLED,

    /**
     * The focal moment was cut short before settling (app backgrounded / screen changed).
     */
    INTERRUPTED,
}
