package io.embrace.android.embracesdk.session.message

import io.embrace.android.embracesdk.capture.envelope.session.SessionEnvelopeSource
import io.embrace.android.embracesdk.gating.GatingService
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.session.orchestrator.SessionSnapshotType

/**
 * Generates a V2 payload. This currently calls through to the V1 collator for
 * backwards compatibility.
 */
internal class V2PayloadMessageCollator(
    private val gatingService: GatingService,
    private val v1Collator: V1PayloadMessageCollator,
    private val sessionEnvelopeSource: SessionEnvelopeSource
) : PayloadMessageCollator {

    override fun buildInitialSession(params: InitialEnvelopeParams): Session {
        return v1Collator.buildInitialSession(params)
    }

    override fun buildFinalSessionMessage(params: FinalEnvelopeParams.SessionParams): SessionMessage {
        return v1Collator.buildFinalSessionMessage(params)
            .convertToV2Payload(params.endType)
    }

    override fun buildFinalBackgroundActivityMessage(params: FinalEnvelopeParams.BackgroundActivityParams): SessionMessage {
        return v1Collator.buildFinalBackgroundActivityMessage(params)
            .convertToV2Payload(params.endType)
    }

    private fun SessionMessage.convertToV2Payload(endType: SessionSnapshotType): SessionMessage {
        val envelope = gatingService.gateSessionEnvelope(this, sessionEnvelopeSource.getEnvelope(endType))
        return copy(
            // future work: make legacy fields null here.
            resource = envelope.resource,
            metadata = envelope.metadata,
            data = envelope.data,
            newVersion = envelope.version,
            type = envelope.type,

            // make legacy fields null
            userInfo = null,
            version = null,
            spans = null,

            // future: make appInfo, deviceInfo, performanceInfo, breadcrumbs null.
            // this is blocked until we can migrate others
        )
    }
}
