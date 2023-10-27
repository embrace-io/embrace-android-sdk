package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.FakeBreadcrumbService
import io.embrace.android.embracesdk.capture.crumbs.BreadcrumbService
import io.embrace.android.embracesdk.capture.crumbs.PushNotificationCaptureService
import io.embrace.android.embracesdk.capture.crumbs.activity.EmbraceActivityLifecycleBreadcrumbService
import io.embrace.android.embracesdk.capture.memory.MemoryService
import io.embrace.android.embracesdk.capture.powersave.NoOpPowerSaveModeService
import io.embrace.android.embracesdk.capture.powersave.PowerSaveModeService
import io.embrace.android.embracesdk.capture.strictmode.NoOpStrictModeService
import io.embrace.android.embracesdk.capture.strictmode.StrictModeService
import io.embrace.android.embracesdk.capture.thermalstate.NoOpThermalStatusService
import io.embrace.android.embracesdk.capture.thermalstate.ThermalStatusService
import io.embrace.android.embracesdk.capture.webview.EmbraceWebViewService
import io.embrace.android.embracesdk.capture.webview.WebViewService
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeMemoryService
import io.embrace.android.embracesdk.injection.DataCaptureServiceModule
import io.embrace.android.embracesdk.internal.EmbraceSerializer

internal class FakeDataCaptureServiceModule(
    override val strictModeService: StrictModeService = NoOpStrictModeService(),
    override val thermalStatusService: ThermalStatusService = NoOpThermalStatusService(),
    override val activityLifecycleBreadcrumbService: EmbraceActivityLifecycleBreadcrumbService? = null,
    override val powerSaveModeService: PowerSaveModeService = NoOpPowerSaveModeService(),
    override val memoryService: MemoryService = FakeMemoryService(),
    override val breadcrumbService: BreadcrumbService = FakeBreadcrumbService(),
    override val webviewService: WebViewService = EmbraceWebViewService(FakeConfigService(), EmbraceSerializer())
) : DataCaptureServiceModule {

    override val pushNotificationService: PushNotificationCaptureService
        get() = TODO("Not yet implemented")
}
