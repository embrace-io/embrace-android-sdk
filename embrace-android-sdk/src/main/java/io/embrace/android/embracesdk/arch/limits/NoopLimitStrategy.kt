package io.embrace.android.embracesdk.arch.limits

/**
 * This class captures whatever the hell it wants, whenever it wants
 */
internal object NoopLimitStrategy : LimitStrategy {

    override fun shouldCapture(): Boolean = true

    override fun resetDataCaptureLimits() {
    }
}
