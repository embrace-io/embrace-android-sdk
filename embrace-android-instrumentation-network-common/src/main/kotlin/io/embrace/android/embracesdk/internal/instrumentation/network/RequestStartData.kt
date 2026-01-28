package io.embrace.android.embracesdk.internal.instrumentation.network

data class RequestStartData(
    val url: String,
    val httpMethod: String,
    val sdkClockStartTime: Long,
)
