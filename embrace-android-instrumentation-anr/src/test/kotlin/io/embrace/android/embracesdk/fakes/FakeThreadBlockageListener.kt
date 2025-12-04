package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.instrumentation.anr.detection.ThreadBlockageEvent
import io.embrace.android.embracesdk.internal.instrumentation.anr.detection.ThreadBlockageListener

internal class FakeThreadBlockageListener : ThreadBlockageListener {
    var intervalCount: Int = 0

    override fun onThreadBlockageEvent(
        event: ThreadBlockageEvent,
        timestamp: Long,
    ) {
        if (event == ThreadBlockageEvent.BLOCKED_INTERVAL) {
            intervalCount++
        }
    }
}
