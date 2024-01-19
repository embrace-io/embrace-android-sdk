package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.telemetry.TelemetryService

internal class FakeTelemetryService : TelemetryService {

    val storageTelemetryMap = mutableMapOf<String, Int>()
    override fun onPublicApiCalled(name: String) {
        // no-op
    }

    override fun logStorageTelemetry(fileToSizeMap: Map<String, Int>) {
        this.storageTelemetryMap.putAll(fileToSizeMap)
    }

    override fun getAndClearTelemetryAttributes(): Map<String, String> {
        return emptyMap()
    }
}
