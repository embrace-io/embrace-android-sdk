package io.embrace.android.embracesdk.internal.navigation

import android.app.Activity
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationTrackingInitListener
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationTrackingService
import io.embrace.android.embracesdk.internal.arch.navigation.ScreenAttributesSource
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCodeEach
import java.util.concurrent.CopyOnWriteArrayList

internal class NavigationTrackingServiceImpl(
    private val errorHandler: ErrorHandler,
    override var navigationTrackingInitListener: NavigationTrackingInitListener = NoopNavigationTrackingInitListener,
) : NavigationTrackingService {

    private val screenAttributesSources = CopyOnWriteArrayList<ScreenAttributesSource>()

    override fun addScreenAttributesSource(source: ScreenAttributesSource) {
        screenAttributesSources.add(source)
    }

    override fun collectScreenAttributes(sink: (key: String, value: String) -> Unit) {
        errorHandler.guardSdkCodeEach(SdkErrorType.SessionPartListenerFail, screenAttributesSources) { source ->
            source.writeAttributes(sink)
        }
    }

    override fun trackNavigation(activity: Activity, controller: Any?) {
        navigationTrackingInitListener.trackNavigation(activity, controller)
    }
}

private object NoopNavigationTrackingInitListener : NavigationTrackingInitListener {
    override fun trackNavigation(activity: Activity, controller: Any?) {}
}
