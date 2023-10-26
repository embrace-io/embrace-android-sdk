package io.embrace.android.embracesdk.internal.clock

internal class SystemClock : Clock {
    override fun now(): Long {
        return System.currentTimeMillis()
    }
}
