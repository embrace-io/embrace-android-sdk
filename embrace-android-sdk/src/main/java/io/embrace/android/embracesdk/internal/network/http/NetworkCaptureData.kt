package io.embrace.android.embracesdk.internal.network.http

import io.embrace.android.embracesdk.InternalApi

/**
 * The additional data captured if network body capture is enabled for the URL
 */
@InternalApi
public data class NetworkCaptureData(
    val requestHeaders: Map<String, String>?,
    val requestQueryParams: String?,
    val capturedRequestBody: ByteArray?,
    val responseHeaders: Map<String, String>?,
    val capturedResponseBody: ByteArray?,
    val dataCaptureErrorMessage: String? = null
) {
    val requestBodySize: Int
        get() = capturedRequestBody?.size ?: 0

    val responseBodySize: Int
        get() = capturedResponseBody?.size ?: 0
}
