package io.embrace.android.embracesdk.comms.api

internal enum class Endpoint(val path: String) {
    EVENTS("events"),
    BLOBS("blobs"),
    LOGGING("logging"),
    NETWORK("network"),
    SESSIONS("sessions"),
    UNKNOWN("unknown");

    var isRateLimited = false
        private set

    var rateLimitRetryCount = 0
        private set

    fun setRateLimited() {
        isRateLimited = true
        rateLimitRetryCount++
    }

    fun clearRateLimit() {
        isRateLimited = false
        rateLimitRetryCount = 0
    }
}
