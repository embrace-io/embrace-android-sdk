package io.embrace.android.embracesdk.fakes

import android.app.Activity
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationTrackingInitListener

class FakeNavigationTrackingInitListener: NavigationTrackingInitListener {
    override fun trackNavigation(activity: Activity, controller: Any?) {
    }
}
