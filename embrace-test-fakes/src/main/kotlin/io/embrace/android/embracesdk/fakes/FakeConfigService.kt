package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.fakes.behavior.FakeBreadcrumbBehavior
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.config.behavior.AnrBehavior
import io.embrace.android.embracesdk.internal.config.behavior.AppExitInfoBehavior
import io.embrace.android.embracesdk.internal.config.behavior.AutoDataCaptureBehavior
import io.embrace.android.embracesdk.internal.config.behavior.BackgroundActivityBehavior
import io.embrace.android.embracesdk.internal.config.behavior.BreadcrumbBehavior
import io.embrace.android.embracesdk.internal.config.behavior.DataCaptureEventBehavior
import io.embrace.android.embracesdk.internal.config.behavior.LogMessageBehavior
import io.embrace.android.embracesdk.internal.config.behavior.NetworkBehavior
import io.embrace.android.embracesdk.internal.config.behavior.NetworkSpanForwardingBehavior
import io.embrace.android.embracesdk.internal.config.behavior.SdkModeBehavior
import io.embrace.android.embracesdk.internal.config.behavior.SensitiveKeysBehavior
import io.embrace.android.embracesdk.internal.config.behavior.SessionBehavior
import io.embrace.android.embracesdk.internal.config.behavior.WebViewVitalsBehavior
import io.embrace.android.embracesdk.internal.payload.AppFramework

/**
 * Fake [ConfigService] used for testing. Note that the
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
    override var anrBehavior: AnrBehavior = createAnrBehavior(),
    override var sessionBehavior: SessionBehavior = createSessionBehavior(),
    override var networkBehavior: NetworkBehavior = createNetworkBehavior(),
    override var dataCaptureEventBehavior: DataCaptureEventBehavior = createDataCaptureEventBehavior(),
    override var sdkModeBehavior: SdkModeBehavior = createSdkModeBehavior(),
    override var webViewVitalsBehavior: WebViewVitalsBehavior = createWebViewVitalsBehavior(),
    override var appExitInfoBehavior: AppExitInfoBehavior = createAppExitInfoBehavior(),
    override var networkSpanForwardingBehavior: NetworkSpanForwardingBehavior = createNetworkSpanForwardingBehavior(),
    override var sensitiveKeysBehavior: SensitiveKeysBehavior = createSensitiveKeysBehavior(),
) : ConfigService {
    override fun isOnlyUsingOtelExporters(): Boolean = onlyUsingOtelExporters
}
