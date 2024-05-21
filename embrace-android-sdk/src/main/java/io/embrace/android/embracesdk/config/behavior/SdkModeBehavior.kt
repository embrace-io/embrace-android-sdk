package io.embrace.android.embracesdk.config.behavior

import io.embrace.android.embracesdk.config.local.LocalConfig
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.utils.Provider
import kotlin.math.max
import kotlin.math.min

/**
 * Provides whether the SDK should enable certain 'behavior' modes, such as 'integration mode'
 */
internal class SdkModeBehavior(
    private val isDebug: Boolean,
    thresholdCheck: BehaviorThresholdCheck,
    localSupplier: Provider<LocalConfig?>,
    remoteSupplier: Provider<RemoteConfig?>
) : MergedConfigBehavior<LocalConfig, RemoteConfig>(
    thresholdCheck,
    localSupplier,
    remoteSupplier
) {

    companion object {

        /**
         * The percentage of devices which should have beta features initialized.
         *
         * The range of allowed values is 0.0f to 100.0f, and the default is 1.0f (1% of devices).
         */
        private const val DEFAULT_BETA_FEATURES_PCT = 1.0f

        /**
         * The percentage of devices that defer some expensive service initialization to a background
         * thread to improve startup performance in exchange for delayed enablement of some features of the SDK
         */
        private const val DEFAULT_DEFER_SERVICE_INIT_PCT = 0.0f

        /**
         * The default percentage of devices for which the SDK is enabled.
         */
        private const val DEFAULT_THRESHOLD = 100

        /**
         * The default percentage offset of devices for which the SDK is enabled.
         */
        private const val DEFAULT_OFFSET = 0
    }

    /**
     * Checks if beta features are enabled for this device.
     *
     * @return true if beta features should run for this device, otherwise false.
     */
    fun isBetaFeaturesEnabled(): Boolean {
        if (local?.sdkConfig?.betaFeaturesEnabled == false) {
            return false
        }

        if (isDebug) {
            return true
        }

        val pct = remote?.pctBetaFeaturesEnabled ?: DEFAULT_BETA_FEATURES_PCT
        return thresholdCheck.isBehaviorEnabled(pct)
    }

    /**
     * Checks if an expanded list of services' initialization during startup will be done on a background thread
     */
    fun isServiceInitDeferred(): Boolean {
        val pct = remote?.pctDeferServiceInitEnabled ?: DEFAULT_DEFER_SERVICE_INIT_PCT
        return thresholdCheck.isBehaviorEnabled(pct)
    }

    /**
     * The Embrace app ID. This is used to identify the app within the database.
     */
    val appId: String? by lazy { local?.appId }

    /**
     * The % of devices that should be enabled.
     */
    private fun getThreshold(): Int = remote?.threshold ?: DEFAULT_THRESHOLD

    /**
     * The % at which to start enabling devices.
     */
    private fun getOffset(): Int = remote?.offset ?: DEFAULT_OFFSET

    /**
     * Given a Config instance, computes if the SDK is enabled based on the threshold and the offset.
     *
     * @return true if the sdk is enabled, false otherwise
     */
    fun isSdkDisabled(): Boolean {
        @Suppress("DEPRECATION")
        val result = thresholdCheck.getNormalizedDeviceId()
        // Check if this is lower than the threshold, to determine whether
        // we should enable/disable the SDK.
        val lowerBound = max(0, getOffset())
        val upperBound = min(getOffset() + getThreshold(), 100)
        return (lowerBound == upperBound || result < lowerBound || result > upperBound)
    }
}
