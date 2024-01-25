package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.fakes.fakeSession
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.session.SessionService

internal class FakeSessionService : SessionService {

    val startTimestamps = mutableListOf<Long>()
    val endTimestamps = mutableListOf<Long>()
    var manualEndCount = 0
    var manualStartCount = 0

    override var activeSession: Session? = null

    override fun startSessionWithState(coldStart: Boolean, timestamp: Long): String {
        startTimestamps.add(timestamp)
        activeSession = fakeSession(startMs = timestamp)
        return activeSession?.sessionId ?: ""
    }

    override fun startSessionWithManual(): String {
        manualStartCount++
        activeSession = fakeSession()
        return activeSession?.sessionId ?: ""
    }

    override fun endSessionWithState(timestamp: Long) {
        endTimestamps.add(timestamp)
        activeSession = null
    }

    var crashId: String? = null

    override fun endSessionWithCrash(crashId: String) {
        this.crashId = crashId
        activeSession = null
    }

    override fun endSessionWithManual() {
        manualEndCount++
        activeSession = null
    }
}
