package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.UnimplementedConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig

interface WebViewVitalsBehavior : ConfigBehavior<UnimplementedConfig, RemoteConfig> {
    fun getMaxWebViewVitals(): Int
    fun isWebViewVitalsEnabled(): Boolean
}
