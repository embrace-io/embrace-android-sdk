package io.embrace.android.embracesdk.clock

internal class SystemClock : Clock {
    override fun now(): Long {
        return System.currentTimeMillis()
    }
}
