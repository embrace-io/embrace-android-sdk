package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.capture.strictmode.StrictModeService
import io.embrace.android.embracesdk.payload.StrictModeViolation

internal class FakeStrictModeService : FakeDataCaptureService<StrictModeViolation>(), StrictModeService {

    override fun start() {
    }
}
