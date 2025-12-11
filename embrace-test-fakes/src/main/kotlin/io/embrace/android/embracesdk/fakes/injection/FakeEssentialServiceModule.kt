package io.embrace.android.embracesdk.fakes.injection

import android.app.Activity
import android.os.Bundle
import io.embrace.android.embracesdk.fakes.FakeAppStateTracker
import io.embrace.android.embracesdk.fakes.FakeNetworkConnectivityService
import io.embrace.android.embracesdk.fakes.FakeSessionTracker
import io.embrace.android.embracesdk.fakes.FakeSessionPropertiesService
import io.embrace.android.embracesdk.fakes.FakeTelemetryDestination
import io.embrace.android.embracesdk.fakes.FakeUserService
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.state.AppStateTracker
import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.internal.capture.user.UserService
import io.embrace.android.embracesdk.internal.injection.EssentialServiceModule
import io.embrace.android.embracesdk.internal.session.id.SessionTracker
import io.embrace.android.embracesdk.internal.session.lifecycle.ActivityTracker

class FakeEssentialServiceModule(
    override val appStateTracker: AppStateTracker = FakeAppStateTracker(),
    override val activityLifecycleTracker: ActivityTracker = FakeActivityTracker(),
    override val sessionTracker: SessionTracker = FakeSessionTracker(),
    override val userService: UserService = FakeUserService(),
    override val networkConnectivityService: NetworkConnectivityService = FakeNetworkConnectivityService(),
    override val telemetryDestination: TelemetryDestination = FakeTelemetryDestination(),
    override val sessionPropertiesService: FakeSessionPropertiesService = FakeSessionPropertiesService(),
) : EssentialServiceModule

private class FakeActivityTracker(
    override var foregroundActivity: Activity? = null,
) : ActivityTracker {

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        TODO("Not yet implemented")
    }

    override fun onActivityStarted(activity: Activity) {
        TODO("Not yet implemented")
    }

    override fun onActivityResumed(activity: Activity) {
        TODO("Not yet implemented")
    }

    override fun onActivityPaused(activity: Activity) {
        TODO("Not yet implemented")
    }

    override fun onActivityStopped(activity: Activity) {
        TODO("Not yet implemented")
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        TODO("Not yet implemented")
    }

    override fun onActivityDestroyed(activity: Activity) {
        TODO("Not yet implemented")
    }

    override fun close() {
        TODO("Not yet implemented")
    }
}
