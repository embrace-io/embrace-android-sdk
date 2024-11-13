package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.comms.api.ApiUrlBuilder
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.config.source.RemoteConfigSource
import io.embrace.android.embracesdk.internal.config.store.RemoteConfigStore

interface ConfigModule {
    val configService: ConfigService
    val remoteConfigSource: RemoteConfigSource?
    val remoteConfigStore: RemoteConfigStore
    val urlBuilder: ApiUrlBuilder?
}
