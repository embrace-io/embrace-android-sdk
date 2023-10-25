package io.embrace.android.embracesdk.clock

import io.embrace.android.embracesdk.InternalApi

@InternalApi
public class SystemClock : Clock {
    override fun now(): Long {
        return System.currentTimeMillis()
    }
}
