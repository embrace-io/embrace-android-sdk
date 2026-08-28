package io.embrace.android.embracesdk.instrumentation.leaks

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import io.embrace.android.embracesdk.internal.session.id.SessionIdsSnapshot

internal class ActivityLeakDetectionLifecycleCallbacks(
    private val leakDetector: LeakDetector,
    private val activeSessionIdsProvider: () -> SessionIdsSnapshot,
    private val fragmentSupport: FragmentSupport,
) : Application.ActivityLifecycleCallbacks {

    override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
        leakDetector.trackOpened(activity)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            leakDetector.trackOpened(activity)
        }
        fragmentSupport.onActivityCreated(activity)
    }

    override fun onActivityDestroyed(activity: Activity) {
        leakDetector.trackClosed(activity, LeakContext(OBJECT_TYPE, activeSessionIdsProvider()))
    }

    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit

    private companion object {

        /**
         * Reported as [LeakContext.objectType], naming what this instrumentation tracks.
         */
        const val OBJECT_TYPE = "activity"
    }
}
