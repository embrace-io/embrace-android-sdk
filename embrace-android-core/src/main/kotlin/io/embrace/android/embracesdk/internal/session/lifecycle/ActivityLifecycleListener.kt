package io.embrace.android.embracesdk.internal.session.lifecycle

import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Bundle

/**
 * Implementation of [ActivityLifecycleCallbacks] with no-op defaults
 */
public interface ActivityLifecycleListener : ActivityLifecycleCallbacks {

    override public fun onActivityCreated(activity: Activity, bundle: Bundle?) {}

    override public fun onActivityStarted(activity: Activity) {}

    override public fun onActivityResumed(activity: Activity) {}

    override public fun onActivityPaused(activity: Activity) {}

    override public fun onActivityStopped(activity: Activity) {}

    override public fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override public fun onActivityDestroyed(activity: Activity) {}
}
