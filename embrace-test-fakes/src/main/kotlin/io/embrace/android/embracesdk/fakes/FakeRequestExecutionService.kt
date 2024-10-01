package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.comms.api.ApiResponse
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import io.embrace.android.embracesdk.internal.delivery.execution.RequestExecutionService
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import java.io.InputStream

class FakeRequestExecutionService : RequestExecutionService {

    private val serializer = TestPlatformSerializer()
    var constantResponse: ApiResponse = ApiResponse.None
    var responseAction: (intake: Envelope<*>) -> ApiResponse = { _ -> constantResponse }
    var exceptionOnExecution: Throwable? = null
    val attemptedHttpRequests = mutableListOf<Envelope<*>>()

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : Any> getRequests(): List<Envelope<T>> {
        if (T::class != SessionPayload::class && T::class != LogPayload::class) {
            error("Unsupported type: ${T::class}")
        }
        return attemptedHttpRequests.filter { it.data is T } as List<Envelope<T>>
    }

    override fun attemptHttpRequest(
        payloadStream: () -> InputStream,
        envelopeType: SupportedEnvelopeType
    ): ApiResponse {
        exceptionOnExecution?.run { throw this }
        val bufferedStream = payloadStream()
        val json: Envelope<*> = serializer.fromJson(bufferedStream, envelopeType.serializedType)
        attemptedHttpRequests.add(json)
        return responseAction(json)
    }

    fun sendAttempts() = getRequests<SessionPayload>().size + getRequests<LogPayload>().size
}
