package io.embrace.android.embracesdk.benchmark

import io.embrace.android.embracesdk.internal.config.BuildInfo
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.config.behavior.BehaviorThresholdCheck
import io.embrace.android.embracesdk.internal.config.behavior.NetworkBehavior
import io.embrace.android.embracesdk.internal.config.behavior.NetworkBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.NetworkSpanForwardingBehavior
import io.embrace.android.embracesdk.internal.config.behavior.NetworkSpanForwardingBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.OtelBehavior
import io.embrace.android.embracesdk.internal.config.behavior.OtelBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.TraceparentInjectionBehavior
import io.embrace.android.embracesdk.internal.config.behavior.TraceparentInjectionBehaviorImpl
import io.embrace.android.embracesdk.internal.config.behavior.UserSessionBehavior
import io.embrace.android.embracesdk.internal.config.behavior.UserSessionBehaviorImpl
import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.resolved.EmbraceConfig
import io.embrace.android.embracesdk.internal.payload.AppFramework

/**
 * A [ConfigService] that resolves every behavior from [remoteConfig] and the default instrumented config.
 */
internal class BenchmarkConfigService(
    thresholdCheck: BehaviorThresholdCheck,
    remoteConfig: RemoteConfig,
) : ConfigService {
    override val config: EmbraceConfig = EmbraceConfig()
    override val sessionBehavior: UserSessionBehavior = UserSessionBehaviorImpl(remoteConfig)
    override val networkBehavior: NetworkBehavior = NetworkBehaviorImpl(InstrumentedConfigImpl, remoteConfig, 0)
    override val traceparentInjectionBehavior: TraceparentInjectionBehavior =
        TraceparentInjectionBehaviorImpl(thresholdCheck, InstrumentedConfigImpl, remoteConfig)
    override val networkSpanForwardingBehavior: NetworkSpanForwardingBehavior =
        NetworkSpanForwardingBehaviorImpl(traceparentInjectionBehavior, thresholdCheck, InstrumentedConfigImpl, remoteConfig)
    override val otelBehavior: OtelBehavior = OtelBehaviorImpl(thresholdCheck, InstrumentedConfigImpl, remoteConfig)
    override val enabledPctRollouts: List<String> = emptyList()
    override val appFramework: AppFramework = AppFramework.NATIVE
    override val appId: String = "abcde"
    override val buildInfo: BuildInfo = BuildInfo(null, null, null, null, "1.0", "1", "io.embrace.benchmark")
    override val deviceId: String = ""
    override val nativeSymbolMap: Map<String, String>? = null

    override fun isOnlyUsingOtelExporters(): Boolean = false

    override fun scheduleConfigRequests() {
    }
}
