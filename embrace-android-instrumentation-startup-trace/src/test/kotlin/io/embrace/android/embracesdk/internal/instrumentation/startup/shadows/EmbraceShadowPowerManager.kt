package io.embrace.android.embracesdk.internal.instrumentation.startup.shadows

import android.os.Build.VERSION_CODES
import android.os.PowerManager
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowPowerManager

/**
 * Robolectric shadow that extends [ShadowPowerManager] and adds support for thermal headroom, which the default doesn't.
 */
@Implements(PowerManager::class)
class EmbraceShadowPowerManager : ShadowPowerManager() {

    /**
     * Value [PowerManager.getCurrentThermalStatus] answers with, or null to make it throw. Not restricted to the
     * levels the platform defines today, unlike [setCurrentThermalStatus], so you can set invalid values for testings.
     */
    var thermalStatus: Int? = PowerManager.THERMAL_STATUS_NONE

    /**
     * Value [PowerManager.getThermalHeadroom] answers with, or null to make it throw. Defaults to the [Float.NaN] a
     * device that does not forecast reports.
     */
    var thermalHeadroom: Float? = Float.NaN

    @Implementation(minSdk = VERSION_CODES.Q)
    override fun getCurrentThermalStatus(): Int = thermalStatus ?: error(THERMAL_READ_FAILED)

    @Suppress("UnusedParameter")
    @Implementation(minSdk = VERSION_CODES.R)
    fun getThermalHeadroom(forecastSeconds: Int): Float = thermalHeadroom ?: error(THERMAL_READ_FAILED)

    override fun setCurrentThermalStatus(thermalStatus: Int) {
        this.thermalStatus = thermalStatus
    }

    private companion object {
        private const val THERMAL_READ_FAILED = "thermal state cannot be read"
    }
}
