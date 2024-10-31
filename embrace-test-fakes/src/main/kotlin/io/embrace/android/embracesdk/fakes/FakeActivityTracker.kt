package io.embrace.android.embracesdk.fakes

import android.app.Activity
import android.os.Bundle
import io.embrace.android.embracesdk.internal.session.lifecycle.ActivityLifecycleListener
import io.embrace.android.embracesdk.internal.session.lifecycle.ActivityTracker
import io.embrace.android.embracesdk.internal.session.lifecycle.StartupListener

class FakeActivityTracker(
    override var foregroundActivity: Activity? = null,
) : ActivityTracker {

    val listeners: MutableList<ActivityLifecycleListener> = mutableListOf()
    val startupListeners: MutableList<StartupListener> = mutableListOf()

    override fun addListener(listener: ActivityLifecycleListener) {
        listeners.add(listener)
    }

    override fun addStartupListener(listener: StartupListener) {
        startupListeners.add(listener)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        TODO("Not yet implemented")
    }

    override fun onActivityStarted(activity: Activity) {
        TODO("Not yet implemented")
    }

    override fun onActivityResumed(activity: Activity) {
        TODO("Not yet implemented")
    }

    override fun onActivityPaused(activity: Activity) {
        TODO("Not yet implemented")
    }

    override fun onActivityStopped(activity: Activity) {
        TODO("Not yet implemented")
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        TODO("Not yet implemented")
    }

    override fun onActivityDestroyed(activity: Activity) {
        TODO("Not yet implemented")
    }

    override fun close() {
        TODO("Not yet implemented")
    }
}
