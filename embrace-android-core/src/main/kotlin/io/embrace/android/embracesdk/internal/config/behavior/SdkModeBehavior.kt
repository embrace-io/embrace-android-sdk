package io.embrace.android.embracesdk.internal.config.behavior

public interface SdkModeBehavior {

    /**
     * Checks if beta features are enabled for this device.
     *
     * @return true if beta features should run for this device, otherwise false.
     */
    public fun isBetaFeaturesEnabled(): Boolean

    /**
     * Given a Config instance, computes if the SDK is enabled based on the threshold and the offset.
     *
     * @return true if the sdk is enabled, false otherwise
     */
    public fun isSdkDisabled(): Boolean
}
