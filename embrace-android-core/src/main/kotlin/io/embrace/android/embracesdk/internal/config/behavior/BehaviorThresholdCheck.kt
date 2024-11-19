package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.utils.Provider
import kotlin.math.pow

/**
 * Checks whether a percent-based config value is over a threshold where it should be enabled.
 */
class BehaviorThresholdCheck(
    private val deviceIdProvider: Provider<String>,
) {

    private companion object {
        private const val DIGITS = 6
    }

    /**
     * An implementation of [isBehaviorEnabled] that returns null if the pctEnabled parameter
     * is null.
     */
    fun isBehaviorEnabled(pctEnabled: Float?): Boolean? = pctEnabled?.let(::isBehaviorEnabled)

    /**
     * An implementation of [isBehaviorEnabled] that returns null if the pctEnabled parameter
     * is null.
     */
    fun isBehaviorEnabled(pctEnabled: Int?): Boolean? = pctEnabled?.toFloat().let(::isBehaviorEnabled)

    /**
     * Determines whether behaviour is enabled for a percentage roll-out. This is achieved
     * by taking a normalized hex value from the last 6 digits of the device ID, and comparing
     * it against the enabled percentage. This ensures that devices are consistently in a given
     * group for beta functionality.
     *
     *
     * The normalized device ID has 16^6 possibilities (roughly 1.6m) which should be sufficient
     * granularity for our needs.
     *
     * @param pctEnabled the % enabled for a given config value. This should be a float rather than
     * an integer for maximum granularity.
     * @return whether the behaviour is enabled or not.
     */
    fun isBehaviorEnabled(pctEnabled: Float): Boolean {
        if (pctEnabled <= 0 || pctEnabled > 100) {
            return false
        }
        val deviceId = getNormalizedDeviceId()
        return pctEnabled >= deviceId
    }

    fun getNormalizedDeviceId(): Float {
        val deviceId = deviceIdProvider()
        val finalChars = deviceId.substring(deviceId.length - DIGITS)
        // Normalize the device ID to a value between 0.0 - 100.0
        val radix = 16
        val space = (radix.toDouble().pow(DIGITS.toDouble()) - 1).toInt()
        val value = Integer.valueOf(finalChars, radix)
        return value.toFloat() / space * 100
    }
}
