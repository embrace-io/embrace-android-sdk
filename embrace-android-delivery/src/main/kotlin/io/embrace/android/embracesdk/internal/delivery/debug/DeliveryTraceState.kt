package io.embrace.android.embracesdk.internal.delivery.debug

import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import io.embrace.android.embracesdk.internal.delivery.execution.ExecutionResult

/**
 * Models states in the delivery layer that can be useful as a starting point for debugging
 * problems with payload delivery. This is not an exhaustive model of states and it may be helpful to add
 * more in future to aid debugging.
 */
internal sealed class DeliveryTraceState {

    /**
     * The schedule service was informed of a new payload
     */
    internal class IntakeServiceAcceptedEnvelope(val metadata: StoredTelemetryMetadata) : DeliveryTraceState() {
        override fun toString(): String = "IntakeServiceAccepted, ${metadata.toReportString()}"
    }

    /**
     * An envelope was accepted by the intake service
     */
    internal class ScheduleServiceInformed(val metadata: StoredTelemetryMetadata) : DeliveryTraceState() {
        override fun toString(): String = "ScheduleServiceInformed, ${metadata.toReportString()}"
    }

    /**
     * The payload store service persisted a payload
     */
    internal class PayloadStored(val metadata: StoredTelemetryMetadata) : DeliveryTraceState() {
        override fun toString(): String = "PayloadStored, ${metadata.toReportString()}"
    }

    /**
     * The payload store service deleted a payload
     */
    internal class PayloadDeleted(val metadata: StoredTelemetryMetadata) : DeliveryTraceState() {
        override fun toString(): String = "PayloadDeleted, ${metadata.toReportString()}"
    }

    /**
     * The payload store service loaded a payload
     */
    internal class PayloadLoaded(val success: Boolean) : DeliveryTraceState() {
        override fun toString(): String = "PayloadLoaded, success=$success"
    }

    /**
     * The payload store returned a list of payloads by priority
     */
    internal class GetPayloadsByPriority(val payloads: List<StoredTelemetryMetadata>) : DeliveryTraceState() {
        override fun toString(): String =
            "GetPayloadsByPriority, count=${payloads.size},\n${payloads.reportListString()}"
    }

    /**
     * The payload store returned a list of undelivered payloads
     */
    internal class GetUndeliveredPayloads(val payloads: List<StoredTelemetryMetadata>) : DeliveryTraceState() {
        override fun toString(): String =
            "GetUndeliveredPayloads, count=${payloads.size},\n${payloads.reportListString()}"
    }

    /**
     * A HTTP call ended with the attached results
     */
    internal class HttpCallEnded(
        val result: ExecutionResult,
        val envelopeType: SupportedEnvelopeType,
        val payloadType: String,
    ) : DeliveryTraceState() {
        override fun toString(): String = "HttpCallEnded, ${result.javaClass.simpleName}, $envelopeType, $payloadType"
    }

    /**
     * Session caching was started for the current session
     */
    internal object SessionCachingStarted : DeliveryTraceState() {
        override fun toString(): String = "SessionCachingStarted"
    }

    /**
     * Session caching was stopped for the current session
     */
    internal object SessionCachingStopped : DeliveryTraceState() {
        override fun toString(): String = "SessionCachingStopped"
    }

    /**
     * A session was written to the cache.
     */
    internal object SessionCacheAttempt : DeliveryTraceState() {
        override fun toString(): String = "SessionCacheAttempt"
    }

    /**
     * The delivery loop was started
     */
    internal class StartDeliveryLoop(private val loopAlreadyActive: Boolean) : DeliveryTraceState() {
        override fun toString(): String = "StartDeliveryLoop loopAlreadyActive=$loopAlreadyActive"
    }

    /**
     * A payload was enqueued for delivery
     */
    class PayloadEnqueued(private val payload: StoredTelemetryMetadata) : DeliveryTraceState() {
        override fun toString(): String = "PayloadEnqueued, ${payload.toReportString()}"
    }

    /**
     * A payload result was obtained
     */
    class PayloadResult(
        private val payload: StoredTelemetryMetadata,
        private val result: ExecutionResult,
    ) : DeliveryTraceState() {
        override fun toString(): String = "PayloadResult, ${payload.toReportString()}, result=${result.javaClass.simpleName}"
    }

    /**
     * A payload queue was created
     */
    class PayloadQueueCreated(
        private val payloadsByPriority: List<StoredTelemetryMetadata>,
        private val payloadsToSend: List<StoredTelemetryMetadata>,
    ) : DeliveryTraceState() {
        override fun toString(): String {
            return "PayloadQueueCreated, payloadsByPriority=${payloadsByPriority.map { it.uuid }}, " +
                "payloadsToSend=${payloadsToSend.map { it.uuid }}"
        }
    }

    internal fun StoredTelemetryMetadata.toReportString(): String {
        return "$timestamp, $uuid, $payloadType, complete=$complete"
    }

    internal fun List<StoredTelemetryMetadata>.reportListString() = joinToString(
        "\n",
        transform = { it.toReportString() }
    )
}
