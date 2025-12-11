package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.state.AppStateTracker
import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.internal.capture.session.SessionPropertiesService
import io.embrace.android.embracesdk.internal.capture.user.UserService
import io.embrace.android.embracesdk.internal.session.id.SessionTracker
import io.embrace.android.embracesdk.internal.session.lifecycle.ActivityTracker

/**
 * This module contains services that are essential for bootstrapping other functionality in
 * the SDK during initialization.
 */
interface EssentialServiceModule {
    val appStateTracker: AppStateTracker
    val activityLifecycleTracker: ActivityTracker
    val userService: UserService
    val networkConnectivityService: NetworkConnectivityService
    val sessionTracker: SessionTracker
    val sessionPropertiesService: SessionPropertiesService
    val telemetryDestination: TelemetryDestination
}
