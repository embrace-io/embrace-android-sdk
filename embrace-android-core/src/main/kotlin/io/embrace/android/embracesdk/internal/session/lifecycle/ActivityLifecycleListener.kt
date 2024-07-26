package io.embrace.android.embracesdk.internal.session.lifecycle

import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Bundle

/**
 * Implementation of [ActivityLifecycleCallbacks] with no-op defaults
 */
public interface ActivityLifecycleListener : ActivityLifecycleCallbacks {

    public override fun onActivityCreated(activity: Activity, bundle: Bundle?) {}

    public override fun onActivityStarted(activity: Activity) {}

    public override fun onActivityResumed(activity: Activity) {}

    public override fun onActivityPaused(activity: Activity) {}

    public override fun onActivityStopped(activity: Activity) {}

    public override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    public override fun onActivityDestroyed(activity: Activity) {}
}
