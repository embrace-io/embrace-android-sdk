package io.embrace.android.embracesdk.arch.limits

import io.embrace.android.embracesdk.annotation.InternalApi

/**
 * Defines a strategy for limiting the capture of data in a [DataSource].
 */
@InternalApi
public interface LimitStrategy {

    /**
     * Whether data should be captured or not. Each invocation of this increments an internal
     * counter by one, as it assumes that data has been captured.
     */
    public fun shouldCapture(): Boolean

    /**
     * Resets the internal count of data that has been captured.
     */
    public fun resetDataCaptureLimits()
}
