package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.comms.api.ApiUrlBuilder
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.config.source.CombinedRemoteConfigSource
import io.embrace.android.embracesdk.internal.envelope.BuildInfo
import io.embrace.android.embracesdk.internal.envelope.CpuAbi
import okhttp3.OkHttpClient

interface ConfigModule {
    val configService: ConfigService
    val combinedRemoteConfigSource: CombinedRemoteConfigSource?
    val urlBuilder: ApiUrlBuilder?
    val okHttpClient: OkHttpClient

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

    val deviceIdentifier: String
}
