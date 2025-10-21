package io.embrace.android.embracesdk.internal.arch.limits

/**
 * Defines a strategy for limiting the capture of data in a [DataSource].
 */
interface LimitStrategy {

    /**
     * Whether data should be captured or not. Each invocation of this increments an internal
     * counter by one, as it assumes that data has been captured.
     */
    fun shouldCapture(): Boolean

    /**
     * Resets the internal count of data that has been captured.
     */
    fun resetDataCaptureLimits()
}
