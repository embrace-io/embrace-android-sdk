package io.embrace.android.embracesdk.internal.clock

class SystemClock : Clock {
    override fun now(): Long {
        return System.currentTimeMillis()
    }
}
