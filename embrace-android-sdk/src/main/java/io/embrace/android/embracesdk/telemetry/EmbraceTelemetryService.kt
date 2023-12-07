package io.embrace.android.embracesdk.telemetry

/*
    Service for tracking usage of public APIs, and different internal metrics about the app.
 */
internal class EmbraceTelemetryService {

    val usageCountMap = mutableMapOf<String, Int>()
    fun onPublicApiCalled(name: String) {
        usageCountMap[name] = (usageCountMap[name] ?: 0) + 1
    }

    fun onSessionEnd() {
        usageCountMap.clear()
    }
}