package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.fakes.FakeAppStateTracker
import io.embrace.android.embracesdk.fakes.FakeNetworkConnectivityService
import io.embrace.android.embracesdk.fakes.FakeSessionPropertiesService
import io.embrace.android.embracesdk.fakes.FakeSessionTracker
import io.embrace.android.embracesdk.fakes.FakeTelemetryDestination
import io.embrace.android.embracesdk.fakes.FakeUserService
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.state.AppStateTracker
import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.internal.capture.user.UserService
import io.embrace.android.embracesdk.internal.injection.EssentialServiceModule
import io.embrace.android.embracesdk.internal.session.id.SessionTracker

class FakeEssentialServiceModule(
    override val appStateTracker: AppStateTracker = FakeAppStateTracker(),
    override val sessionTracker: SessionTracker = FakeSessionTracker(),
    override val userService: UserService = FakeUserService(),
    override val networkConnectivityService: NetworkConnectivityService = FakeNetworkConnectivityService(),
    override val telemetryDestination: TelemetryDestination = FakeTelemetryDestination(),
    override val sessionPropertiesService: FakeSessionPropertiesService = FakeSessionPropertiesService(),
) : EssentialServiceModule
