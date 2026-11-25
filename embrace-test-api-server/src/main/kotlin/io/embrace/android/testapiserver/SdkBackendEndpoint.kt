package io.embrace.android.testapiserver

/**
 * The endpoints of Embrace's server backend that the SDK calls, each by the HTTP method it calls it with and the last
 * segment of its path.
 */
enum class SdkBackendEndpoint(
    val method: String,
    val pathSegment: String,
) {
    CONFIG("GET", "config"),
    SESSIONS("POST", "spans"),
    LOGS("POST", "logs"),
    ATTACHMENTS("POST", "attachments"),
}
