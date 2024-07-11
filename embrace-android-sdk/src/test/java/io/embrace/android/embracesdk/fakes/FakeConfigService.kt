package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.config.behavior.AnrBehavior
import io.embrace.android.embracesdk.config.behavior.AppExitInfoBehavior
import io.embrace.android.embracesdk.config.behavior.AutoDataCaptureBehavior
import io.embrace.android.embracesdk.config.behavior.BackgroundActivityBehavior
import io.embrace.android.embracesdk.config.behavior.BreadcrumbBehavior
import io.embrace.android.embracesdk.config.behavior.DataCaptureEventBehavior
import io.embrace.android.embracesdk.config.behavior.LogMessageBehavior
import io.embrace.android.embracesdk.config.behavior.NetworkBehavior
import io.embrace.android.embracesdk.config.behavior.NetworkSpanForwardingBehavior
import io.embrace.android.embracesdk.config.behavior.SdkEndpointBehavior
import io.embrace.android.embracesdk.config.behavior.SdkModeBehavior
import io.embrace.android.embracesdk.config.behavior.SessionBehavior
import io.embrace.android.embracesdk.config.behavior.StartupBehavior
import io.embrace.android.embracesdk.config.behavior.WebViewVitalsBehavior
import io.embrace.android.embracesdk.internal.payload.AppFramework

/**
 * Fake [ConfigService] used for testing. Updates to registered listeners can be triggered by calling [updateListeners]. Note that the
 * current config values of this object will be propagated, and you can trigger this fake update even if you have not changed the underlying
 * data. Beware of this difference in implementation compared to the real EmbraceConfigService
 */
internal class FakeConfigService(
    override var appFramework: AppFramework = AppFramework.NATIVE,
    var sdkDisabled: Boolean = false,
    var backgroundActivityCaptureEnabled: Boolean = false,
    private var hasValidRemoteConfig: Boolean = false,
    override var backgroundActivityBehavior: BackgroundActivityBehavior = fakeBackgroundActivityBehavior(),
    override var autoDataCaptureBehavior: AutoDataCaptureBehavior = fakeAutoDataCaptureBehavior(),
    override var breadcrumbBehavior: BreadcrumbBehavior = fakeBreadcrumbBehavior(),
    override var logMessageBehavior: LogMessageBehavior = fakeLogMessageBehavior(),
    override var anrBehavior: AnrBehavior = fakeAnrBehavior(),
    override var sessionBehavior: SessionBehavior = fakeSessionBehavior(),
    override var networkBehavior: NetworkBehavior = fakeNetworkBehavior(),
    override var startupBehavior: StartupBehavior = fakeStartupBehavior(),
    override var dataCaptureEventBehavior: DataCaptureEventBehavior = fakeDataCaptureEventBehavior(),
    override var sdkModeBehavior: SdkModeBehavior = fakeSdkModeBehavior(),
    override var sdkEndpointBehavior: SdkEndpointBehavior = fakeSdkEndpointBehavior(),
    override var webViewVitalsBehavior: WebViewVitalsBehavior = fakeWebViewVitalsBehavior(),
    override var appExitInfoBehavior: AppExitInfoBehavior = fakeAppExitInfoBehavior(),
    override var networkSpanForwardingBehavior: NetworkSpanForwardingBehavior = fakeNetworkSpanForwardingBehavior()
) : ConfigService {

    val listeners = mutableSetOf<() -> Unit>()
    override fun addListener(configListener: () -> Unit) {
        listeners.add(configListener)
    }

    override fun isSdkDisabled(): Boolean = sdkDisabled

    override fun isBackgroundActivityCaptureEnabled() = backgroundActivityCaptureEnabled

    override fun hasValidRemoteConfig(): Boolean = hasValidRemoteConfig
    override fun isAppExitInfoCaptureEnabled(): Boolean = appExitInfoBehavior.isEnabled()

    override fun close() {}

    fun updateListeners() {
        listeners.forEach {
            it()
        }
    }
}
