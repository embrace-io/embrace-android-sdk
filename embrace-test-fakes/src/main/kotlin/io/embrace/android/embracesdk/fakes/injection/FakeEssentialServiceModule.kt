package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.fakes.FakeActivityTracker
import io.embrace.android.embracesdk.fakes.FakeLogWriter
import io.embrace.android.embracesdk.fakes.FakeNetworkConnectivityService
import io.embrace.android.embracesdk.fakes.FakeProcessStateService
import io.embrace.android.embracesdk.fakes.FakeSessionIdTracker
import io.embrace.android.embracesdk.fakes.FakeSessionPropertiesService
import io.embrace.android.embracesdk.fakes.FakeUserService
import io.embrace.android.embracesdk.internal.arch.destination.LogWriter
import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.internal.capture.user.UserService
import io.embrace.android.embracesdk.internal.injection.EssentialServiceModule
import io.embrace.android.embracesdk.internal.session.id.SessionIdTracker
import io.embrace.android.embracesdk.internal.session.lifecycle.ActivityTracker
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessStateService

class FakeEssentialServiceModule(
    override val processStateService: ProcessStateService = FakeProcessStateService(),
    override val activityLifecycleTracker: ActivityTracker = FakeActivityTracker(),
    override val sessionIdTracker: SessionIdTracker = FakeSessionIdTracker(),
    override val userService: UserService = FakeUserService(),
    override val networkConnectivityService: NetworkConnectivityService = FakeNetworkConnectivityService(),
    override val logWriter: LogWriter = FakeLogWriter(),
    override val sessionPropertiesService: FakeSessionPropertiesService = FakeSessionPropertiesService(),
) : EssentialServiceModule
