package io.embrace.android.embracesdk.internal.arch.navigation

import android.app.Activity

/**
 * Listener that receives events related to components that control navigation
 */
interface NavigationControllerEventListener {
    /**
     * Called when a component that controls navigation is attached
     */
    fun onControllerAttached(activity: Activity, timestampMs: Long)

    /**
     * Called when a navigation component attached to the given activity updates its destination
     */
    fun onDestinationChange(activity: Activity, screenName: String, timestampMs: Long)
}
