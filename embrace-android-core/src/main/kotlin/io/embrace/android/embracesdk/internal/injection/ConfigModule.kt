package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.capture.metadata.AppEnvironment
import io.embrace.android.embracesdk.internal.comms.api.ApiUrlBuilder
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.config.source.CombinedRemoteConfigSource
import io.embrace.android.embracesdk.internal.config.source.RemoteConfigSource
import io.embrace.android.embracesdk.internal.envelope.BuildInfo
import io.embrace.android.embracesdk.internal.envelope.CpuAbi
import io.embrace.android.embracesdk.internal.envelope.PackageVersionInfo
import okhttp3.OkHttpClient

interface ConfigModule {
    val configService: ConfigService
    val combinedRemoteConfigSource: CombinedRemoteConfigSource?
    val remoteConfigSource: RemoteConfigSource?
    val urlBuilder: ApiUrlBuilder?
    val okHttpClient: OkHttpClient

    val packageVersionInfo: PackageVersionInfo

    /**
     * Whether the application is a debug build
     */
    val appEnvironment: AppEnvironment

    val buildInfo: BuildInfo

    /**
     * The primary CPU architecture. We assume that for the vast majority of devices
     * all CPUs have the same ABI (technically not true, but a reasonable enough for
     * simpler data analysis)
     */
    val cpuAbi: CpuAbi

    /**
     * The current native symbols.
     */
    val nativeSymbolMap: Map<String, String>?
}
