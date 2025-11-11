package io.embrace.android.embracesdk.testframework

import io.embrace.android.embracesdk.internal.instrumentation.anr.AnrService
import io.embrace.android.embracesdk.internal.instrumentation.anr.BlockedThreadListener
import io.embrace.android.embracesdk.internal.instrumentation.anr.payload.AnrInterval

internal object NoopAnrService : AnrService {

    override fun getCapturedData(): List<AnrInterval> = emptyList()

    override fun startAnrCapture() {
    }

    override fun addBlockedThreadListener(listener: BlockedThreadListener) {
    }

    override fun cleanCollections() {
    }

    override fun handleCrash(crashId: String) {
    }

    override fun onBackground() {
    }

    override fun onForeground() {
    }

    override fun onThreadBlocked(thread: Thread, timestamp: Long) {
    }

    override fun onThreadBlockedInterval(thread: Thread, timestamp: Long) {
    }

    override fun onThreadUnblocked(thread: Thread, timestamp: Long) {
    }
}
