package io.embrace.android.embracesdk.internal.config

import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig

/**
 * Accessors for the config service that should only be used by the hybrid SDKs.
 */
interface HybridSdkConfigService {
    val remoteConfig: RemoteConfig?
    fun isBehaviorEnabled(pctEnabled: Float?): Boolean?
}
