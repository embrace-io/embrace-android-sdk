package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.capture.thermalstate.ThermalStatusService
import io.embrace.android.embracesdk.payload.ThermalState

internal class FakeThermalStatusService : FakeDataCaptureService<ThermalState>(), ThermalStatusService {
    override fun close() {
    }
}
