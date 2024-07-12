package io.embrace.android.embracesdk.arch.limits

import io.embrace.android.embracesdk.annotation.InternalApi

/**
 * This class captures whatever the hell it wants, whenever it wants
 */
@InternalApi
public object NoopLimitStrategy : LimitStrategy {

    override fun shouldCapture(): Boolean = true

    override fun resetDataCaptureLimits() {
    }
}
