package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.anr.BlockedThreadListener

class FakeBlockedThreadListener : BlockedThreadListener {
    var blockedCount: Int = 0
    var unblockedCount: Int = 0
    var intervalCount: Int = 0

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
