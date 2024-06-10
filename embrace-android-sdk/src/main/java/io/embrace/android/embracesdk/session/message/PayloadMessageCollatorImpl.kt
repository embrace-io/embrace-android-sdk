package io.embrace.android.embracesdk.session.message

import io.embrace.android.embracesdk.capture.envelope.session.SessionEnvelopeSource
import io.embrace.android.embracesdk.event.EventService
import io.embrace.android.embracesdk.event.LogMessageService
import io.embrace.android.embracesdk.gating.GatingService
import io.embrace.android.embracesdk.internal.spans.CurrentSessionSpan
import io.embrace.android.embracesdk.logging.EmbLogger
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.payload.SessionZygote
import io.embrace.android.embracesdk.payload.SessionZygote.Companion.toSession
import io.embrace.android.embracesdk.prefs.PreferencesService
import io.embrace.android.embracesdk.session.captureDataSafely
import io.embrace.android.embracesdk.session.orchestrator.SessionSnapshotType

/**
 * Generates a V2 payload
 */
internal class PayloadMessageCollatorImpl(
    private val gatingService: GatingService,
    private val sessionEnvelopeSource: SessionEnvelopeSource,
    private val eventService: EventService,
    private val logMessageService: LogMessageService,
    private val preferencesService: PreferencesService,
    private val currentSessionSpan: CurrentSessionSpan,
    private val logger: EmbLogger,
) : PayloadMessageCollator {

    override fun buildInitialSession(params: InitialEnvelopeParams): SessionZygote {
        return with(params) {
            SessionZygote(
                sessionId = currentSessionSpan.getSessionId(),
                startTime = startTime,
                isColdStart = coldStart,
                appState = appState,
                startType = startType,
                number = getSessionNumber(preferencesService)
            )
        }
    }

    override fun buildFinalSessionMessage(params: FinalEnvelopeParams.SessionParams): SessionMessage {
        val newParams = FinalEnvelopeParams.SessionParams(
            initial = params.initial,
            endTime = params.endTime,
            lifeEventType = params.lifeEventType,
            crashId = params.crashId,
            endType = params.endType,
            captureSpans = false,
            logger = logger
        )
        val obj = with(newParams) {
            val base = buildFinalBackgroundActivity(newParams)

            val endSession = base.copy(
                networkLogIds = captureDataSafely(logger) {
                    logMessageService.findNetworkLogIds(
                        initial.startTime,
                        endTime
                    )
                },
                terminationTime = terminationTime,
                endTime = endTimeVal
            )
            val envelope = buildWrapperEnvelope(endSession)
            gatingService.gateSessionMessage(envelope)
        }
        return obj.convertToV2Payload(newParams.endType, newParams.crashId)
    }

    override fun buildFinalBackgroundActivityMessage(params: FinalEnvelopeParams.BackgroundActivityParams): SessionMessage {
        val newParams = FinalEnvelopeParams.BackgroundActivityParams(
            initial = params.initial,
            endTime = params.endTime,
            lifeEventType = params.lifeEventType,
            crashId = params.crashId,
            endType = params.endType,
            captureSpans = false,
            logger = logger
        )
        val msg = buildFinalBackgroundActivity(newParams)
        val envelope = buildWrapperEnvelope(msg)
        val obj = gatingService.gateSessionMessage(envelope)
        return obj.convertToV2Payload(newParams.endType, newParams.crashId)
    }

    private fun SessionMessage.convertToV2Payload(endType: SessionSnapshotType, crashId: String?): SessionMessage {
        val envelope = gatingService.gateSessionEnvelope(this, sessionEnvelopeSource.getEnvelope(endType, crashId))
        return copy(
            // future work: make legacy fields null here.
            resource = envelope.resource,
            metadata = envelope.metadata,
            data = envelope.data,
            newVersion = envelope.version,
            type = envelope.type
        )
    }

    /**
     * Creates a background activity stop message.
     */
    private fun buildFinalBackgroundActivity(
        params: FinalEnvelopeParams
    ): Session = with(params) {
        return initial.toSession().copy(
            endTime = endTime,
            eventIds = captureDataSafely(logger) {
                eventService.findEventIdsForSession()
            },
            lastHeartbeatTime = endTime
        )
    }

    private fun buildWrapperEnvelope(finalPayload: Session) = SessionMessage(session = finalPayload)
}
