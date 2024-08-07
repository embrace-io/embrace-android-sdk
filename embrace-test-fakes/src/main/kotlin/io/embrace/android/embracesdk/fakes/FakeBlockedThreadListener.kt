package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.anr.BlockedThreadListener

public class FakeBlockedThreadListener : BlockedThreadListener {
    public var blockedCount: Int = 0
    public var unblockedCount: Int = 0
    public var intervalCount: Int = 0

    override fun onThreadBlocked(thread: Thread, timestamp: Long) {
        blockedCount++
    }

    override fun onThreadBlockedInterval(thread: Thread, timestamp: Long) {
        intervalCount++
    }

    override fun onThreadUnblocked(thread: Thread, timestamp: Long) {
        unblockedCount++
    }
}
