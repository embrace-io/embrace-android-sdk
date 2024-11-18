package io.embrace.android.embracesdk.internal.delivery.execution

import io.embrace.android.embracesdk.internal.comms.api.ApiRequestV2
import io.embrace.android.embracesdk.internal.comms.api.Endpoint
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import io.embrace.android.embracesdk.internal.delivery.debug.DeliveryTracer
import io.embrace.android.embracesdk.internal.delivery.execution.ExecutionResult.Companion.getResult
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import okhttp3.Headers.Companion.toHeaders
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.buffer
import okio.source
import java.io.IOException
import java.io.InputStream

class OkHttpRequestExecutionService(
    private val okHttpClient: OkHttpClient,
    private val coreBaseUrl: String,
    private val lazyDeviceId: Lazy<String>,
    private val appId: String,
    private val embraceVersionName: String,
    private val logger: EmbLogger,
    private val deliveryTracer: DeliveryTracer? = null,
) : RequestExecutionService {

    override fun attemptHttpRequest(
        payloadStream: () -> InputStream,
        envelopeType: SupportedEnvelopeType,
        payloadType: String,
    ): ExecutionResult {
        val apiRequest = envelopeType.endpoint.getApiRequestFromEndpoint()
        val requestBody = ApiRequestBody(payloadStream)
        val request = Request.Builder()
            .url(apiRequest.url)
            .headers(
                apiRequest
                    .getHeaders()
                    .plus("X-EM-TYPES" to payloadType)
                    .toHeaders()
            )
            .post(requestBody)
            .build()

        var executionError: Throwable? = null
        val httpCallResponse = try {
            okHttpClient.newCall(request).execute()
        } catch (throwable: Throwable) {
            // IOExceptions are expected during the execution of a network request is expected, so don't log errors
            // for those. But any unexpected error should be logged.
            if (throwable !is IOException) {
                logger.trackInternalError(
                    type = InternalErrorType.PAYLOAD_DELIVERY_FAIL,
                    throwable = throwable
                )
            }
            executionError = throwable
            null
        }

        return getResult(
            endpoint = envelopeType.endpoint,
            responseCode = httpCallResponse?.code,
            headersProvider = { httpCallResponse?.headers?.toMap() ?: emptyMap() },
            executionError = executionError,
        ).apply {
            deliveryTracer?.onHttpCallEnded(this, envelopeType, payloadType)
        }
    }

    private fun Endpoint.getApiRequestFromEndpoint(): ApiRequestV2 = ApiRequestV2(
        url = "$coreBaseUrl${this.path}",
        appId = appId,
        deviceId = lazyDeviceId.value,
        contentEncoding = "gzip",
        userAgent = "Embrace/a/$embraceVersionName"
    )

    private companion object {
        private val mediaType = "application/json".toMediaType()

        class ApiRequestBody(
            private val payloadStream: () -> InputStream,
        ) : RequestBody() {
            override fun contentType() = mediaType

            override fun writeTo(sink: BufferedSink) {
                payloadStream().source().buffer().use { source ->
                    sink.writeAll(source)
                }
            }
        }
    }
}
