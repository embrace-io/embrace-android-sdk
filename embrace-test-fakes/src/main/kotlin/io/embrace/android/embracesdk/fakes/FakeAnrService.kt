package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.anr.AnrService
import io.embrace.android.embracesdk.internal.anr.BlockedThreadListener
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.payload.AnrInterval

public class FakeAnrService : AnrService {

    public var data: List<AnrInterval> = mutableListOf()
    public var forceAnrTrackingStopOnCrashCount: Int = 0

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
