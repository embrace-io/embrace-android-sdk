package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.anr.AnrService
import io.embrace.android.embracesdk.anr.BlockedThreadListener
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.payload.AnrInterval

internal class FakeAnrService : AnrService {

    var data: List<AnrInterval> = mutableListOf()
    var forceAnrTrackingStopOnCrashCount: Int = 0

    override fun cleanCollections() {
    }

    override fun getCapturedData(): List<AnrInterval> = data

    override fun forceAnrTrackingStopOnCrash() {
        forceAnrTrackingStopOnCrashCount++
    }

    override fun finishInitialization(
        configService: ConfigService
    ) {
    }

    override fun addBlockedThreadListener(listener: BlockedThreadListener) {
    }

    override fun close() {
    }
}
