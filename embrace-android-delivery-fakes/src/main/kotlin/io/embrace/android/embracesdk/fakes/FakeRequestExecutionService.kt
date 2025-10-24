package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import io.embrace.android.embracesdk.internal.delivery.execution.ExecutionResult
import io.embrace.android.embracesdk.internal.delivery.execution.RequestExecutionService
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import java.io.InputStream
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.zip.GZIPInputStream

class FakeRequestExecutionService : RequestExecutionService {

    private val serializer = TestPlatformSerializer()
    var constantResponse: ExecutionResult = ExecutionResult.Success
    var responseAction: (intake: Envelope<*>) -> ExecutionResult = { _ -> constantResponse }
    var exceptionOnExecution: Throwable? = null
    val attemptedHttpRequests = ConcurrentLinkedQueue<Envelope<*>>()

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
        val envelope: Envelope<*> = serializer.fromJson(bufferedStream, checkNotNull(envelopeType.serializedType))
        attemptedHttpRequests.add(envelope)
        return responseAction(envelope)
    }

    fun sendAttempts() = getRequests<SessionPayload>().size + getRequests<LogPayload>().size
}
