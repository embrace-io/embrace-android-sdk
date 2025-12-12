package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.fakes.behavior.FakeNetworkBehavior
import io.embrace.android.embracesdk.internal.capture.metadata.AppEnvironment
import io.embrace.android.embracesdk.internal.comms.api.ApiUrlBuilder
import io.embrace.android.embracesdk.internal.comms.api.Endpoint
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.config.source.CombinedRemoteConfigSource
import io.embrace.android.embracesdk.internal.envelope.BuildInfo
import io.embrace.android.embracesdk.internal.envelope.CpuAbi
import io.embrace.android.embracesdk.internal.injection.ConfigModule
import okhttp3.OkHttpClient

class FakeConfigModule(
    override val configService: ConfigService = FakeConfigService(
        networkBehavior = FakeNetworkBehavior()
    ),
    override val combinedRemoteConfigSource: CombinedRemoteConfigSource? = null,

    override val urlBuilder: ApiUrlBuilder = FakeApiUrlBuilder(),

    override val appEnvironment: AppEnvironment = AppEnvironment(true),
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
) : ConfigModule {

    override val okHttpClient: OkHttpClient by lazy { OkHttpClient() }
}

private class FakeApiUrlBuilder(
    override val appId: String = "",
    override val deviceId: String = "",
    override val baseDataUrl: String = "",
) : ApiUrlBuilder {
    override fun resolveUrl(endpoint: Endpoint): String = ""
}
