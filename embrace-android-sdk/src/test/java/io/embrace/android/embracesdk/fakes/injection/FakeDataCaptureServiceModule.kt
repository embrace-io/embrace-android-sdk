package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.fakes.FakeAppStartupDataCollector
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeStartupService
import io.embrace.android.embracesdk.fakes.FakeUiLoadDataListener
import io.embrace.android.embracesdk.internal.capture.activity.UiLoadDataListener
import io.embrace.android.embracesdk.internal.capture.startup.AppStartupDataCollector
import io.embrace.android.embracesdk.internal.capture.startup.StartupService
import io.embrace.android.embracesdk.internal.capture.startup.StartupTracker
import io.embrace.android.embracesdk.internal.injection.DataCaptureServiceModule
import io.embrace.android.embracesdk.internal.session.lifecycle.ActivityLifecycleListener
import io.mockk.mockk

internal class FakeDataCaptureServiceModule : DataCaptureServiceModule {

    override val startupService: StartupService = FakeStartupService()

    override val appStartupDataCollector: AppStartupDataCollector = FakeAppStartupDataCollector(FakeClock())

    override val startupTracker: StartupTracker = mockk(relaxed = true)

    override val uiLoadDataListener: UiLoadDataListener = FakeUiLoadDataListener()

    override val activityLoadEventEmitter: ActivityLifecycleListener = object : ActivityLifecycleListener {}
}
