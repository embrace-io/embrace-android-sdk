package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.config.UnimplementedConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig

interface DataCaptureEventBehavior : ConfigBehavior<UnimplementedConfig, RemoteConfig> {
    fun isInternalExceptionCaptureEnabled(): Boolean
    fun isEventEnabled(eventName: String): Boolean
    fun isLogMessageEnabled(logMessage: String): Boolean
}
