package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.payload.ApplicationState
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.payload.SessionZygote
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessState
import io.embrace.android.embracesdk.internal.session.message.PayloadFactory

public class FakePayloadFactory : PayloadFactory {

    public val startSessionTimestamps: MutableList<Long> = mutableListOf<Long>()
    public val endSessionTimestamps: MutableList<Long> = mutableListOf<Long>()
    public var manualSessionEndCount: Int = 0
    public var manualSessionStartCount: Int = 0
    public var snapshotSessionCount: Int = 0
    private var activeSession: SessionZygote? = null
    public val endBaTimestamps: MutableList<Long> = mutableListOf<Long>()
    public val startBaTimestamps: MutableList<Long> = mutableListOf<Long>()
    public var baCrashId: String? = null
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
    ): Envelope<SessionPayload> {
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
    ): Envelope<SessionPayload> {
        return when (state) {
            ProcessState.FOREGROUND -> endSessionWithCrash(crashId)
            ProcessState.BACKGROUND -> endBackgroundActivityWithCrash(crashId)
        }
    }

    override fun snapshotPayload(
        state: ProcessState,
        timestamp: Long,
        initial: SessionZygote
    ): Envelope<SessionPayload>? {
        return when (state) {
            ProcessState.FOREGROUND -> snapshotSession()
            ProcessState.BACKGROUND -> snapshotBackgroundActivity()
        }
    }

    private fun startBackgroundActivityWithState(timestamp: Long): SessionZygote {
        startBaTimestamps.add(timestamp)
        return fakeSessionZygote().copy(appState = ApplicationState.BACKGROUND)
    }

    private fun endBackgroundActivityWithState(timestamp: Long): Envelope<SessionPayload> {
        endBaTimestamps.add(timestamp)
        return fakeSessionEnvelope()
    }

    private fun endBackgroundActivityWithCrash(
        crashId: String
    ): Envelope<SessionPayload> {
        this.baCrashId = crashId
        return fakeSessionEnvelope()
    }

    private fun snapshotBackgroundActivity(): Envelope<SessionPayload> {
        snapshotBaCount++
        return fakeSessionEnvelope()
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

    private fun endSessionWithState(timestamp: Long): Envelope<SessionPayload> {
        endSessionTimestamps.add(timestamp)
        activeSession = null
        return fakeSessionEnvelope()
    }

    public var crashId: String? = null

    private fun endSessionWithCrash(crashId: String): Envelope<SessionPayload> {
        this.crashId = crashId
        activeSession = null
        return fakeSessionEnvelope()
    }

    override fun endSessionWithManual(timestamp: Long, initial: SessionZygote): Envelope<SessionPayload> {
        manualSessionEndCount++
        activeSession = null
        return fakeSessionEnvelope()
    }

    private fun snapshotSession(): Envelope<SessionPayload>? {
        snapshotSessionCount++
        return null
    }
}
