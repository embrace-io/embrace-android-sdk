package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.payload.ApplicationState
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.session.SessionZygote
import io.embrace.android.embracesdk.internal.session.message.FinalEnvelopeParams
import io.embrace.android.embracesdk.internal.session.message.InitialEnvelopeParams
import io.embrace.android.embracesdk.internal.session.message.PayloadMessageCollator
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionSnapshotType
import java.util.concurrent.atomic.AtomicInteger

class FakeV2PayloadCollator(
    val currentSessionSpan: FakeCurrentSessionSpan = FakeCurrentSessionSpan(),
) : PayloadMessageCollator {

    val sessionCount: AtomicInteger = AtomicInteger(0)
    val baCount: AtomicInteger = AtomicInteger(0)

    override fun buildInitialSession(params: InitialEnvelopeParams): SessionZygote = with(params) {
        val sessionNumber = when (appState) {
            ApplicationState.FOREGROUND -> {
                sessionCount.incrementAndGet()
            }

            ApplicationState.BACKGROUND -> {
                baCount.incrementAndGet()
            }

            else -> {
                error("Unknown appState")
            }
        }
        SessionZygote(
            sessionId = currentSessionSpan.getSessionId(),
            startTime = startTime,
            isColdStart = coldStart,
            appState = appState,
            startType = startType,
            number = sessionNumber,
        )
    }

    /**
     * Builds a fully populated session message. This can be sent to the backend (or stored
     * on disk).
     */
    override fun buildFinalEnvelope(
        params: FinalEnvelopeParams,
    ): Envelope<SessionPayload> {
        if (params.endType != SessionSnapshotType.PERIODIC_CACHE) {
            currentSessionSpan.endSession(startNewSession = params.startNewSession)
        }
        return Envelope(data = SessionPayload())
    }
}
