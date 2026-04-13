package io.embrace.android.embracesdk.internal.arch.navigation

import android.app.Activity

/**
 * Receives events related to the initialization of components that control navigation
 */
interface NavigationTrackingInitListener {
    /**
     * Track navigation events from a controller associated with the given Activity. If the controller is null, the implementation
     * will try to find the controller within the given activity.
     *
     * The implementation is responsible for casting the controller to whatever interface it needs in order to do its tracking,
     * as the interface is generic and agnostic to any navigation controller implementation details.
     */
    fun trackNavigation(activity: Activity, controller: Any? = null)
}
