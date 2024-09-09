package io.embrace.android.embracesdk.internal.comms.api

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
enum class Endpoint(
    val path: String,
    val version: String
) {
    EVENTS("events", "v1"),
    LOGS("logs", "v2"),
    SESSIONS("sessions", "v1"),
    SESSIONS_V2("spans", "v2"),
    UNKNOWN("unknown", "v1")
}
