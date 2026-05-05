package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationTrackingService
import io.embrace.android.embracesdk.internal.arch.state.AppStateTracker
import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.internal.capture.session.UserSessionPropertiesService
import io.embrace.android.embracesdk.internal.capture.user.UserService
import io.embrace.android.embracesdk.internal.session.id.ActiveSessionIdsProvider
import io.embrace.android.embracesdk.internal.session.id.SessionIdProvider
import io.embrace.android.embracesdk.internal.session.id.SessionPartTracker

/**
 * This module contains services that are essential for bootstrapping other functionality in
 * the SDK during initialization.
 */
interface EssentialServiceModule {
    val appStateTracker: AppStateTracker
    val navigationTrackingService: NavigationTrackingService
    val userService: UserService
    val networkConnectivityService: NetworkConnectivityService
    val sessionPartTracker: SessionPartTracker
    val sessionIdProvider: SessionIdProvider
    val activeSessionIdsProvider: ActiveSessionIdsProvider
    val userSessionPropertiesService: UserSessionPropertiesService
    val telemetryDestination: TelemetryDestination
}
