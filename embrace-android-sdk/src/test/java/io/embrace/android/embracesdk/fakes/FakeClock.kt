package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.clock.Clock

internal class FakeClock(
    @Volatile
    private var currentTime: Long = DEFAULT_FAKE_CURRENT_TIME
) : Clock {

    fun setCurrentTime(currentTime: Long) {
        this.currentTime = currentTime
    }

    @JvmOverloads
    fun tick(millis: Long = 1) {
        currentTime += millis
    }

    fun tickSecond() = tick(1000)

    override fun now(): Long = currentTime

    companion object {
        const val DEFAULT_FAKE_CURRENT_TIME = 1692201601000L
    }
}
