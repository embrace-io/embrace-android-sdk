package io.embrace.android.embracesdk.capture.strictmode

import io.embrace.android.embracesdk.payload.StrictModeViolation

internal class NoOpStrictModeService : StrictModeService {
    override fun start() {}
    override fun cleanCollections() {}

    override fun getCapturedData(): List<StrictModeViolation>? {
        return null
    }
}
