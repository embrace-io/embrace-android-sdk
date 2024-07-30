package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.DeviceArchitecture
import io.embrace.android.embracesdk.internal.SharedObjectLoader
import io.embrace.android.embracesdk.internal.arch.destination.LogWriter
import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.internal.capture.cpu.CpuInfoDelegate
import io.embrace.android.embracesdk.internal.capture.metadata.MetadataService
import io.embrace.android.embracesdk.internal.capture.session.EmbraceSessionProperties
import io.embrace.android.embracesdk.internal.capture.user.UserService
import io.embrace.android.embracesdk.internal.comms.api.ApiClient
import io.embrace.android.embracesdk.internal.comms.api.ApiService
import io.embrace.android.embracesdk.internal.comms.api.ApiUrlBuilder
import io.embrace.android.embracesdk.internal.comms.delivery.PendingApiCallsSender
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.envelope.metadata.HostedSdkVersionInfo
import io.embrace.android.embracesdk.internal.gating.GatingService
import io.embrace.android.embracesdk.internal.session.MemoryCleanerService
import io.embrace.android.embracesdk.internal.session.id.SessionIdTracker
import io.embrace.android.embracesdk.internal.session.lifecycle.ActivityTracker
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessStateService

/**
 * This module contains services that are essential for bootstrapping other functionality in
 * the SDK during initialization.
 */
internal interface EssentialServiceModule {
    val memoryCleanerService: MemoryCleanerService
    val processStateService: ProcessStateService
    val activityLifecycleTracker: ActivityTracker
    val metadataService: MetadataService
    val hostedSdkVersionInfo: HostedSdkVersionInfo
    val configService: ConfigService
    val gatingService: GatingService
    val userService: UserService
    val urlBuilder: ApiUrlBuilder
    val apiClient: ApiClient
    val apiService: ApiService?
    val sharedObjectLoader: SharedObjectLoader
    val cpuInfoDelegate: CpuInfoDelegate
    val deviceArchitecture: DeviceArchitecture
    val networkConnectivityService: NetworkConnectivityService
    val pendingApiCallsSender: PendingApiCallsSender
    val sessionIdTracker: SessionIdTracker
    val sessionProperties: EmbraceSessionProperties
    val logWriter: LogWriter
}
