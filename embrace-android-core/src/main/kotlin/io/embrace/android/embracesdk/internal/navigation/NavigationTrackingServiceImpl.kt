package io.embrace.android.embracesdk.internal.navigation

import android.app.Activity
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationTrackingInitListener
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationTrackingService
import io.embrace.android.embracesdk.internal.arch.navigation.ScreenAttributesSource
import java.util.concurrent.CopyOnWriteArrayList

internal class NavigationTrackingServiceImpl(
    override var navigationTrackingInitListener: NavigationTrackingInitListener = NoopNavigationTrackingInitListener,
) : NavigationTrackingService {

    private val screenAttributesSources = CopyOnWriteArrayList<ScreenAttributesSource>()

    override fun addScreenAttributesSource(source: ScreenAttributesSource) {
        screenAttributesSources.add(source)
    }

    override fun collectScreenAttributes(sink: (key: String, value: String) -> Unit) {
        screenAttributesSources.forEach { source ->
            try {
                source.writeAttributes(sink)
            } catch (_: Throwable) {
            }
        }
    }

    override fun trackNavigation(activity: Activity, controller: Any?) {
        navigationTrackingInitListener.trackNavigation(activity, controller)
    }
}

private object NoopNavigationTrackingInitListener : NavigationTrackingInitListener {
    override fun trackNavigation(activity: Activity, controller: Any?) {}
}
