package io.embrace.android.embracesdk.internal.delivery.debug

import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import io.embrace.android.embracesdk.internal.delivery.execution.ExecutionResult
import java.util.concurrent.CopyOnWriteArrayList

class DeliveryTracer {

    private val events: MutableList<DeliveryTraceState> = CopyOnWriteArrayList()

    fun onHttpCallEnded(result: ExecutionResult, envelopeType: SupportedEnvelopeType, payloadType: String) {
        addWithThreadInfo(DeliveryTraceState.HttpCallEnded(result, envelopeType, payloadType))
    }

    fun onPayloadIntake(metadata: StoredTelemetryMetadata) {
        addWithThreadInfo(DeliveryTraceState.ScheduleServiceInformed(metadata))
    }

    fun onTake(metadata: StoredTelemetryMetadata) {
        addWithThreadInfo(DeliveryTraceState.IntakeServiceAcceptedEnvelope(metadata))
    }

    fun onStore(metadata: StoredTelemetryMetadata) {
        addWithThreadInfo(DeliveryTraceState.PayloadStored(metadata))
    }

    fun onDelete(metadata: StoredTelemetryMetadata) {
        addWithThreadInfo(DeliveryTraceState.PayloadDeleted(metadata))
    }

    fun onLoadPayloadAsStream(success: Boolean) {
        addWithThreadInfo(DeliveryTraceState.PayloadLoaded(success))
    }

    fun onGetPayloadsByPriority(payloads: List<StoredTelemetryMetadata>) {
        addWithThreadInfo(DeliveryTraceState.GetPayloadsByPriority(payloads))
    }

    fun onGetUndeliveredPayloads(payloads: List<StoredTelemetryMetadata>) {
        addWithThreadInfo(DeliveryTraceState.GetUndeliveredPayloads(payloads))
    }

    fun onCachingStopped() {
        addWithThreadInfo(DeliveryTraceState.SessionPartCachingStopped)
    }

    fun onCachingStarted() {
        addWithThreadInfo(DeliveryTraceState.SessionPartCachingStarted)
    }

    fun onSessionCache() {
        addWithThreadInfo(DeliveryTraceState.SessionPartCacheAttempt)
    }

    fun generateReport(): String {
        return "Delivery layer Trace Report\n" +
            events.toList().mapIndexed { k, state ->
                "#$k, $state"
            }.joinToString("\n")
    }

    fun onQueueDeliveryAttempt() {
        addWithThreadInfo(DeliveryTraceState.QueueDeliveryAttempt)
    }

    fun onFindNextPayload(
        payloadsByPriority: List<StoredTelemetryMetadata>,
        payloadToSend: StoredTelemetryMetadata?,
    ) {
        addWithThreadInfo(DeliveryTraceState.FindNextPayload(payloadsByPriority, payloadToSend))
    }

    fun onExecuteDelivery(payload: StoredTelemetryMetadata) {
        addWithThreadInfo(DeliveryTraceState.ExecuteDelivery(payload))
    }

    fun onProcessingDeliveryResult(payload: StoredTelemetryMetadata, result: ExecutionResult) {
        addWithThreadInfo(DeliveryTraceState.PayloadResult(payload, result))
    }

    fun onServerReceivedRequest(endpoint: String) {
        addWithThreadInfo(DeliveryTraceState.ServerReceivedRequest(endpoint))
    }

    fun onServerCompletedRequest(endpoint: String, sessionId: String) {
        addWithThreadInfo(DeliveryTraceState.ServerCompletedRequest(endpoint, sessionId))
    }

    private fun addWithThreadInfo(state: DeliveryTraceState) {
        state.threadName = Thread.currentThread().name
        events.add(state)
    }
}
