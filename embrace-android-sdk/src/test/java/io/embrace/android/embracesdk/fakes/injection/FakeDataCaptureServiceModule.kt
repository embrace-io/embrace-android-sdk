package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.fakes.FakeStartupService
import io.embrace.android.embracesdk.internal.instrumentation.startup.AppStartupDataCollector
import io.embrace.android.embracesdk.internal.instrumentation.startup.DataCaptureServiceModule
import io.embrace.android.embracesdk.internal.instrumentation.startup.StartupService
import io.embrace.android.embracesdk.internal.instrumentation.startup.StartupTracker
import io.embrace.android.embracesdk.internal.instrumentation.startup.activity.UiLoadDataListener
import io.embrace.android.embracesdk.internal.session.lifecycle.ActivityLifecycleListener
import io.mockk.mockk

internal class FakeDataCaptureServiceModule : DataCaptureServiceModule {

    override val startupService: StartupService = FakeStartupService()

    override val appStartupDataCollector: AppStartupDataCollector
        get() = throw UnsupportedOperationException()

    override val startupTracker: StartupTracker = mockk(relaxed = true)

    override val uiLoadDataListener: UiLoadDataListener
        get() = throw UnsupportedOperationException()

    override val activityLoadEventEmitter: ActivityLifecycleListener = object : ActivityLifecycleListener {}
}
