package io.embrace.android.embracesdk.internal.config.behavior

/**
 * Checks whether a percent-based config value is over a threshold where it should be enabled.
 */
class BehaviorThresholdCheck(
    private val deviceIdProvider: () -> String,
) {

    private companion object {
        private const val DIGITS = 6
        private const val RADIX = 16

        /**
         * The number of distinct values the normalized device ID can take, i.e. 16^6 - 1. That's
         * roughly 1.6m possibilities, which is sufficient granularity for our needs.
         */
        private const val SPACE = 0xFFFFFF
    }

    /**
     * The device ID is fixed for the lifetime of the process, so its normalized value is too.
     * Resolving it can hit the [io.embrace.android.embracesdk.internal.store.KeyValueStore], so this
     * is deliberately lazy - a threshold that short-circuits in [isBehaviorEnabled] never pays that
     * cost, and everything else pays it at most once.
     */
    private val normalizedId: Float by lazy {
        val deviceId = deviceIdProvider()
        val finalChars = deviceId.substring(deviceId.length - DIGITS)
        // Normalize the device ID to a value between 0.0 - 100.0
        finalChars.toInt(RADIX).toFloat() / SPACE * 100
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
     * @param pctEnabled the % enabled for a given config value. This should be a float rather than
     * an integer for maximum granularity.
     * @return whether the behaviour is enabled or not.
     */
    fun isBehaviorEnabled(pctEnabled: Float): Boolean {
        if (pctEnabled <= 0 || pctEnabled > 100) {
            return false
        }
        if (pctEnabled == 100f) {
            return true
        }
        val deviceId = getNormalizedDeviceId()
        return pctEnabled >= deviceId
    }

    fun getNormalizedDeviceId(): Float = normalizedId
}
