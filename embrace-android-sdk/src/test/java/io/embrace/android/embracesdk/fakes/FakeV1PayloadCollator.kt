package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.arch.schema.AppTerminationCause
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.session.message.FinalEnvelopeParams
import io.embrace.android.embracesdk.session.message.InitialEnvelopeParams
import io.embrace.android.embracesdk.session.message.PayloadMessageCollator
import java.util.concurrent.atomic.AtomicInteger

internal class FakeV1PayloadCollator(
    val currentSessionSpan: FakeCurrentSessionSpan = FakeCurrentSessionSpan()
) : PayloadMessageCollator {

    val sessionCount = AtomicInteger(0)
    val baCount = AtomicInteger(0)

    override fun buildInitialSession(params: InitialEnvelopeParams) = with(params) {
        val sessionNumber = when (appState) {
            Session.APPLICATION_STATE_FOREGROUND -> {
                sessionCount.incrementAndGet()
            }
            Session.APPLICATION_STATE_BACKGROUND -> {
                baCount.incrementAndGet()
            }
            else -> {
                error("Unknown appState")
            }
        }
        Session(
            sessionId = currentSessionSpan.getSessionId(),
            startTime = startTime,
            isColdStart = coldStart,
            messageType = Session.MESSAGE_TYPE_END,
            appState = appState,
            startType = startType,
            number = sessionNumber,
        )
    }

    /**
     * Builds a fully populated session message. This can be sent to the backend (or stored
     * on disk).
     */
    override fun buildFinalSessionMessage(
        params: FinalEnvelopeParams.SessionParams
    ): SessionMessage = with(params) {
        val endSession = buildFinalBackgroundActivity(params).copy(
            isEndedCleanly = endType.endedCleanly,
            terminationTime = terminationTime,
            isReceivedTermination = receivedTermination,
            endTime = endTimeVal,
            sdkStartupDuration = if (initial.isColdStart) 100L else null,
            startupDuration = if (initial.isColdStart) 1000L else null,
            startupThreshold = if (initial.isColdStart) 5000L else null,
        )
        return buildWrapperEnvelope(params, endSession)
    }

    override fun buildFinalBackgroundActivityMessage(
        params: FinalEnvelopeParams.BackgroundActivityParams
    ): SessionMessage = buildWrapperEnvelope(params, buildFinalBackgroundActivity(params))

    /**
     * Creates a background activity stop message.
     */
    private fun buildFinalBackgroundActivity(
        params: FinalEnvelopeParams
    ): Session = with(params) {
        return initial.copy(
            endTime = endTime,
            lastHeartbeatTime = endTime,
            endType = lifeEventType,
            crashReportId = crashId
        )
    }

    private fun buildWrapperEnvelope(
        params: FinalEnvelopeParams,
        finalPayload: Session,
    ): SessionMessage {
        val spans: List<EmbraceSpanData>? =
            when {
                !params.captureSpans -> null
                !params.isCacheAttempt -> {
                    val appTerminationCause = when {
                        finalPayload.crashReportId != null -> AppTerminationCause.Crash
                        else -> null
                    }
                    currentSessionSpan.endSession(appTerminationCause)
                }

                else -> null
            }

        val spanSnapshots = currentSessionSpan.sessionSpan?.let { span ->
            listOf(EmbraceSpanData(spanData = span))
        } ?: listOf()

        return SessionMessage(
            session = finalPayload,
            spans = spans,
            spanSnapshots = spanSnapshots,
        )
    }
}
