package io.embrace.android.embracesdk.fakes

import android.app.Activity
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationControllerEventListener
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationTrackingInitListener
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationTrackingService
import io.embrace.android.embracesdk.internal.arch.navigation.ScreenAttributesSource

class FakeNavigationTrackingService(
    override var navigationTrackingInitListener: NavigationTrackingInitListener = FakeNavigationTrackingInitListener(),
    override var navigationControllerEventListener: NavigationControllerEventListener = FakeNavigationControllerEventListener()
) : NavigationTrackingService {
    val attachedCalls = mutableListOf<AttachedCall>()
    val destinationChangedCalls = mutableListOf<DestinationChangedCall>()
    val screenAttributesSources = mutableListOf<ScreenAttributesSource>()

    override fun addScreenAttributesSource(source: ScreenAttributesSource) {
        screenAttributesSources.add(source)
    }

    override fun collectScreenAttributes(sink: (key: String, value: String) -> Unit) {
        screenAttributesSources.forEach { source -> source.writeAttributes(sink) }
    }

    override fun trackNavigation(activity: Activity, controller: Any?) {}

    override fun onControllerAttached(activity: Activity, timestampMs: Long) {
        attachedCalls.add(AttachedCall(activity, timestampMs))
    }

    override fun onDestinationChange(activity: Activity, screenName: String, timestampMs: Long) {
        destinationChangedCalls.add(DestinationChangedCall(activity, screenName, timestampMs))
    }

    data class AttachedCall(val activity: Activity, val timestampMs: Long)
    data class DestinationChangedCall(val activity: Activity, val screenName: String, val timestampMs: Long)
}
