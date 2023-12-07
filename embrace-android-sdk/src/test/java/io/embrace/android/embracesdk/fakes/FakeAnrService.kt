package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.anr.AnrService
import io.embrace.android.embracesdk.anr.BlockedThreadListener
import io.embrace.android.embracesdk.anr.detection.AnrProcessErrorStateInfo
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.payload.AnrInterval

internal class FakeAnrService : AnrService {

    var data: List<AnrInterval> = mutableListOf()
    var anrProcessErrors: List<AnrProcessErrorStateInfo> = mutableListOf()

    override fun cleanCollections() {
        TODO("Not yet implemented")
    }

    override fun getCapturedData(): List<AnrInterval> = data

    override fun getAnrProcessErrors(startTime: Long): List<AnrProcessErrorStateInfo> = anrProcessErrors

    override fun forceAnrTrackingStopOnCrash() {
    }

    override fun finishInitialization(
        configService: ConfigService
    ) {
        TODO("Not yet implemented")
    }

    override fun addBlockedThreadListener(listener: BlockedThreadListener) {
        TODO("Not yet implemented")
    }

    override fun close() {
        TODO("Not yet implemented")
    }
}
