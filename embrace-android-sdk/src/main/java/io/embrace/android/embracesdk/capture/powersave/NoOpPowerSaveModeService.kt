package io.embrace.android.embracesdk.capture.powersave

import io.embrace.android.embracesdk.payload.PowerModeInterval

internal class NoOpPowerSaveModeService : PowerSaveModeService {

    override fun close() {
    }

    override fun cleanCollections() {
    }

    override fun getCapturedData(): List<PowerModeInterval>? {
        return null
    }
}
