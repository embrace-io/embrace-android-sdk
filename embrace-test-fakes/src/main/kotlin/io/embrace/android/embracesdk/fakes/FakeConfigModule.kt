package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.fakes.behavior.FakeNetworkBehavior
import io.embrace.android.embracesdk.internal.comms.api.ApiUrlBuilder
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.config.source.RemoteConfigSource
import io.embrace.android.embracesdk.internal.injection.ConfigModule

class FakeConfigModule(
    override val configService: ConfigService = FakeConfigService(
        networkBehavior = FakeNetworkBehavior(
            captureHttpUrlConnectionRequests = false
        )
    ),
    override val remoteConfigSource: RemoteConfigSource = FakeRemoteConfigSource(),
    override val remoteConfigStore: FakeRemoteConfigStore = FakeRemoteConfigStore(),
    override val urlBuilder: ApiUrlBuilder = FakeApiUrlBuilder(),
) : ConfigModule
