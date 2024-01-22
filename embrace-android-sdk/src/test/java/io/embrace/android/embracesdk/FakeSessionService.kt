package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.session.SessionService

internal class FakeSessionService : SessionService {

    val startTimestamps = mutableListOf<Long>()
    val endTimestamps = mutableListOf<Long>()
    var manualEndCount = 0

    override fun startSessionWithState(coldStart: Boolean, timestamp: Long) {
        startTimestamps.add(timestamp)
    }

    override fun endSessionWithState(timestamp: Long) {
        endTimestamps.add(timestamp)
    }

    var crashId: String? = null

    override fun endSessionWithCrash(crashId: String) {
        this.crashId = crashId
    }

    override fun endSessionWithManual(clearUserInfo: Boolean) {
        manualEndCount++
    }
}
