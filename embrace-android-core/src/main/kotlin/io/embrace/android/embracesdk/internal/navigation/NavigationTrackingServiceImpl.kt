package io.embrace.android.embracesdk.internal.navigation

import android.app.Activity
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationControllerEventListener
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationTrackingInitListener
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationTrackingService

internal class NavigationTrackingServiceImpl : NavigationTrackingService {

    private var navigationTrackingInitListener: NavigationTrackingInitListener = NoopNavigationTrackingInitListener
    private var navigationControllerEventListener: NavigationControllerEventListener = NoopNavigationControllerEventListener

    override fun setTrackingInitListener(listener: NavigationTrackingInitListener) {
        navigationTrackingInitListener = listener
    }

    override fun setControllerEventListener(listener: NavigationControllerEventListener) {
        navigationControllerEventListener = listener
    }

    override fun trackNavigation(activity: Activity, controller: Any?) {
        navigationTrackingInitListener.trackNavigation(activity, controller)
    }

    override fun onControllerAttached(activity: Activity, timestampMs: Long) {
        navigationControllerEventListener.onControllerAttached(activity, timestampMs)
    }

    override fun onDestinationChange(activity: Activity, screenName: String, timestampMs: Long) {
        navigationControllerEventListener.onDestinationChange(activity, screenName, timestampMs)
    }
}

private object NoopNavigationTrackingInitListener : NavigationTrackingInitListener {
    override fun trackNavigation(activity: Activity, controller: Any?) {}
}

private object NoopNavigationControllerEventListener : NavigationControllerEventListener {
    override fun onControllerAttached(activity: Activity, timestampMs: Long) {}
    override fun onDestinationChange(activity: Activity, screenName: String, timestampMs: Long) {}
}
