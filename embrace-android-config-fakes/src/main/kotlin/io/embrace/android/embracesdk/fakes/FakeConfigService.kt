package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.fakes.behavior.FakeBreadcrumbBehavior
import io.embrace.android.embracesdk.fakes.behavior.FakeNetworkBehavior
import io.embrace.android.embracesdk.internal.config.BuildInfo
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.config.CpuAbi
import io.embrace.android.embracesdk.internal.config.behavior.AppExitInfoBehavior
import io.embrace.android.embracesdk.internal.config.behavior.AutoDataCaptureBehavior
import io.embrace.android.embracesdk.internal.config.behavior.BackgroundActivityBehavior
import io.embrace.android.embracesdk.internal.config.behavior.BreadcrumbBehavior
import io.embrace.android.embracesdk.internal.config.behavior.DataCaptureEventBehavior
import io.embrace.android.embracesdk.internal.config.behavior.LogMessageBehavior
import io.embrace.android.embracesdk.internal.config.behavior.NetworkBehavior
import io.embrace.android.embracesdk.internal.config.behavior.NetworkSpanForwardingBehavior
import io.embrace.android.embracesdk.internal.config.behavior.OtelBehavior
import io.embrace.android.embracesdk.internal.config.behavior.SdkModeBehavior
import io.embrace.android.embracesdk.internal.config.behavior.SensitiveKeysBehavior
import io.embrace.android.embracesdk.internal.config.behavior.SessionBehavior
import io.embrace.android.embracesdk.internal.config.behavior.ThreadBlockageBehavior
import io.embrace.android.embracesdk.internal.payload.AppFramework

/**
 * Fake [io.embrace.android.embracesdk.internal.config.ConfigService] used for testing. Note that the
 * current config values of this object will be propagated, and you can trigger this fake update even if you have not changed the underlying
 * data. Beware of this difference in implementation compared to the real EmbraceConfigService
 */
class FakeConfigService(
    override var appFramework: AppFramework = AppFramework.NATIVE,
    override var appId: String = "abcde",
    var onlyUsingOtelExporters: Boolean = false,
    override var backgroundActivityBehavior: BackgroundActivityBehavior = createBackgroundActivityBehavior(),
    override var autoDataCaptureBehavior: AutoDataCaptureBehavior = createAutoDataCaptureBehavior(),
    override var breadcrumbBehavior: BreadcrumbBehavior = FakeBreadcrumbBehavior(),
    override var logMessageBehavior: LogMessageBehavior = createLogMessageBehavior(),
    override var threadBlockageBehavior: ThreadBlockageBehavior = createThreadBlockageBehavior(),
    override var sessionBehavior: SessionBehavior = createSessionBehavior(),
    override var networkBehavior: NetworkBehavior = FakeNetworkBehavior(),
    override var dataCaptureEventBehavior: DataCaptureEventBehavior = createDataCaptureEventBehavior(),
    override var sdkModeBehavior: SdkModeBehavior = createSdkModeBehavior(),
    override var appExitInfoBehavior: AppExitInfoBehavior = createAppExitInfoBehavior(),
    override var networkSpanForwardingBehavior: NetworkSpanForwardingBehavior = createNetworkSpanForwardingBehavior(),
    override var sensitiveKeysBehavior: SensitiveKeysBehavior = createSensitiveKeysBehavior(),
    override val otelBehavior: OtelBehavior = createOtelBehavior(),
    override var buildInfo: BuildInfo = BuildInfo(
        "fakeBuildId",
        "fakeBuildType",
        "fakeBuildFlavor",
        "fakeRnBundleId",
        "2.5.1",
        "99",
        "com.fake.package",
    ),
    override var deviceId: String = "",
    override val cpuAbi: CpuAbi = CpuAbi.ARM64_V8A,
    override val nativeSymbolMap: Map<String, String>? = emptyMap(),
) : ConfigService {
    override fun isOnlyUsingOtelExporters(): Boolean = onlyUsingOtelExporters
}
