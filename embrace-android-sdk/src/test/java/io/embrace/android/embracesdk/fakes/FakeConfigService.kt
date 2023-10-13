package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.config.ConfigListener
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
import io.embrace.android.embracesdk.config.behavior.SpansBehavior
import io.embrace.android.embracesdk.config.behavior.StartupBehavior
import io.embrace.android.embracesdk.config.behavior.WebViewVitalsBehavior

/**
 * Fake [ConfigService] used for testing. Updates to registered listeners can be triggered by calling [updateListeners]. Note that the
 * current config values of this object will be propagated, and you can trigger this fake update even if you have not changed the underlying
 * data. Beware of this difference in implementation compared to the real EmbraceConfigService
 */
internal class FakeConfigService(
    var sdkDisabled: Boolean = false,
    private val backgroundActivityCaptureEnabled: Boolean = false,
    private val hasValidRemoteConfig: Boolean = false,
    override val backgroundActivityBehavior: BackgroundActivityBehavior = fakeBackgroundActivityBehavior(),
    override val autoDataCaptureBehavior: AutoDataCaptureBehavior = fakeAutoDataCaptureBehavior(),
    override val breadcrumbBehavior: BreadcrumbBehavior = fakeBreadcrumbBehavior(),
    override val logMessageBehavior: LogMessageBehavior = fakeLogMessageBehavior(),
    override val anrBehavior: AnrBehavior = fakeAnrBehavior(),
    override val sessionBehavior: SessionBehavior = fakeSessionBehavior(),
    override val networkBehavior: NetworkBehavior = fakeNetworkBehavior(),
    override val spansBehavior: SpansBehavior = fakeSpansBehavior(),
    override val startupBehavior: StartupBehavior = fakeStartupBehavior(),
    override val dataCaptureEventBehavior: DataCaptureEventBehavior = fakeDataCaptureEventBehavior(),
    override val sdkModeBehavior: SdkModeBehavior = fakeSdkModeBehavior(),
    override val sdkEndpointBehavior: SdkEndpointBehavior = fakeSdkEndpointBehavior(),
    override val webViewVitalsBehavior: WebViewVitalsBehavior = fakeWebViewVitalsBehavior(),
    override val appExitInfoBehavior: AppExitInfoBehavior = fakeAppExitInfoBehavior(),
    override val networkSpanForwardingBehavior: NetworkSpanForwardingBehavior = fakeNetworkSpanForwardingBehavior(),
) : ConfigService {

    val listeners = mutableSetOf<ConfigListener>()

    override fun addListener(configListener: ConfigListener) {
        listeners.add(configListener)
    }

    override fun isSdkDisabled(): Boolean = sdkDisabled

    override fun isBackgroundActivityCaptureEnabled() = backgroundActivityCaptureEnabled

    override fun hasValidRemoteConfig(): Boolean = hasValidRemoteConfig
    override fun isAppExitInfoCaptureEnabled(): Boolean = appExitInfoBehavior.isEnabled()

    override fun close() {}

    fun updateListeners() {
        listeners.forEach {
            it.onConfigChange(this)
        }
    }
}
