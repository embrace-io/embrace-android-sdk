package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.delivery.PayloadType
import io.embrace.android.embracesdk.internal.delivery.PayloadType.Companion.fromValue
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import io.embrace.android.embracesdk.internal.delivery.intake.IntakeService
import io.embrace.android.embracesdk.internal.otel.schema.EmbType.System
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.Log
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.utils.Uuid

internal class PayloadStoreImpl(
    private val intakeService: IntakeService,
    private val clock: Clock,
    private val processIdProvider: () -> String,
    private val uuidProvider: () -> String = { Uuid.getEmbUuid() },
) : PayloadStore {

    override fun storeSessionPayload(
        envelope: Envelope<SessionPayload>,
        transitionType: TransitionType,
    ) {
        intakeService.take(envelope, createMetadata(SupportedEnvelopeType.SESSION, payloadType = PayloadType.SESSION))
    }

    override fun cacheSessionSnapshot(envelope: Envelope<SessionPayload>) {
        intakeService.take(
            envelope,
            createMetadata(SupportedEnvelopeType.SESSION, complete = false, payloadType = PayloadType.SESSION)
        )
    }

    override fun storeLogPayload(envelope: Envelope<LogPayload>, attemptImmediateRequest: Boolean) {
        val type = findSupportedEnvelopeType(envelope.data.logs)
        val payloadType = getPayloadType(envelope)
        val payloadTypesHeader = getPayloadTypesHeader(envelope)
        intakeService.take(envelope, createMetadata(type, payloadType = payloadType, payloadTypesHeader = payloadTypesHeader))
    }

    override fun storeAttachment(envelope: Envelope<Pair<String, ByteArray>>) {
        intakeService.take(
            envelope,
            createMetadata(
                type = SupportedEnvelopeType.ATTACHMENT,
                payloadType = PayloadType.ATTACHMENT
            )
        )
    }

    override fun cacheEmptyCrashEnvelope(envelope: Envelope<LogPayload>) {
        intakeService.take(
            intake = envelope,
            metadata = createMetadata(
                type = SupportedEnvelopeType.CRASH,
                complete = false,
                payloadType = PayloadType.UNKNOWN
            )
        )
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
    private fun createMetadata(
        type: SupportedEnvelopeType,
        complete: Boolean = true,
        payloadType: PayloadType,
        payloadTypesHeader: String = payloadType.value,
    ): StoredTelemetryMetadata {
        return StoredTelemetryMetadata(
            clock.now(),
            uuidProvider(),
            processIdProvider(),
            type,
            complete,
            payloadType = payloadType,
            payloadTypesHeader = payloadTypesHeader
        )
    }

    /**
     * Returns the payload type for the given [Envelope].
     */
    private fun getPayloadType(envelope: Envelope<LogPayload>) = fromValue(
        envelope.data.logs?.firstOrNull()?.attributes?.findAttributeValue("emb.type")
    )

    /**
     * Returns all unique payload types in the envelope as a comma-separated string.
     * Used for X-EM-PAYLOAD-TYPES header.
     */
    private fun getPayloadTypesHeader(envelope: Envelope<LogPayload>): String {
        return envelope.data.logs
            ?.mapNotNull { log -> log.attributes?.findAttributeValue("emb.type") }
            ?.distinct()
            ?.joinToString(",") ?: ""
    }
}
