package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.telemetry.TelemetryService

internal class FakeTelemetryService : TelemetryService {
    override fun onPublicApiCalled(name: String) {
        // no-op
    }

    override fun getAndClearTelemetryAttributes(): Map<String, String> {
        return emptyMap()
    }
}
