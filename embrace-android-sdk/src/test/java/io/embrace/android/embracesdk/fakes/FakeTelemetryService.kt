package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.telemetry.TelemetryService

internal class FakeTelemetryService : TelemetryService {

    val storageTelemetryMap = mutableMapOf<String, String>()
    override fun onPublicApiCalled(name: String) {
        // no-op
    }

    override fun logStorageTelemetry(storageTelemetry: Map<String, String>) {
        this.storageTelemetryMap.putAll(storageTelemetry)
    }

    override fun getAndClearTelemetryAttributes(): Map<String, String> {
        return emptyMap()
    }
}
