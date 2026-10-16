package io.embrace.android.embracesdk.internal.instrumentation.startup

import android.app.Activity
import android.app.Application
import android.os.Build.VERSION_CODES
import android.os.Bundle

internal class PreQActivityLifecycleCallbacks(
    private val delegate: Application.ActivityLifecycleCallbacks,
) : Application.ActivityLifecycleCallbacks {

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) =
        delegate.onActivityCreated(activity, savedInstanceState)

    override fun onActivityStarted(activity: Activity) = delegate.onActivityStarted(activity)

    override fun onActivityResumed(activity: Activity) = delegate.onActivityResumed(activity)

    override fun onActivityPaused(activity: Activity) = delegate.onActivityPaused(activity)

    override fun onActivityStopped(activity: Activity) = delegate.onActivityStopped(activity)

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) =
        delegate.onActivitySaveInstanceState(activity, outState)

    override fun onActivityDestroyed(activity: Activity) = delegate.onActivityDestroyed(activity)
}

internal fun Application.ActivityLifecycleCallbacks.asDispatchedOn(
    sdkInt: Int,
): Application.ActivityLifecycleCallbacks =
    if (sdkInt >= VERSION_CODES.Q) this else PreQActivityLifecycleCallbacks(this)
