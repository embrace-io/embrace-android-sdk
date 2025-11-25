package io.embrace.android.embracesdk.fakes

import android.content.pm.PackageInfo
import io.embrace.android.embracesdk.fakes.behavior.FakeNetworkBehavior
import io.embrace.android.embracesdk.internal.capture.metadata.AppEnvironment
import io.embrace.android.embracesdk.internal.comms.api.ApiUrlBuilder
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.config.source.CombinedRemoteConfigSource
import io.embrace.android.embracesdk.internal.config.source.RemoteConfigSource
import io.embrace.android.embracesdk.internal.envelope.BuildInfo
import io.embrace.android.embracesdk.internal.envelope.CpuAbi
import io.embrace.android.embracesdk.internal.envelope.PackageVersionInfo
import io.embrace.android.embracesdk.internal.injection.ConfigModule
import okhttp3.OkHttpClient

class FakeConfigModule(
    override val configService: ConfigService = FakeConfigService(
        networkBehavior = FakeNetworkBehavior()
    ),
    override val combinedRemoteConfigSource: CombinedRemoteConfigSource? = null,

    override val remoteConfigSource: RemoteConfigSource = FakeRemoteConfigSource(),
    override val urlBuilder: ApiUrlBuilder = FakeApiUrlBuilder(),

    override val appEnvironment: AppEnvironment = AppEnvironment(true),
    override val buildInfo: BuildInfo = BuildInfo(
        "fakeBuildId",
        "fakeBuildType",
        "fakeBuildFlavor",
        "fakeRnBundleId",
    ),
    override val packageVersionInfo: PackageVersionInfo = fakePackageVersionInfo,
    override val cpuAbi: CpuAbi = CpuAbi.ARM64_V8A,
    override val nativeSymbolMap: Map<String, String>? = emptyMap(),
) : ConfigModule {

    override val okHttpClient: OkHttpClient by lazy { OkHttpClient() }

    companion object {

        @Suppress("DEPRECATION")
        private val fakePackageInfo = PackageInfo().apply {
            packageName = "com.fake.package"
            versionName = "2.5.1"
            versionCode = 99
        }

        private val fakePackageVersionInfo = PackageVersionInfo(fakePackageInfo)
    }
}
