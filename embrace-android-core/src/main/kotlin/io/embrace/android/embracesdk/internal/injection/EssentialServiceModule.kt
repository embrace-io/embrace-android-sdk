package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.arch.destination.LogWriter
import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.internal.capture.session.SessionPropertiesService
import io.embrace.android.embracesdk.internal.capture.user.UserService
import io.embrace.android.embracesdk.internal.comms.api.ApiClient
import io.embrace.android.embracesdk.internal.comms.api.ApiService
import io.embrace.android.embracesdk.internal.comms.api.ApiUrlBuilder
import io.embrace.android.embracesdk.internal.comms.delivery.PendingApiCallsSender
import io.embrace.android.embracesdk.internal.session.id.SessionIdTracker
import io.embrace.android.embracesdk.internal.session.lifecycle.ActivityTracker
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessStateService

/**
 * This module contains services that are essential for bootstrapping other functionality in
 * the SDK during initialization.
 */
public interface EssentialServiceModule {
    public val processStateService: ProcessStateService
    public val activityLifecycleTracker: ActivityTracker
    public val userService: UserService
    public val urlBuilder: ApiUrlBuilder
    public val apiClient: ApiClient
    public val apiService: ApiService?
    public val networkConnectivityService: NetworkConnectivityService
    public val pendingApiCallsSender: PendingApiCallsSender
    public val sessionIdTracker: SessionIdTracker
    public val sessionPropertiesService: SessionPropertiesService
    public val logWriter: LogWriter
}
