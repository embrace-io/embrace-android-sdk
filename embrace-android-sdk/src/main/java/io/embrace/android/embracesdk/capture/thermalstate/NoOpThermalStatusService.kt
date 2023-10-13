package io.embrace.android.embracesdk.capture.thermalstate

import io.embrace.android.embracesdk.payload.ThermalState

internal class NoOpThermalStatusService : ThermalStatusService {
    override fun close() {}

    override fun cleanCollections() {
    }

    override fun getCapturedData(): List<ThermalState>? {
        return null
    }
}
