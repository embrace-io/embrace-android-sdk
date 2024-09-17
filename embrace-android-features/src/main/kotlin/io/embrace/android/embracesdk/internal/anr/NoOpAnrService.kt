package io.embrace.android.embracesdk.internal.anr

import io.embrace.android.embracesdk.internal.payload.AnrInterval

internal class NoOpAnrService : AnrService {

    override fun getCapturedData(): List<AnrInterval> {
        return emptyList()
    }

    override fun handleCrash(crashId: String) {
    }

    override fun startAnrCapture() {
    }

    override fun addBlockedThreadListener(listener: BlockedThreadListener) {
    }

    override fun close() {
    }

    override fun cleanCollections() {
    }
}
