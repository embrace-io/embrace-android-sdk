package io.embrace.android.gradle.plugin.util

import io.embrace.android.gradle.plugin.system.Clock

class TestClock : Clock {
    private var currentTime: Long = 0

    fun setCurrentTime(currentTime: Long) {
        this.currentTime = currentTime
    }

    fun tickSecond() {
        tick(1000)
    }

    @JvmOverloads
    fun tick(millis: Long = 1) {
        currentTime += millis
    }

    override fun now(): Long {
        return currentTime
    }
}
