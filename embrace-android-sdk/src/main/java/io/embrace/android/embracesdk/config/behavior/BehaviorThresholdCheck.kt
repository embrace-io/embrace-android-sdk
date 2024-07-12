package io.embrace.android.embracesdk.config.behavior

import io.embrace.android.embracesdk.annotation.InternalApi
import io.embrace.android.embracesdk.internal.utils.Provider
import kotlin.math.pow

/**
 * Checks whether a percent-based config value is over a threshold where it should be enabled.
 */
@InternalApi
public class BehaviorThresholdCheck(
    private val deviceIdProvider: Provider<String>
) {

    /**
     * An implementation of [isBehaviorEnabled] that returns null if the pctEnabled parameter
     * is null.
     */
    public fun isBehaviorEnabled(pctEnabled: Float?): Boolean? = pctEnabled?.let(::isBehaviorEnabled)

    /**
     * An implementation of [isBehaviorEnabled] that returns null if the pctEnabled parameter
     * is null.
     */
    public fun isBehaviorEnabled(pctEnabled: Int?): Boolean? = pctEnabled?.toFloat().let(::isBehaviorEnabled)

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
    public fun isBehaviorEnabled(pctEnabled: Float): Boolean {
        if (pctEnabled <= 0 || pctEnabled > 100) {
            return false
        }
        val deviceId = getNormalizedLargeDeviceId()
        return pctEnabled >= deviceId
    }

    public fun getNormalizedLargeDeviceId(): Float = getNormalizedDeviceId(6)

    /**
     * Use [.isBehaviorEnabled] instead as it allows rollouts to be controlled
     * at greater granularity.
     */
    @Deprecated("")
    public fun getNormalizedDeviceId(): Float = getNormalizedDeviceId(2)

    private fun getNormalizedDeviceId(digits: Int): Float {
        val deviceId = deviceIdProvider()
        val finalChars = deviceId.substring(deviceId.length - digits)

        // Normalize the device ID to a value between 0.0 - 100.0
        val radix = 16
        val space = (radix.toDouble().pow(digits.toDouble()) - 1).toInt()
        val value = Integer.valueOf(finalChars, radix)
        return value.toFloat() / space * 100
    }
}
