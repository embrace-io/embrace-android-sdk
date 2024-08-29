package io.embrace.android.embracesdk.internal.comms.api

public enum class Endpoint(
    public val path: String,
    public val version: String
) {
    EVENTS("events", "v1"),
    LOGS("logs", "v2"),
    SESSIONS("sessions", "v1"),
    SESSIONS_V2("spans", "v2"),
    UNKNOWN("unknown", "v1")
}
