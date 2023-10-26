package io.embrace.android.embracesdk.internal.clock

import io.embrace.android.embracesdk.InternalApi

@InternalApi
public fun interface Clock {

    /**
     * Returns the current milliseconds from epoch.
     */
    public fun now(): Long
}
