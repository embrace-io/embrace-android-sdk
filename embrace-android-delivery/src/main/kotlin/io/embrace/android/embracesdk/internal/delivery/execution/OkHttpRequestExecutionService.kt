package io.embrace.android.embracesdk.internal.delivery.execution

import io.embrace.android.embracesdk.internal.comms.api.ApiRequestV2
import io.embrace.android.embracesdk.internal.comms.api.Endpoint
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import io.embrace.android.embracesdk.internal.delivery.debug.DeliveryTracer
import io.embrace.android.embracesdk.internal.delivery.execution.ExecutionResult.Companion.getResult
import io.embrace.android.embracesdk.internal.delivery.storage.loadAttachment
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import okhttp3.Headers.Companion.toHeaders
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.buffer
import okio.source
import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPInputStream

class OkHttpRequestExecutionService(
    private val okHttpClient: OkHttpClient,
    private val coreBaseUrl: String,
    private val lazyDeviceId: Lazy<String>,
    private val appId: String,
    private val embraceVersionName: String,
    private val logger: EmbLogger,
    private val deliveryTracer: DeliveryTracer? = null,
) : RequestExecutionService {

    private companion object {
        private val mediaType = "application/json".toMediaType()
    }

    override fun attemptHttpRequest(
        payloadStream: () -> InputStream,
        envelopeType: SupportedEnvelopeType,
        payloadType: String,
    ): ExecutionResult {
        val multipart = envelopeType.endpoint == Endpoint.ATTACHMENTS
        val apiRequest = envelopeType.endpoint.getApiRequestFromEndpoint(multipart)
        val request = when {
            multipart -> prepareMultipartRequest(payloadStream, apiRequest)
            else -> prepareRequest(payloadStream, apiRequest, payloadType)
        }

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

    private fun prepareRequest(
        payloadStream: () -> InputStream,
        apiRequest: ApiRequestV2,
        payloadType: String,
    ): Request {
        val request = Request.Builder()
            .url(apiRequest.url)
            .headers(
                apiRequest
                    .getHeaders()
                    .plus("X-EM-TYPES" to payloadType)
                    .toHeaders()
            )
            .post(ApiRequestBody(payloadStream))
            .build()
        return request
    }

    private fun prepareMultipartRequest(
        payloadStream: () -> InputStream,
        apiRequest: ApiRequestV2,
    ): Request {
        GZIPInputStream(payloadStream()).use {
            val attachment = loadAttachment(it) ?: throw IOException("Failed to load attachment")
            val builder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("app_id", checkNotNull(apiRequest.appId))
                .addFormDataPart("attachment_id", attachment.second)
                .addFormDataPart("file", "file", attachment.first.toRequestBody())

            val request = Request.Builder()
                .url(apiRequest.url)
                .post(builder.build())
                .build()
            return request
        }
    }

    private fun Endpoint.getApiRequestFromEndpoint(multipart: Boolean): ApiRequestV2 = ApiRequestV2(
        url = "$coreBaseUrl${this.path}",
        appId = appId,
        deviceId = lazyDeviceId.value,
        contentEncoding = when {
            multipart -> null
            else -> "gzip"
        },
        contentType = when {
            multipart -> "multipart/form-data"
            else -> "application/json"
        },
        userAgent = "Embrace/a/$embraceVersionName"
    )

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
