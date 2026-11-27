package io.embrace.android.embracesdk.internal.network

import io.embrace.android.embracesdk.internal.network.http.NetworkCaptureData

/**
 * A network request that the app recorded manually.
 */
internal class EmbraceNetworkRequest(
    val url: String,
    val httpMethod: String,
    val startTime: Long,
    val endTime: Long,
    val bytesSent: Long?,
    val bytesReceived: Long?,
    val responseCode: Int?,
    val errorType: String?,
    val errorMessage: String?,
    val traceId: String?,
    val w3cTraceparent: String?,
    val networkCaptureData: NetworkCaptureData?,
)
