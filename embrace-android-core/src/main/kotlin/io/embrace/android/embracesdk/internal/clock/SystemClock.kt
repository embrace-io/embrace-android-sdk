package io.embrace.android.embracesdk.internal.clock

public class SystemClock : Clock {
    override fun now(): Long {
        return System.currentTimeMillis()
    }
}
