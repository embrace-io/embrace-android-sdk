package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.telemetry.TelemetryService

class FakeTelemetryService : TelemetryService {

    val storageTelemetryMap: MutableMap<String, String> = mutableMapOf()
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
