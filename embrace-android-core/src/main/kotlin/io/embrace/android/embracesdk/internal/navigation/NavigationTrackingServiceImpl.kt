package io.embrace.android.embracesdk.internal.navigation

import android.app.Activity
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationControllerEventListener
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationTrackingInitListener
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationTrackingService
import io.embrace.android.embracesdk.internal.arch.navigation.ScreenAttributesSource
import java.util.concurrent.CopyOnWriteArrayList

internal class NavigationTrackingServiceImpl(
    override var navigationTrackingInitListener: NavigationTrackingInitListener = NoopNavigationTrackingInitListener,
    override var navigationControllerEventListener: NavigationControllerEventListener = NoopNavigationControllerEventListener,
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
