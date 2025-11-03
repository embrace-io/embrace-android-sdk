package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.instrumentation.anr.AnrService
import io.embrace.android.embracesdk.internal.instrumentation.anr.BlockedThreadListener
import io.embrace.android.embracesdk.internal.instrumentation.anr.payload.AnrInterval

class FakeAnrService : AnrService {

    var data: List<AnrInterval> = mutableListOf()
    var crashCount: Int = 0

    override fun cleanCollections() {
    }

    override fun getCapturedData(): List<AnrInterval> = data

    override fun handleCrash(crashId: String) {
        crashCount++
    }

    override fun startAnrCapture() {
    }

    override fun addBlockedThreadListener(listener: BlockedThreadListener) {
    }

    override fun close() {
    }
}
