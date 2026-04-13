package io.embrace.android.embracesdk.internal.navigation

import android.app.Activity
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationControllerEventListener
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationTrackingInitListener
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationTrackingService

internal class NavigationTrackingServiceImpl(
    override var navigationTrackingInitListener: NavigationTrackingInitListener = NoopNavigationTrackingInitListener,
    override var navigationControllerEventListener: NavigationControllerEventListener = NoopNavigationControllerEventListener
) : NavigationTrackingService {

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
