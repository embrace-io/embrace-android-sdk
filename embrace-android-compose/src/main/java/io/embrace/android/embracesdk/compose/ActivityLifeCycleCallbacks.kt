package io.embrace.android.embracesdk.compose

import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Bundle

public interface ActivityLifeCycleCallbacks : ActivityLifecycleCallbacks {
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        // no op
    }

    override fun onActivityStarted(activity: Activity) {
        // no op
    }

    override fun onActivityPaused(activity: Activity) {
        // no op
    }

    override fun onActivityStopped(activity: Activity) {
        // no op
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        // no op
    }

    override fun onActivityDestroyed(activity: Activity) {
        // no op
    }
}
