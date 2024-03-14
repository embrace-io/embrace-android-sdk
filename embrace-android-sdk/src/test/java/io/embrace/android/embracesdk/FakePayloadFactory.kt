package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.fakes.fakeSession
import io.embrace.android.embracesdk.fakes.fakeV1SessionMessage
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.session.lifecycle.ProcessState
import io.embrace.android.embracesdk.session.message.PayloadFactory

internal class FakePayloadFactory : PayloadFactory {

    val startSessionTimestamps = mutableListOf<Long>()
    val endSessionTimestamps = mutableListOf<Long>()
    var manualSessionEndCount = 0
    var manualSessionStartCount = 0
    var snapshotSessionCount = 0
    private var activeSession: Session? = null
    val endBaTimestamps = mutableListOf<Long>()
    val startBaTimestamps = mutableListOf<Long>()
    var baCrashId: String? = null
    private var snapshotBaCount: Int = 0

    override fun startPayloadWithState(
        state: ProcessState,
        timestamp: Long,
        coldStart: Boolean
    ): Session {
        return when (state) {
            ProcessState.FOREGROUND -> startSessionWithState(timestamp)
            ProcessState.BACKGROUND -> startBackgroundActivityWithState(timestamp)
        }
    }

    override fun endPayloadWithState(
        state: ProcessState,
        timestamp: Long,
        initial: Session
    ): SessionMessage {
        return when (state) {
            ProcessState.FOREGROUND -> endSessionWithState(timestamp)
            ProcessState.BACKGROUND -> endBackgroundActivityWithState(timestamp)
        }
    }

    override fun endPayloadWithCrash(
        state: ProcessState,
        timestamp: Long,
        initial: Session,
        crashId: String
    ): SessionMessage {
        return when (state) {
            ProcessState.FOREGROUND -> endSessionWithCrash(crashId)
            ProcessState.BACKGROUND -> endBackgroundActivityWithCrash(crashId)
        }
    }

    override fun snapshotPayload(
        state: ProcessState,
        timestamp: Long,
        initial: Session
    ): SessionMessage? {
        return when (state) {
            ProcessState.FOREGROUND -> snapshotSession()
            ProcessState.BACKGROUND -> snapshotBackgroundActivity()
        }
    }

    private fun startBackgroundActivityWithState(timestamp: Long): Session {
        startBaTimestamps.add(timestamp)
        return fakeBackgroundActivity()
    }

    private fun endBackgroundActivityWithState(timestamp: Long): SessionMessage {
        endBaTimestamps.add(timestamp)
        return fakeBackgroundActivityMessage()
    }

    private fun endBackgroundActivityWithCrash(
        crashId: String
    ): SessionMessage {
        this.baCrashId = crashId
        return fakeBackgroundActivityMessage()
    }

    private fun snapshotBackgroundActivity(): SessionMessage {
        snapshotBaCount++
        return fakeBackgroundActivityMessage()
    }

    private fun startSessionWithState(timestamp: Long): Session {
        startSessionTimestamps.add(timestamp)
        activeSession = fakeSession(startMs = timestamp)
        return checkNotNull(activeSession)
    }

    override fun startSessionWithManual(timestamp: Long): Session {
        manualSessionStartCount++
        activeSession = fakeSession()
        return checkNotNull(activeSession)
    }

    private fun endSessionWithState(timestamp: Long): SessionMessage {
        endSessionTimestamps.add(timestamp)
        activeSession = null
        return fakeV1SessionMessage()
    }

    var crashId: String? = null

    private fun endSessionWithCrash(crashId: String): SessionMessage {
        this.crashId = crashId
        activeSession = null
        return fakeV1SessionMessage()
    }

    override fun endSessionWithManual(timestamp: Long, initial: Session): SessionMessage {
        manualSessionEndCount++
        activeSession = null
        return fakeV1SessionMessage()
    }

    private fun snapshotSession(): SessionMessage? {
        snapshotSessionCount++
        return null
    }
}
