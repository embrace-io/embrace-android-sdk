package io.embrace.android.embracesdk.comms.api

internal data class ApiResponse<T>(
    val statusCode: Int?,
    val headers: Map<String, String>,
    val body: T?
)
