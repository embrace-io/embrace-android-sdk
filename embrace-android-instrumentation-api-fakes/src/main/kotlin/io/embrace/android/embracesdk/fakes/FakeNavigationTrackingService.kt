package io.embrace.android.embracesdk.fakes

import android.app.Activity
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationTrackingInitListener
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationTrackingService
import io.embrace.android.embracesdk.internal.arch.navigation.ScreenAttributesSource

class FakeNavigationTrackingService(
    override var navigationTrackingInitListener: NavigationTrackingInitListener = FakeNavigationTrackingInitListener(),
) : NavigationTrackingService {
    val trackedActivities = mutableListOf<Activity>()
    val screenAttributesSources = mutableListOf<ScreenAttributesSource>()

    override fun addScreenAttributesSource(source: ScreenAttributesSource) {
        screenAttributesSources.add(source)
    }

    override fun collectScreenAttributes(sink: (key: String, value: String) -> Unit) {
        screenAttributesSources.forEach { source -> source.writeAttributes(sink) }
    }

    override fun trackNavigation(activity: Activity, controller: Any?) {
        trackedActivities.add(activity)
    }
}
