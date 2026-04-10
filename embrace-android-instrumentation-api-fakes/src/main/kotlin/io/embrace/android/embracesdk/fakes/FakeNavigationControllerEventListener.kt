package io.embrace.android.embracesdk.fakes

import android.app.Activity
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationControllerEventListener

class FakeNavigationControllerEventListener: NavigationControllerEventListener {
    override fun onControllerAttached(activity: Activity, timestampMs: Long) {
    }

    override fun onDestinationChange(activity: Activity, screenName: String, timestampMs: Long) {
    }
}
