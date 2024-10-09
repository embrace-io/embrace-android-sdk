package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import io.embrace.android.embracesdk.internal.delivery.intake.IntakeService
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.utils.Uuid

internal class V2PayloadStore(
    private val intakeService: IntakeService,
    private val clock: Clock,
    private val uuidProvider: () -> String = { Uuid.getEmbUuid() },
) : PayloadStore {

    override fun storeSessionPayload(
        envelope: Envelope<SessionPayload>,
        transitionType: TransitionType,
    ) {
        intakeService.take(envelope, createMetadata(SupportedEnvelopeType.SESSION))
    }

    override fun cacheSessionSnapshot(envelope: Envelope<SessionPayload>) {
        intakeService.take(envelope, createMetadata(SupportedEnvelopeType.SESSION, complete = false))
    }

    override fun storeLogPayload(envelope: Envelope<LogPayload>, attemptImmediateRequest: Boolean) {
        val type = SupportedEnvelopeType.LOG // TODO: distinguish between log/network/crash type
        intakeService.take(envelope, createMetadata(type))
    }

    override fun handleCrash(crashId: String) {
        intakeService.shutdown()
    }

    /**
     * Constructs a [StoredTelemetryMetadata] object from the given [Envelope].
     */
    private fun createMetadata(type: SupportedEnvelopeType, complete: Boolean = true): StoredTelemetryMetadata {
        return StoredTelemetryMetadata(clock.now(), uuidProvider(), type, complete)
    }
}
