package io.embrace.android.embracesdk.internal.session.lifecycle

import android.app.Activity
import android.os.Bundle

/**
 * Listener implemented by observers of the [ActivityLifecycleTracker].
 */
public interface ActivityLifecycleListener {

    /**
     * Triggered when an activity is opened.
     *
     * @param activity details of the activity
     */
    public fun onView(activity: Activity) {}

    /**
     * Triggered when an activity is closed.
     *
     * @param activity details of the activity
     */
    public fun onViewClose(activity: Activity) {}

    /**
     * Triggered when an activity is created.
     *
     * @param activity the activity
     * @param bundle   the bundle
     */
    public fun onActivityCreated(activity: Activity, bundle: Bundle?) {}
}
