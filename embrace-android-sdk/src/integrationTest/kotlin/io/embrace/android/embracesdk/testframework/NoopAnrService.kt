package io.embrace.android.embracesdk.testframework

import io.embrace.android.embracesdk.internal.instrumentation.anr.AnrService
import io.embrace.android.embracesdk.internal.payload.Span

internal object NoopAnrService : AnrService {

    override fun startAnrCapture() {
    }

    override fun cleanCollections() {
    }

    override fun handleCrash(crashId: String) {
    }

    override fun onBackground() {
    }

    override fun onForeground() {
    }

    override fun snapshotSpans(): List<Span> = emptyList()

    override fun record() {
    }
}
