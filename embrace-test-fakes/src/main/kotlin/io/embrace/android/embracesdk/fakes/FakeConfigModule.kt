package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.fakes.behavior.FakeNetworkBehavior
import io.embrace.android.embracesdk.internal.config.ConfigModule
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.config.source.CombinedRemoteConfigSource
import io.embrace.android.embracesdk.internal.envelope.BuildInfo
import io.embrace.android.embracesdk.internal.envelope.CpuAbi

class FakeConfigModule(
    override val configService: ConfigService = FakeConfigService(
        networkBehavior = FakeNetworkBehavior()
    ),
    override val combinedRemoteConfigSource: CombinedRemoteConfigSource? = null,

    override val buildInfo: BuildInfo = BuildInfo(
        "fakeBuildId",
        "fakeBuildType",
        "fakeBuildFlavor",
        "fakeRnBundleId",
        "2.5.1",
        "99",
        "com.fake.package",
    ),
    override val cpuAbi: CpuAbi = CpuAbi.ARM64_V8A,
    override val nativeSymbolMap: Map<String, String>? = emptyMap(),
    override var deviceIdentifier: String = "",
) : ConfigModule
