package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.UnimplementedConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig

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
    }

    override val local: UnimplementedConfig = null

    /**
     * The % of devices that should be enabled.
     */
    private fun getThreshold(): Int = remote?.threshold ?: DEFAULT_THRESHOLD

    override fun isSdkDisabled(): Boolean {
        return !thresholdCheck.isBehaviorEnabled(getThreshold().toFloat())
    }
}
