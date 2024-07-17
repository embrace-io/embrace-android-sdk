package io.embrace.android.embracesdk.internal.session.lifecycle

import android.app.Activity
import android.app.Application
import java.io.Closeable

/**
 * Tracks activity lifecycle events.
 */
internal interface ActivityTracker : Application.ActivityLifecycleCallbacks, Closeable {

    /**
     * Gets the activity which is currently in the foreground.
     *
     * @return an optional of the activity currently in the foreground
     */
    val foregroundActivity: Activity?

    fun addListener(listener: ActivityLifecycleListener)

    fun addStartupListener(listener: StartupListener)
}
