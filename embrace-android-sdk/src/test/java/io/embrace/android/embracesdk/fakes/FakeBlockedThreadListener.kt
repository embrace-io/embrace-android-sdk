package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.anr.BlockedThreadListener

internal class FakeBlockedThreadListener : BlockedThreadListener {
    var blockedCount = 0
    var unblockedCount = 0
    var intervalCount = 0

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
