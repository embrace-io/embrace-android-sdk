package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.UnimplementedConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig

interface SdkModeBehavior : ConfigBehavior<UnimplementedConfig, RemoteConfig> {

    /**
     * Given a Config instance, computes if the SDK is enabled based on the threshold and the offset.
     *
     * @return true if the sdk is enabled, false otherwise
     */
    fun isSdkDisabled(): Boolean
}
