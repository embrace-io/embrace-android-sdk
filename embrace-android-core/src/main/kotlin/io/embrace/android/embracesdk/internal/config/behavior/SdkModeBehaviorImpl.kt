package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.UnimplementedConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import kotlin.math.max
import kotlin.math.min

/**
 * Provides whether the SDK should enable certain 'behavior' modes, such as 'integration mode'
 */
class SdkModeBehaviorImpl(
    private val thresholdCheck: BehaviorThresholdCheck,
    override val remote: RemoteConfig?,
) : SdkModeBehavior {

    private companion object {

        /**
         * The default percentage of devices for which the SDK is enabled.
         */
        private const val DEFAULT_THRESHOLD = 100

        /**
         * The default percentage offset of devices for which the SDK is enabled.
         */
        private const val DEFAULT_OFFSET = 0
    }

    override val local: UnimplementedConfig = null

    /**
     * The % of devices that should be enabled.
     */
    private fun getThreshold(): Int = remote?.threshold ?: DEFAULT_THRESHOLD

    /**
     * The % at which to start enabling devices.
     */
    private fun getOffset(): Int = remote?.offset ?: DEFAULT_OFFSET

    override fun isSdkDisabled(): Boolean {
        @Suppress("DEPRECATION")
        val result = thresholdCheck.getNormalizedDeviceId()
        // Check if this is lower than the threshold, to determine whether
        // we should enable/disable the SDK.
        val lowerBound = max(0, getOffset())
        val upperBound = min(getOffset() + getThreshold(), 100)
        return (lowerBound == upperBound || result < lowerBound || result > upperBound)
    }
}
