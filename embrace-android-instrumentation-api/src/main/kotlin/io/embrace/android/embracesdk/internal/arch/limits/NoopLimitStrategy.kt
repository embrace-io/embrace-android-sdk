package io.embrace.android.embracesdk.internal.arch.limits

/**
 * This class captures whatever the hell it wants, whenever it wants
 */
object NoopLimitStrategy : LimitStrategy {

    override fun shouldCapture(): Boolean = true

    override fun resetDataCaptureLimits() {
    }
}
