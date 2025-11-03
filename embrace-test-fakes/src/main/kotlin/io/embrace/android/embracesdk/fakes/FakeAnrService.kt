package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.instrumentation.anr.AnrService
import io.embrace.android.embracesdk.internal.instrumentation.anr.BlockedThreadListener
import io.embrace.android.embracesdk.internal.instrumentation.anr.payload.AnrInterval

class FakeAnrService : AnrService {

    var data: List<AnrInterval> = mutableListOf()

    override fun cleanCollections() {
    }

    override fun getCapturedData(): List<AnrInterval> = data

    override fun startAnrCapture() {
    }

    override fun addBlockedThreadListener(listener: BlockedThreadListener) {
    }
}
