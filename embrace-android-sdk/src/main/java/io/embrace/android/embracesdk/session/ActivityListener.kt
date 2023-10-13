package io.embrace.android.embracesdk.session

import android.app.Activity
import android.os.Bundle

/**
 * Listener implemented by observers of the [ActivityService].
 */
internal interface ActivityListener {

    /**
     * Triggered when the app enters the background.
     */
    fun onBackground(timestamp: Long) {}

    /**
     * Triggered when the application is resumed.
     *
     * @param coldStart   whether this is a cold start
     * @param startupTime the timestamp at which the application started
     */
    fun onForeground(coldStart: Boolean, startupTime: Long, timestamp: Long) {}

    /**
     * Triggered when the application has completed startup;
     */
    fun applicationStartupComplete() {}

    /**
     * Triggered when an activity is opened.
     *
     * @param activity details of the activity
     */
    fun onView(activity: Activity) {}

    /**
     * Triggered when an activity is closed.
     *
     * @param activity details of the activity
     */
    fun onViewClose(activity: Activity) {}

    /**
     * Triggered when an activity is created.
     *
     * @param activity the activity
     * @param bundle   the bundle
     */
    fun onActivityCreated(activity: Activity, bundle: Bundle?) {}
}
