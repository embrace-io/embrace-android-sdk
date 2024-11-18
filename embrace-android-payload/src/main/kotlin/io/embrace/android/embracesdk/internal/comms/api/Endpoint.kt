package io.embrace.android.embracesdk.internal.comms.api

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
enum class Endpoint(
    val path: String,
    val version: String,
) {
    LOGS("logs", "v2"),
    SESSIONS("spans", "v2"),
    CONFIG("config", "v2"),
    UNKNOWN("unknown", "v1"),
}
