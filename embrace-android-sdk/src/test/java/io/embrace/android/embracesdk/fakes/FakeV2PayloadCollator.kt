package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.payload.ApplicationState
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.payload.SessionZygote
import io.embrace.android.embracesdk.session.message.FinalEnvelopeParams
import io.embrace.android.embracesdk.session.message.InitialEnvelopeParams
import io.embrace.android.embracesdk.session.message.PayloadMessageCollator
import java.util.concurrent.atomic.AtomicInteger

internal class FakeV2PayloadCollator(
    val currentSessionSpan: FakeCurrentSessionSpan = FakeCurrentSessionSpan()
) : PayloadMessageCollator {

    val sessionCount = AtomicInteger(0)
    val baCount = AtomicInteger(0)

    override fun buildInitialSession(params: InitialEnvelopeParams) = with(params) {
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
    override fun buildFinalSessionMessage(params: FinalEnvelopeParams) = SessionMessage()
}
