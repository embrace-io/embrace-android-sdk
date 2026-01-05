package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.telemetry.AppliedLimitType
import io.embrace.android.embracesdk.internal.telemetry.TelemetryService

class FakeTelemetryService : TelemetryService {

    val storageTelemetryMap: MutableMap<String, String> = mutableMapOf()
    val apiCalls: MutableList<String> = mutableListOf()
    val appliedLimits: MutableList<Pair<String, AppliedLimitType>> = mutableListOf()

    override fun onPublicApiCalled(name: String) {
        apiCalls.add(name)
    }

    override fun logStorageTelemetry(storageTelemetry: Map<String, String>) {
        storageTelemetryMap.putAll(storageTelemetry)
    }

    override fun trackAppliedLimit(telemetryType: String, limitType: AppliedLimitType) {
        appliedLimits.add(telemetryType to limitType)
    }

    override fun getAndClearTelemetryAttributes(): Map<String, String> {
        return emptyMap()
    }
}
