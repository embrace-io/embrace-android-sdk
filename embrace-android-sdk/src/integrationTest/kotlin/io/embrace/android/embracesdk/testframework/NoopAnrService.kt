package io.embrace.android.embracesdk.testframework

import io.embrace.android.embracesdk.internal.instrumentation.anr.AnrService
import io.embrace.android.embracesdk.internal.instrumentation.anr.BlockedThreadListener
import io.embrace.android.embracesdk.internal.payload.Span

internal object NoopAnrService : AnrService {

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

    override fun snapshotSpans(): List<Span> = emptyList()

    override fun record() {
    }
}
