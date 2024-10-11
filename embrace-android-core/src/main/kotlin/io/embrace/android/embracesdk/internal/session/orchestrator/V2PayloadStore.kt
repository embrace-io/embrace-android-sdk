package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.internal.arch.schema.EmbType.System
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import io.embrace.android.embracesdk.internal.delivery.intake.IntakeService
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.Log
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.spans.findAttributeValue
import io.embrace.android.embracesdk.internal.utils.Uuid

internal class V2PayloadStore(
    private val intakeService: IntakeService,
    private val clock: Clock,
    private val processIdProvider: () -> String,
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
        val type = findSupportedEnvelopeType(envelope.data.logs)
        intakeService.take(envelope, createMetadata(type))
    }

    override fun handleCrash(crashId: String) {
        intakeService.shutdown()
    }

    private fun findSupportedEnvelopeType(logs: List<Log>?): SupportedEnvelopeType {
        // look at emb.type in the first log. This assumes logs are homogenous.
        val embType: String? = logs?.firstOrNull()?.attributes?.findAttributeValue("emb.type")

        return when (embType) {
            System.Crash.value -> SupportedEnvelopeType.CRASH
            System.NativeCrash.value -> SupportedEnvelopeType.CRASH
            System.ReactNativeCrash.value -> SupportedEnvelopeType.CRASH
            System.FlutterException.value -> SupportedEnvelopeType.CRASH
            System.NetworkCapturedRequest.value -> SupportedEnvelopeType.BLOB
            else -> SupportedEnvelopeType.LOG
        }
    }

    /**
     * Constructs a [StoredTelemetryMetadata] object from the given [Envelope].
     */
    private fun createMetadata(type: SupportedEnvelopeType, complete: Boolean = true): StoredTelemetryMetadata {
        return StoredTelemetryMetadata(clock.now(), uuidProvider(), processIdProvider(), type, complete)
    }
}
