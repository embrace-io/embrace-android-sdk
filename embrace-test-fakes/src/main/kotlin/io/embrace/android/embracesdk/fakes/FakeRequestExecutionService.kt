package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.assertions.getSessionId
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import io.embrace.android.embracesdk.internal.delivery.execution.ExecutionResult
import io.embrace.android.embracesdk.internal.delivery.execution.RequestExecutionService
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.spans.findAttributeValue
import io.opentelemetry.semconv.incubating.LogIncubatingAttributes
import java.io.InputStream
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.zip.GZIPInputStream

class FakeRequestExecutionService(
    private var strictMode: Boolean = true
) : RequestExecutionService {

    private val serializer = TestPlatformSerializer()
    var constantResponse: ExecutionResult = ExecutionResult.Success
    var responseAction: (intake: Envelope<*>) -> ExecutionResult = { _ -> constantResponse }
    var exceptionOnExecution: Throwable? = null
    val attemptedHttpRequests = ConcurrentLinkedQueue<Envelope<*>>()
    val sessionIds = mutableSetOf<String>()
    val logIds = mutableSetOf<String>()

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : Any> getRequests(): List<Envelope<T>> {
        if (T::class != SessionPayload::class && T::class != LogPayload::class) {
            error("Unsupported type: ${T::class}")
        }
        return attemptedHttpRequests.filter { it.data is T } as List<Envelope<T>>
    }

    override fun attemptHttpRequest(
        payloadStream: () -> InputStream,
        envelopeType: SupportedEnvelopeType,
        payloadType: String,
    ): ExecutionResult {
        exceptionOnExecution?.run { throw this }
        val bufferedStream = GZIPInputStream(payloadStream())
        val envelope: Envelope<*> = serializer.fromJson(bufferedStream, envelopeType.serializedType)
        processEnvelope(envelope)
        return responseAction(envelope)
    }

    @Suppress("UNCHECKED_CAST")
    private fun processEnvelope(envelope: Envelope<*>) {
        if (strictMode) {
            if (envelope.data is SessionPayload) {
                val sid = (envelope as Envelope<SessionPayload>).getSessionId()

                if (sessionIds.contains(sid)) {
                    error("Duplicate session id detected in request: $sid")
                }
                sessionIds.add(sid)
            } else if (envelope.data is LogPayload) {
                val log = envelope as Envelope<LogPayload>
                val logs = log.data.logs ?: error("Log payload missing logs")
                val lidList: List<String> = logs.map {
                    it.attributes?.findAttributeValue(LogIncubatingAttributes.LOG_RECORD_UID.key) ?: error("Log missing log id")
                }
                lidList.forEach { lid ->
                    if (logIds.contains(lid)) {
                        error("Duplicate log id detected in request: $lid")
                    }
                    logIds.add(lid)
                }
            }
        }
        attemptedHttpRequests.add(envelope)
    }

    fun sendAttempts() = getRequests<SessionPayload>().size + getRequests<LogPayload>().size
}
