package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.fakes.fakeSession
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.session.SessionService

internal class FakeSessionService : SessionService {

    val startTimestamps = mutableListOf<Long>()
    val endTimestamps = mutableListOf<Long>()
    var manualEndCount = 0
    var manualStartCount = 0
    var activeSession: Session? = null

    override fun startSessionWithState(timestamp: Long, coldStart: Boolean): Session {
        startTimestamps.add(timestamp)
        activeSession = fakeSession(startMs = timestamp)
        return checkNotNull(activeSession)
    }

    override fun startSessionWithManual(timestamp: Long): Session {
        manualStartCount++
        activeSession = fakeSession()
        return checkNotNull(activeSession)
    }

    override fun endSessionWithState(initial: Session, timestamp: Long) {
        endTimestamps.add(timestamp)
        activeSession = null
    }

    var crashId: String? = null

    override fun endSessionWithCrash(initial: Session, timestamp: Long, crashId: String) {
        this.crashId = crashId
        activeSession = null
    }

    override fun endSessionWithManual(initial: Session, timestamp: Long) {
        manualEndCount++
        activeSession = null
    }
}
