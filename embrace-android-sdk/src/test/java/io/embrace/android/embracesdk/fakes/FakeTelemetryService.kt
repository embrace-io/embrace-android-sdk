package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.telemetry.TelemetryService

internal class FakeTelemetryService : TelemetryService {

    val storageTelemetryMap = mutableMapOf<String, String>()
    val apiCalls: MutableList<String> = mutableListOf()

    override fun onPublicApiCalled(name: String) {
        apiCalls.add(name)
    }

    override fun logStorageTelemetry(storageTelemetry: Map<String, String>) {
        storageTelemetryMap.putAll(storageTelemetry)
    }

    override fun getAndClearTelemetryAttributes(): Map<String, String> {
        return emptyMap()
    }
}
