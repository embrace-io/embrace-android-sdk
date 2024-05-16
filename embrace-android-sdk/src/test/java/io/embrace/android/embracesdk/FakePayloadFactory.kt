package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.fakes.FakeCurrentSessionSpan
import io.embrace.android.embracesdk.fakes.fakeV1EndedSessionMessage
import io.embrace.android.embracesdk.fakes.fakeV1SessionMessage
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.session.lifecycle.ProcessState
import io.embrace.android.embracesdk.session.message.PayloadFactory
import java.util.concurrent.atomic.AtomicInteger

internal class FakePayloadFactory(
    val currentSessionSpan: FakeCurrentSessionSpan = FakeCurrentSessionSpan()
) : PayloadFactory {
    val startSessionTimestamps = mutableListOf<Long>()
    val endSessionTimestamps = mutableListOf<Long>()
    var manualSessionEndCount = 0
    var manualSessionStartCount = 0
    var snapshotSessionCount = 0
    val endBaTimestamps = mutableListOf<Long>()
    val startBaTimestamps = mutableListOf<Long>()
    var foregroundCrashId: String? = null
    var backgroundCrashId: String? = null
    private var activeSession: Session? = null
    private var snapshotBaCount: Int = 0
    private val sessionCount = AtomicInteger(0)
    private val baCount = AtomicInteger(0)

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
            ProcessState.FOREGROUND -> endSessionWithState(initial = initial, timestamp = timestamp)
            ProcessState.BACKGROUND -> endBackgroundActivityWithState(initial = initial, timestamp = timestamp)
        }
    }

    override fun endPayloadWithCrash(
        state: ProcessState,
        timestamp: Long,
        initial: Session,
        crashId: String
    ): SessionMessage {
        return endSessionWithCrash(state, timestamp, initial, crashId)
    }

    override fun snapshotPayload(
        state: ProcessState,
        timestamp: Long,
        initial: Session
    ): SessionMessage {
        return when (state) {
            ProcessState.FOREGROUND -> snapshotSession(initial)
            ProcessState.BACKGROUND -> snapshotBackgroundActivity(initial)
        }
    }

    private fun startBackgroundActivityWithState(timestamp: Long): Session {
        startBaTimestamps.add(timestamp)
        activeSession = newSession(
            startTimeMs = timestamp,
            appState = Session.APPLICATION_STATE_BACKGROUND,
            number = baCount.incrementAndGet(),
            startType = Session.LifeEventType.STATE
        )
        return checkNotNull(activeSession)
    }

    private fun endBackgroundActivityWithState(
        initial: Session,
        timestamp: Long,
    ): SessionMessage {
        endBaTimestamps.add(timestamp)
        return endSession(initial = initial, endTimeMs = timestamp)
    }

    private fun endSessionWithCrash(
        state: ProcessState,
        timestamp: Long,
        initial: Session,
        crashId: String
    ): SessionMessage {
        when (state) {
            ProcessState.FOREGROUND -> foregroundCrashId = crashId
            ProcessState.BACKGROUND -> backgroundCrashId = crashId
        }

        return endSession(initial = initial, endTimeMs = timestamp, crashId = crashId)
    }

    private fun snapshotBackgroundActivity(initial: Session): SessionMessage {
        snapshotBaCount++
        return snapshot(initial = initial)
    }

    private fun startSessionWithState(timestamp: Long): Session {
        startSessionTimestamps.add(timestamp)
        activeSession = newSession(
            startTimeMs = timestamp,
            appState = Session.APPLICATION_STATE_FOREGROUND,
            number = sessionCount.incrementAndGet(),
            startType = Session.LifeEventType.STATE,

        )
        return checkNotNull(activeSession)
    }

    override fun startSessionWithManual(timestamp: Long): Session {
        manualSessionStartCount++
        activeSession = newSession(
            startTimeMs = timestamp,
            appState = Session.APPLICATION_STATE_FOREGROUND,
            number = sessionCount.incrementAndGet(),
            startType = Session.LifeEventType.MANUAL
        )
        return checkNotNull(activeSession)
    }

    private fun endSessionWithState(
        initial: Session,
        timestamp: Long,
    ): SessionMessage {
        endSessionTimestamps.add(timestamp)
        return endSession(initial = initial, endTimeMs = timestamp)
    }

    override fun endSessionWithManual(timestamp: Long, initial: Session): SessionMessage {
        manualSessionEndCount++
        return endSession(initial = initial, endTimeMs = timestamp)
    }

    private fun snapshotSession(initial: Session): SessionMessage {
        snapshotSessionCount++
        return snapshot(initial = initial)
    }

    private fun newSession(
        startTimeMs: Long,
        appState: String,
        number: Int,
        startType: Session.LifeEventType
    ) = Session(
        sessionId = currentSessionSpan.getSessionId(),
        startTime = startTimeMs,
        appState = appState,
        number = number,
        messageType = "en",
        isColdStart = false,
        startType = startType
    )

    private fun endSession(initial: Session, endTimeMs: Long = 0L, crashId: String? = null): SessionMessage {
        val endSessionMessage = fakeV1EndedSessionMessage(
            session = initial.copy(endTime = endTimeMs, crashReportId = crashId),
            spans = currentSessionSpan.endSession()
        )
        activeSession = null
        return endSessionMessage
    }

    private fun snapshot(initial: Session) = fakeV1SessionMessage().copy(
        session = initial,
        spanSnapshots = listOf(
            EmbraceSpanData(checkNotNull(currentSessionSpan.sessionSpan))
        )
    )
}
