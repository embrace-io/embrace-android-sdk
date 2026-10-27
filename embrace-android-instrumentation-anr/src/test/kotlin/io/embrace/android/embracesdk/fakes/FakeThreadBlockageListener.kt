package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.instrumentation.thread.blockage.ThreadBlockageEvent
import io.embrace.android.embracesdk.internal.instrumentation.thread.blockage.ThreadBlockageListener

internal class FakeThreadBlockageListener : ThreadBlockageListener {
    var intervalCount: Int = 0

    /**
     * When set, thrown when the thread is reported as unblocked.
     */
    var unblockedFailure: Throwable? = null

    override fun onThreadBlockageEvent(
        event: ThreadBlockageEvent,
        timestamp: Long,
    ) {
        if (event == ThreadBlockageEvent.UNBLOCKED) {
            unblockedFailure?.let { throw it }
        }
        if (event == ThreadBlockageEvent.BLOCKED_INTERVAL) {
            intervalCount++
        }
    }
}
