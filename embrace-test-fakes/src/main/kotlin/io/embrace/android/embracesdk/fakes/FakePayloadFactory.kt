package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.session.SessionPartToken
import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.session.message.PayloadFactory

class FakePayloadFactory : PayloadFactory {

    val startSessionTimestamps: MutableList<Long> = mutableListOf()
    val endSessionTimestamps: MutableList<Long> = mutableListOf()
    var manualSessionEndCount: Int = 0
    var manualSessionStartCount: Int = 0
    var snapshotSessionCount: Int = 0
    private var activeSession: SessionPartToken? = null
    val endBaTimestamps: MutableList<Long> = mutableListOf()
    val startBaTimestamps: MutableList<Long> = mutableListOf()
    var baCrashId: String? = null
    private var snapshotBaCount: Int = 0

    override fun startPayloadWithState(
        state: AppState,
        timestamp: Long,
        coldStart: Boolean,
    ): SessionPartToken {
        return when (state) {
            AppState.FOREGROUND -> startSessionWithState(timestamp)
            AppState.BACKGROUND -> startBackgroundActivityWithState(timestamp)
        }
    }

    override fun endPayloadWithState(
        state: AppState,
        timestamp: Long,
        initial: SessionPartToken,
    ): Envelope<SessionPartPayload> {
        return when (state) {
            AppState.FOREGROUND -> endSessionWithState(timestamp)
            AppState.BACKGROUND -> endBackgroundActivityWithState(timestamp)
        }
    }

    override fun endPayloadWithCrash(
        state: AppState,
        timestamp: Long,
        initial: SessionPartToken,
        crashId: String,
    ): Envelope<SessionPartPayload> {
        return when (state) {
            AppState.FOREGROUND -> endSessionWithCrash(crashId)
            AppState.BACKGROUND -> endBackgroundActivityWithCrash(crashId)
        }
    }

    override fun snapshotPayload(
        state: AppState,
        timestamp: Long,
        initial: SessionPartToken,
    ): Envelope<SessionPartPayload>? {
        return when (state) {
            AppState.FOREGROUND -> snapshotSession()
            AppState.BACKGROUND -> snapshotBackgroundActivity()
        }
    }

    private fun startBackgroundActivityWithState(timestamp: Long): SessionPartToken {
        startBaTimestamps.add(timestamp)
        return fakeSessionPartToken().copy(appState = AppState.BACKGROUND)
    }

    private fun endBackgroundActivityWithState(timestamp: Long): Envelope<SessionPartPayload> {
        endBaTimestamps.add(timestamp)
        return fakeSessionEnvelope()
    }

    private fun endBackgroundActivityWithCrash(
        crashId: String,
    ): Envelope<SessionPartPayload> {
        this.baCrashId = crashId
        return fakeSessionEnvelope()
    }

    private fun snapshotBackgroundActivity(): Envelope<SessionPartPayload> {
        snapshotBaCount++
        return fakeSessionEnvelope()
    }

    private fun startSessionWithState(timestamp: Long): SessionPartToken {
        startSessionTimestamps.add(timestamp)
        activeSession = fakeSessionPartToken().copy(startTime = timestamp)
        return checkNotNull(activeSession)
    }

    override fun startSessionWithManual(timestamp: Long): SessionPartToken {
        manualSessionStartCount++
        activeSession = fakeSessionPartToken()
        return checkNotNull(activeSession)
    }

    private fun endSessionWithState(timestamp: Long): Envelope<SessionPartPayload> {
        endSessionTimestamps.add(timestamp)
        activeSession = null
        return fakeSessionEnvelope()
    }

    var crashId: String? = null

    private fun endSessionWithCrash(crashId: String): Envelope<SessionPartPayload> {
        this.crashId = crashId
        activeSession = null
        return fakeSessionEnvelope()
    }

    override fun endSessionWithManual(timestamp: Long, initial: SessionPartToken): Envelope<SessionPartPayload> {
        manualSessionEndCount++
        activeSession = null
        return fakeSessionEnvelope()
    }

    override fun createEmptyLogEnvelope(): Envelope<LogPayload> = fakeEmptyLogEnvelope()

    private fun snapshotSession(): Envelope<SessionPartPayload>? {
        snapshotSessionCount++
        return null
    }
}
