package io.embrace.android.embracesdk.arch.limits

internal class OnOffLimitStrategy(
    private val onOffProvider: () -> Boolean
) : LimitStrategy {
    override fun shouldCapture(): Boolean = onOffProvider()

    override fun resetDataCaptureLimits() {
    }
}
