package io.embrace.android.embracesdk.anr

import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.payload.AnrInterval

internal class NoOpAnrService : AnrService {

    override fun getCapturedData(): List<AnrInterval> {
        return emptyList()
    }

    override fun forceAnrTrackingStopOnCrash() {
    }

    override fun finishInitialization(
        configService: ConfigService
    ) {
    }

    override fun addBlockedThreadListener(listener: BlockedThreadListener) {
    }

    override fun close() {
    }

    override fun cleanCollections() {
    }
}
