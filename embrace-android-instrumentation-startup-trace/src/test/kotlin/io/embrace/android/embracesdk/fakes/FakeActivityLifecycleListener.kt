package io.embrace.android.embracesdk.fakes

import android.app.Activity
import android.app.Application
import android.os.Bundle

class FakeActivityLifecycleListener : Application.ActivityLifecycleCallbacks {
    var onCreateInvokedCount = 0

    override fun onActivityCreated(activity: Activity, bundle: Bundle?) {
        onCreateInvokedCount++
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
