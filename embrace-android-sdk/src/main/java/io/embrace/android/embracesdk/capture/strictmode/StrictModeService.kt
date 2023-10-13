package io.embrace.android.embracesdk.capture.strictmode

import io.embrace.android.embracesdk.arch.DataCaptureService
import io.embrace.android.embracesdk.payload.StrictModeViolation

internal interface StrictModeService : DataCaptureService<List<StrictModeViolation>?> {
    fun start()
}
