package io.embrace.android.embracesdk.internal.instrumentation.network

class HttpNetworkRequest(
    val url: String,
    val httpMethod: String,
    val startTime: Long,
    val endTime: Long,
    val bytesSent: Long? = null,
    val bytesReceived: Long? = null,
    val statusCode: Int? = null,
    val errorType: String? = null,
    val errorMessage: String? = null,
    val traceId: String? = null,
    val w3cTraceparent: String? = null,
    val body: HttpRequestBody? = null,
) {
    class HttpRequestBody(
        val requestHeaders: Map<String, String>?,
        val requestQueryParams: String?,
        val capturedRequestBody: ByteArray?,
        val responseHeaders: Map<String, String>?,
        val capturedResponseBody: ByteArray?,
        val dataCaptureErrorMessage: String?,
    ) {
        val requestBodySize: Int
            get() = capturedRequestBody?.size ?: 0

        val responseBodySize: Int
            get() = capturedResponseBody?.size ?: 0
    }
}
