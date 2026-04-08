package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.session.SessionPartToken
import io.embrace.android.embracesdk.internal.session.message.FinalEnvelopeParams
import io.embrace.android.embracesdk.internal.session.message.InitialEnvelopeParams
import io.embrace.android.embracesdk.internal.session.message.PayloadMessageCollator
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionPartSnapshotType
import java.util.concurrent.atomic.AtomicInteger

class FakePayloadMessageCollator(
    val currentSessionSpan: FakeCurrentSessionSpan = FakeCurrentSessionSpan(),
) : PayloadMessageCollator {

    val sessionCount: AtomicInteger = AtomicInteger(0)
    val baCount: AtomicInteger = AtomicInteger(0)

    override fun buildInitialPart(params: InitialEnvelopeParams): SessionPartToken = with(params) {
        val sessionNumber = when (appState) {
            AppState.FOREGROUND -> {
                sessionCount.incrementAndGet()
            }

            AppState.BACKGROUND -> {
                baCount.incrementAndGet()
            }
        }
        SessionPartToken(
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
    ): Envelope<SessionPartPayload> {
        if (params.endType != SessionPartSnapshotType.PERIODIC_CACHE) {
            currentSessionSpan.endSession(startNewSession = params.startNewSession)
        }
        return Envelope(data = SessionPartPayload())
    }
}
