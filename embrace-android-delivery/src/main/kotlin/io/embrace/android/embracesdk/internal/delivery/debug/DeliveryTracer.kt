package io.embrace.android.embracesdk.internal.delivery.debug

import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import io.embrace.android.embracesdk.internal.delivery.execution.ExecutionResult
import java.util.concurrent.CopyOnWriteArrayList

class DeliveryTracer {

    private val events: MutableList<DeliveryTraceState> = CopyOnWriteArrayList()

    fun onHttpCallEnded(result: ExecutionResult, envelopeType: SupportedEnvelopeType, payloadType: String) {
        events.add(DeliveryTraceState.HttpCallEnded(result, envelopeType, payloadType))
    }

    fun onPayloadIntake(metadata: StoredTelemetryMetadata) {
        events.add(DeliveryTraceState.ScheduleServiceInformed(metadata))
    }

    fun onTake(metadata: StoredTelemetryMetadata) {
        events.add(DeliveryTraceState.IntakeServiceAcceptedEnvelope(metadata))
    }

    fun onStore(metadata: StoredTelemetryMetadata) {
        events.add(DeliveryTraceState.PayloadStored(metadata))
    }

    fun onDelete(metadata: StoredTelemetryMetadata) {
        events.add(DeliveryTraceState.PayloadDeleted(metadata))
    }

    fun onLoadPayloadAsStream(success: Boolean) {
        events.add(DeliveryTraceState.PayloadLoaded(success))
    }

    fun onGetPayloadsByPriority(payloads: List<StoredTelemetryMetadata>) {
        events.add(DeliveryTraceState.GetPayloadsByPriority(payloads))
    }

    fun onGetUndeliveredPayloads(payloads: List<StoredTelemetryMetadata>) {
        events.add(DeliveryTraceState.GetUndeliveredPayloads(payloads))
    }

    fun onCachingStopped() {
        events.add(DeliveryTraceState.SessionCachingStopped)
    }

    fun onCachingStarted() {
        events.add(DeliveryTraceState.SessionCachingStarted)
    }

    fun onSessionCache() {
        events.add(DeliveryTraceState.SessionCacheAttempt)
    }

    fun generateReport(): String {
        return "Delivery layer Trace Report\n" +
            events.toList().mapIndexed { k, state ->
                "#$k, $state"
            }.joinToString("\n")
    }

    fun onStartDeliveryLoop() {
        events.add(DeliveryTraceState.StartDeliveryLoop)
    }

    fun onPayloadQueueCreated(
        payloadsByPriority: List<StoredTelemetryMetadata>,
        payloadsToSend: List<StoredTelemetryMetadata>,
    ) {
        events.add(DeliveryTraceState.PayloadQueueCreated(payloadsByPriority, payloadsToSend))
    }

    fun onPayloadEnqueued(payload: StoredTelemetryMetadata) {
        events.add(DeliveryTraceState.PayloadEnqueued(payload))
    }

    fun onPayloadResult(payload: StoredTelemetryMetadata, result: ExecutionResult) {
        events.add(DeliveryTraceState.PayloadResult(payload, result))
    }

    fun onServerReceivedRequest(endpoint: String) {
        events.add(DeliveryTraceState.ServerReceivedRequest(endpoint))
    }

    fun onServerCompletedRequest(endpoint: String, sessionId: String) {
        events.add(DeliveryTraceState.ServerCompletedRequest(endpoint, sessionId))
    }
}
