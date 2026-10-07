package io.embrace.android.embracesdk.internal.navigation

import android.app.Activity
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationTrackingInitListener
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationTrackingService

internal class NavigationTrackingServiceImpl(
    override var navigationTrackingInitListener: NavigationTrackingInitListener = NoopNavigationTrackingInitListener,
) : NavigationTrackingService {

    override fun trackNavigation(activity: Activity, controller: Any?) {
        navigationTrackingInitListener.trackNavigation(activity, controller)
    }
}

private object NoopNavigationTrackingInitListener : NavigationTrackingInitListener {
    override fun trackNavigation(activity: Activity, controller: Any?) {}
}
