package io.embrace.android.embracesdk.internal.config.behavior

interface SdkModeBehavior {

    /**
     * Given a Config instance, computes if the SDK is enabled based on the threshold and the offset.
     *
     * @return true if the sdk is enabled, false otherwise
     */
    fun isSdkDisabled(): Boolean
}
