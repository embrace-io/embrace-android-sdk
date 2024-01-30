package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.fakes.fakeSession
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.session.message.PayloadFactory

internal class FakePayloadFactory : PayloadFactory {

    val startSessionTimestamps = mutableListOf<Long>()
    val endSessionTimestamps = mutableListOf<Long>()
    var manualSessionEndCount = 0
    var manualSessionStartCount = 0
    var snapshotSessionCount = 0
    var activeSession: Session? = null
    val endBaTimestamps = mutableListOf<Long>()
    val startBaTimestamps = mutableListOf<Long>()
    var baCrashId: String? = null
    var snapshotBaCount: Int = 0

    override fun startBackgroundActivityWithState(timestamp: Long, coldStart: Boolean): Session {
        startBaTimestamps.add(timestamp)
        return fakeBackgroundActivity()
    }

    override fun endBackgroundActivityWithState(initial: Session, timestamp: Long) {
        endBaTimestamps.add(timestamp)
    }

    override fun endBackgroundActivityWithCrash(initial: Session, timestamp: Long, crashId: String) {
        this.baCrashId = crashId
    }

    override fun snapshotBackgroundActivity(initial: Session, timestamp: Long): SessionMessage {
        snapshotBaCount++
        return fakeBackgroundActivityMessage()
    }

    override fun startSessionWithState(timestamp: Long, coldStart: Boolean): Session {
        startSessionTimestamps.add(timestamp)
        activeSession = fakeSession(startMs = timestamp)
        return checkNotNull(activeSession)
    }

    override fun startSessionWithManual(timestamp: Long): Session {
        manualSessionStartCount++
        activeSession = fakeSession()
        return checkNotNull(activeSession)
    }

    override fun endSessionWithState(initial: Session, timestamp: Long) {
        endSessionTimestamps.add(timestamp)
        activeSession = null
    }

    var crashId: String? = null

    override fun endSessionWithCrash(initial: Session, timestamp: Long, crashId: String) {
        this.crashId = crashId
        activeSession = null
    }

    override fun endSessionWithManual(initial: Session, timestamp: Long) {
        manualSessionEndCount++
        activeSession = null
    }

    override fun snapshotSession(initial: Session, timestamp: Long): SessionMessage? {
        snapshotSessionCount++
        return null
    }
}
