package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.clock.Clock

internal class FakeClock(
    @Volatile
    private var currentTime: Long = 0
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
}
