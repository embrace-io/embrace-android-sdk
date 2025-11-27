package io.embrace.android.embracesdk.fakes.injection

import android.app.Activity
import android.app.Application
import android.os.Bundle
import io.embrace.android.embracesdk.fakes.FakeStartupService
import io.embrace.android.embracesdk.internal.instrumentation.startup.AppStartupDataCollector
import io.embrace.android.embracesdk.internal.instrumentation.startup.DataCaptureServiceModule
import io.embrace.android.embracesdk.internal.instrumentation.startup.StartupService
import io.embrace.android.embracesdk.internal.instrumentation.startup.StartupTracker
import io.embrace.android.embracesdk.internal.instrumentation.startup.activity.UiLoadDataListener
import io.mockk.mockk

internal class FakeDataCaptureServiceModule : DataCaptureServiceModule {

    override val startupService: StartupService = FakeStartupService()

    override val appStartupDataCollector: AppStartupDataCollector
        get() = throw UnsupportedOperationException()

    override val startupTracker: StartupTracker = mockk(relaxed = true)

    override val uiLoadDataListener: UiLoadDataListener
        get() = throw UnsupportedOperationException()

    override val activityLoadEventEmitter: Application.ActivityLifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, bundle: Bundle?) {
        }

        override fun onActivityDestroyed(activity: Activity) {
        }

        override fun onActivityPaused(activity: Activity) {
        }

        override fun onActivityResumed(activity: Activity) {
        }

        override fun onActivitySaveInstanceState(activity: Activity, bundle: Bundle) {
        }

        override fun onActivityStarted(activity: Activity) {
        }

        override fun onActivityStopped(activity: Activity) {
        }
    }
}
