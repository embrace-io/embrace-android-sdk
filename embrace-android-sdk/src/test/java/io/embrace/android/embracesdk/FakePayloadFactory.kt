package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.fakes.fakeSessionMessage
import io.embrace.android.embracesdk.fakes.fakeSessionZygote
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.payload.SessionZygote
import io.embrace.android.embracesdk.session.lifecycle.ProcessState
import io.embrace.android.embracesdk.session.message.PayloadFactory

internal class FakePayloadFactory : PayloadFactory {

    val startSessionTimestamps = mutableListOf<Long>()
    val endSessionTimestamps = mutableListOf<Long>()
    var manualSessionEndCount = 0
    var manualSessionStartCount = 0
    var snapshotSessionCount = 0
    private var activeSession: SessionZygote? = null
    val endBaTimestamps = mutableListOf<Long>()
    val startBaTimestamps = mutableListOf<Long>()
    var baCrashId: String? = null
    private var snapshotBaCount: Int = 0

    override fun startPayloadWithState(
        state: ProcessState,
        timestamp: Long,
        coldStart: Boolean
    ): SessionZygote {
        return when (state) {
            ProcessState.FOREGROUND -> startSessionWithState(timestamp)
            ProcessState.BACKGROUND -> startBackgroundActivityWithState(timestamp)
        }
    }

    override fun endPayloadWithState(
        state: ProcessState,
        timestamp: Long,
        initial: SessionZygote
    ): SessionMessage {
        return when (state) {
            ProcessState.FOREGROUND -> endSessionWithState(timestamp)
            ProcessState.BACKGROUND -> endBackgroundActivityWithState(timestamp)
        }
    }

    override fun endPayloadWithCrash(
        state: ProcessState,
        timestamp: Long,
        initial: SessionZygote,
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
        initial: SessionZygote
    ): SessionMessage? {
        return when (state) {
            ProcessState.FOREGROUND -> snapshotSession()
            ProcessState.BACKGROUND -> snapshotBackgroundActivity()
        }
    }

    private fun startBackgroundActivityWithState(timestamp: Long): SessionZygote {
        startBaTimestamps.add(timestamp)
        return fakeSessionZygote().copy(appState = Session.APPLICATION_STATE_BACKGROUND)
    }

    private fun endBackgroundActivityWithState(timestamp: Long): SessionMessage {
        endBaTimestamps.add(timestamp)
        return fakeSessionMessage()
    }

    private fun endBackgroundActivityWithCrash(
        crashId: String
    ): SessionMessage {
        this.baCrashId = crashId
        return fakeSessionMessage()
    }

    private fun snapshotBackgroundActivity(): SessionMessage {
        snapshotBaCount++
        return fakeSessionMessage()
    }

    private fun startSessionWithState(timestamp: Long): SessionZygote {
        startSessionTimestamps.add(timestamp)
        activeSession = fakeSessionZygote().copy(startTime = timestamp)
        return checkNotNull(activeSession)
    }

    override fun startSessionWithManual(timestamp: Long): SessionZygote {
        manualSessionStartCount++
        activeSession = fakeSessionZygote()
        return checkNotNull(activeSession)
    }

    private fun endSessionWithState(timestamp: Long): SessionMessage {
        endSessionTimestamps.add(timestamp)
        activeSession = null
        return fakeSessionMessage()
    }

    var crashId: String? = null

    private fun endSessionWithCrash(crashId: String): SessionMessage {
        this.crashId = crashId
        activeSession = null
        return fakeSessionMessage()
    }

    override fun endSessionWithManual(timestamp: Long, initial: SessionZygote): SessionMessage {
        manualSessionEndCount++
        activeSession = null
        return fakeSessionMessage()
    }

    private fun snapshotSession(): SessionMessage? {
        snapshotSessionCount++
        return null
    }
}
