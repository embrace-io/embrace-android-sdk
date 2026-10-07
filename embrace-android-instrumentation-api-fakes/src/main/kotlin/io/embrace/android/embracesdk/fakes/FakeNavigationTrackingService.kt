package io.embrace.android.embracesdk.fakes

import android.app.Activity
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationTrackingInitListener
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationTrackingService

class FakeNavigationTrackingService(
    override var navigationTrackingInitListener: NavigationTrackingInitListener = FakeNavigationTrackingInitListener(),
) : NavigationTrackingService {
    val trackedActivities = mutableListOf<Activity>()

    override fun trackNavigation(activity: Activity, controller: Any?) {
        trackedActivities.add(activity)
    }
}
