package io.embrace.android.embracesdk.session

import android.app.Activity
import android.app.Application
import android.content.ComponentCallbacks2
import androidx.lifecycle.LifecycleObserver
import java.io.Closeable

/**
 * Service which handles Android activity lifecycle callbacks.
 */
internal interface ActivityService :
    ComponentCallbacks2,
    LifecycleObserver,
    Application.ActivityLifecycleCallbacks,
    Closeable {

    /**
     * Whether the application is in the background.
     *
     * @return true if the application is in the background, false otherwise
     */
    val isInBackground: Boolean

    /**
     * Gets the activity which is currently in the foreground.
     *
     * @return an optional of the activity currently in the foreground
     */
    val foregroundActivity: Activity?

    /**
     * Adds an observer of the application's lifecycle activity events.
     *
     * @param listener the observer to register
     */
    fun addListener(listener: ActivityListener)

    /**
     * This function should be automatically invoked when the process lifecycle
     * enters the foreground. You should not call this directly.
     */
    fun onForeground()

    /**
     * This function should be automatically invoked when the process lifecycle
     * enters the background. You should not call this directly.
     */
    fun onBackground()
}
