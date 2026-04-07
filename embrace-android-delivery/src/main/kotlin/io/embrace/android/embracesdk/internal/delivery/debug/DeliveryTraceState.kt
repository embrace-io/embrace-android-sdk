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

    var threadName: String? = null

    /**
     * The schedule service was informed of a new payload
     */
    internal class IntakeServiceAcceptedEnvelope(val metadata: StoredTelemetryMetadata) : DeliveryTraceState() {
        override fun toString(): String = "[$threadName] IntakeServiceAccepted, ${metadata.toReportString()}"
    }

    /**
     * An envelope was accepted by the intake service
     */
    internal class ScheduleServiceInformed(val metadata: StoredTelemetryMetadata) : DeliveryTraceState() {
        override fun toString(): String = "[$threadName] ScheduleServiceInformed, ${metadata.toReportString()}"
    }

    /**
     * The payload store service persisted a payload
     */
    internal class PayloadStored(val metadata: StoredTelemetryMetadata) : DeliveryTraceState() {
        override fun toString(): String = "[$threadName] PayloadStored, ${metadata.toReportString()}"
    }

    /**
     * The payload store service deleted a payload
     */
    internal class PayloadDeleted(val metadata: StoredTelemetryMetadata) : DeliveryTraceState() {
        override fun toString(): String = "[$threadName] PayloadDeleted, ${metadata.toReportString()}"
    }

    /**
     * The payload store service loaded a payload
     */
    internal class PayloadLoaded(val success: Boolean) : DeliveryTraceState() {
        override fun toString(): String = "[$threadName] PayloadLoaded, success=$success"
    }

    /**
     * The payload store returned a list of payloads by priority
     */
    internal class GetPayloadsByPriority(val payloads: List<StoredTelemetryMetadata>) : DeliveryTraceState() {
        override fun toString(): String =
            "[$threadName] GetPayloadsByPriority, count=${payloads.size},\n${payloads.reportListString()}"
    }

    /**
     * The payload store returned a list of undelivered payloads
     */
    internal class GetUndeliveredPayloads(val payloads: List<StoredTelemetryMetadata>) : DeliveryTraceState() {
        override fun toString(): String =
            "[$threadName] GetUndeliveredPayloads, count=${payloads.size},\n${payloads.reportListString()}"
    }

    /**
     * A HTTP call ended with the attached results
     */
    internal class HttpCallEnded(
        val result: ExecutionResult,
        val envelopeType: SupportedEnvelopeType,
        val payloadType: String,
    ) : DeliveryTraceState() {
        override fun toString(): String = "[$threadName] HttpCallEnded, ${result.javaClass.simpleName}, $envelopeType, $payloadType"
    }

    /**
     * Part caching was started for the current part
     */
    internal object SessionPartCachingStarted : DeliveryTraceState() {
        override fun toString(): String = "[$threadName] SessionPartCachingStarted"
    }

    /**
     * Part caching was stopped for the current part
     */
    internal object SessionPartCachingStopped : DeliveryTraceState() {
        override fun toString(): String = "[$threadName] SessionPartCachingStopped"
    }

    /**
     * A part was written to the cache.
     */
    internal object SessionPartCacheAttempt : DeliveryTraceState() {
        override fun toString(): String = "[$threadName] SessionPartCacheAttempt"
    }

    /**
     * The delivery attempt has been queued on scheduling thread
     */
    internal object QueueDeliveryAttempt : DeliveryTraceState() {
        override fun toString(): String = "[$threadName] QueueDeliveryAttempt"
    }

    /**
     * A payload was sent to delivery worker for delivery
     */
    class ExecuteDelivery(private val payload: StoredTelemetryMetadata) : DeliveryTraceState() {
        override fun toString(): String = "[$threadName] ExecuteDelivery, ${payload.toReportString()}"
    }

    /**
     * A payload result was obtained
     */
    class PayloadResult(
        private val payload: StoredTelemetryMetadata,
        private val result: ExecutionResult,
    ) : DeliveryTraceState() {
        override fun toString(): String = "[$threadName] PayloadResult, ${payload.toReportString()}, result=${result.javaClass.simpleName}"
    }

    /**
     * A payload queue was created
     */
    class FindNextPayload(
        private val payloadsByPriority: List<StoredTelemetryMetadata>,
        private val payloadToSend: StoredTelemetryMetadata?,
    ) : DeliveryTraceState() {
        override fun toString(): String {
            return "[$threadName] FindNextPayload, payloadsByPriority=${payloadsByPriority.map { it.uuid }}, " +
                "payloadsToSend=${payloadToSend?.uuid ?: "<none>" }}"
        }
    }

    /**
     * A HTTP request was started
     */
    class ServerReceivedRequest(private val endpoint: String) : DeliveryTraceState() {
        override fun toString(): String = "[$threadName] ServerReceivedRequest, $endpoint"
    }

    /**
     * A HTTP request was completed
     */
    class ServerCompletedRequest(
        private val endpoint: String,
        private val metadata: String
    ) : DeliveryTraceState() {
        override fun toString(): String = "[$threadName] ServerCompletedRequest, $endpoint $metadata"
    }

    internal fun StoredTelemetryMetadata.toReportString(): String {
        return "$timestamp, $uuid, $payloadType, complete=$complete"
    }

    internal fun List<StoredTelemetryMetadata>.reportListString() = joinToString(
        "\n",
        transform = { it.toReportString() }
    )
}
