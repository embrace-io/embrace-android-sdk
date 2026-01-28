package io.embrace.android.embracesdk.internal.instrumentation.network

data class RequestEndData(
    val id: String,
    val url: String,
    val sdkClockStartTime: Long,
    val sdkClockEndTime: Long,
    val statusCode: Int?,
    val bytesSent: Long? = null,
    val bytesReceived: Long? = null,
    val errorType: String? = null,
    val errorMessage: String? = null,
    val traceId: String? = null,
)
