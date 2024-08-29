package io.embrace.android.embracesdk.internal.session.lifecycle

import android.app.Activity
import android.app.Application
import java.io.Closeable

/**
 * Tracks activity lifecycle events.
 */
public interface ActivityTracker : Application.ActivityLifecycleCallbacks, Closeable {

    /**
     * Gets the activity which is currently in the foreground.
     *
     * @return an optional of the activity currently in the foreground
     */
    public val foregroundActivity: Activity?

    public fun addListener(listener: ActivityLifecycleListener)

    public fun addStartupListener(listener: StartupListener)
}
