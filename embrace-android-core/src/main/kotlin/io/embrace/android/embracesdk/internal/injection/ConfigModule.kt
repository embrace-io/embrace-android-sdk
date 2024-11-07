package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.config.RemoteConfigSource

interface ConfigModule {
    val configService: ConfigService
    val remoteConfigSource: RemoteConfigSource
}
