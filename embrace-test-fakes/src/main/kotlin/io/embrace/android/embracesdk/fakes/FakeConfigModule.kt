package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.fakes.behavior.FakeNetworkBehavior
import io.embrace.android.embracesdk.internal.config.ConfigModule
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.config.source.CombinedRemoteConfigSource
import io.embrace.android.embracesdk.internal.config.BuildInfo
import io.embrace.android.embracesdk.internal.config.CpuAbi

class FakeConfigModule(
    override val configService: FakeConfigService = FakeConfigService(
        networkBehavior = FakeNetworkBehavior()
    ),
) : ConfigModule
