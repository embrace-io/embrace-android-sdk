package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.capture.powersave.PowerSaveModeService
import io.embrace.android.embracesdk.payload.PowerModeInterval

internal class FakePowerSaveModeService : FakeDataCaptureService<PowerModeInterval>(), PowerSaveModeService {

    override fun close() {
    }
}
