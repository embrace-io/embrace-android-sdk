package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.fakes.FakeExperimentTrackingService
import io.embrace.android.embracesdk.fakes.FakeProcessStateTracker
import io.embrace.android.embracesdk.fakes.FakeNavigationTrackingService
import io.embrace.android.embracesdk.fakes.FakeNetworkConnectivityService
import io.embrace.android.embracesdk.fakes.FakeSessionIdsProvider
import io.embrace.android.embracesdk.fakes.FakeSessionPartTracker
import io.embrace.android.embracesdk.fakes.FakeTelemetryDestination
import io.embrace.android.embracesdk.fakes.FakeUserService
import io.embrace.android.embracesdk.fakes.FakeUserSessionPropertiesService
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationTrackingService
import io.embrace.android.embracesdk.internal.arch.state.ProcessStateTracker
import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.internal.capture.user.UserService
import io.embrace.android.embracesdk.internal.session.id.SessionIdsProvider
import io.embrace.android.embracesdk.internal.session.id.SessionPartTracker

class FakeEssentialServiceModule(
    val processStateTracker: ProcessStateTracker = FakeProcessStateTracker(),
    val navigationTrackingService: NavigationTrackingService = FakeNavigationTrackingService(),
    val sessionPartTracker: SessionPartTracker = FakeSessionPartTracker(),
    val sessionIdsProvider: SessionIdsProvider = FakeSessionIdsProvider(),
    val userService: UserService = FakeUserService(),
    val networkConnectivityService: NetworkConnectivityService = FakeNetworkConnectivityService(),
    val telemetryDestination: TelemetryDestination = FakeTelemetryDestination(),
    val userSessionPropertiesService: FakeUserSessionPropertiesService = FakeUserSessionPropertiesService(),
    val experimentTrackingService: FakeExperimentTrackingService = FakeExperimentTrackingService(),
)
