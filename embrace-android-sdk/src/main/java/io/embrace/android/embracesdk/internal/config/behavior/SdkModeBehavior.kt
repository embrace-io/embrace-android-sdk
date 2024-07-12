package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.annotation.InternalApi

@InternalApi
public interface SdkModeBehavior {

    /**
     * Checks if beta features are enabled for this device.
     *
     * @return true if beta features should run for this device, otherwise false.
     */
    public fun isBetaFeaturesEnabled(): Boolean

    /**
     * Checks if an expanded list of services' initialization during startup will be done on a background thread
     */
    public fun isServiceInitDeferred(): Boolean

    /**
     * The Embrace app ID. This is used to identify the app within the database.
     */
    public val appId: String?

    /**
     * Given a Config instance, computes if the SDK is enabled based on the threshold and the offset.
     *
     * @return true if the sdk is enabled, false otherwise
     */
    public fun isSdkDisabled(): Boolean
}
