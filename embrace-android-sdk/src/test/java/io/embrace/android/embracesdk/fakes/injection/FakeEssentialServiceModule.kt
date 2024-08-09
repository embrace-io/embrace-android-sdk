package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.fakes.FakeActivityTracker
import io.embrace.android.embracesdk.fakes.FakeApiClient
import io.embrace.android.embracesdk.fakes.FakeApiService
import io.embrace.android.embracesdk.fakes.FakeApiUrlBuilder
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeCpuInfoDelegate
import io.embrace.android.embracesdk.fakes.FakeDeviceArchitecture
import io.embrace.android.embracesdk.fakes.FakeGatingService
import io.embrace.android.embracesdk.fakes.FakeLogWriter
import io.embrace.android.embracesdk.fakes.FakeMemoryCleanerService
import io.embrace.android.embracesdk.fakes.FakeMetadataService
import io.embrace.android.embracesdk.fakes.FakePendingApiCallsSender
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.fakes.FakeProcessStateService
import io.embrace.android.embracesdk.fakes.FakeSessionIdTracker
import io.embrace.android.embracesdk.fakes.FakeSessionPropertiesService
import io.embrace.android.embracesdk.fakes.FakeUserService
import io.embrace.android.embracesdk.fakes.NoOpNetworkConnectivityService
import io.embrace.android.embracesdk.internal.DeviceArchitecture
import io.embrace.android.embracesdk.internal.SharedObjectLoader
import io.embrace.android.embracesdk.internal.arch.destination.LogWriter
import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.internal.capture.cpu.CpuInfoDelegate
import io.embrace.android.embracesdk.internal.capture.metadata.MetadataService
import io.embrace.android.embracesdk.internal.capture.session.SessionPropertiesService
import io.embrace.android.embracesdk.internal.capture.user.UserService
import io.embrace.android.embracesdk.internal.comms.api.ApiClient
import io.embrace.android.embracesdk.internal.comms.api.ApiService
import io.embrace.android.embracesdk.internal.comms.api.ApiUrlBuilder
import io.embrace.android.embracesdk.internal.comms.delivery.PendingApiCallsSender
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.envelope.metadata.HostedSdkVersionInfo
import io.embrace.android.embracesdk.internal.gating.GatingService
import io.embrace.android.embracesdk.internal.injection.EssentialServiceModule
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.internal.session.MemoryCleanerService
import io.embrace.android.embracesdk.internal.session.id.SessionIdTracker
import io.embrace.android.embracesdk.internal.session.lifecycle.ActivityTracker
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessStateService

internal class FakeEssentialServiceModule(
    override val processStateService: ProcessStateService = FakeProcessStateService(),
    override val activityLifecycleTracker: ActivityTracker = FakeActivityTracker(),
    override val metadataService: MetadataService = FakeMetadataService(),
    override val sessionIdTracker: SessionIdTracker = FakeSessionIdTracker(),
    override val configService: ConfigService = FakeConfigService(),
    override val memoryCleanerService: MemoryCleanerService = FakeMemoryCleanerService(),
    override val gatingService: GatingService = FakeGatingService(),
    override val apiClient: ApiClient = FakeApiClient(),
    override val userService: UserService = FakeUserService(),
    override val sharedObjectLoader: SharedObjectLoader = SharedObjectLoader(EmbLoggerImpl()),
    override val deviceArchitecture: DeviceArchitecture = FakeDeviceArchitecture(),
    override val apiService: ApiService = FakeApiService(),
    override val networkConnectivityService: NetworkConnectivityService = NoOpNetworkConnectivityService(),
    override val pendingApiCallsSender: PendingApiCallsSender = FakePendingApiCallsSender(),
    override val urlBuilder: ApiUrlBuilder = FakeApiUrlBuilder(),
    override val hostedSdkVersionInfo: HostedSdkVersionInfo = HostedSdkVersionInfo(FakePreferenceService()),
    override val logWriter: LogWriter = FakeLogWriter(),
    override val cpuInfoDelegate: CpuInfoDelegate = FakeCpuInfoDelegate(),
    override val sessionPropertiesService: SessionPropertiesService = FakeSessionPropertiesService()
) : EssentialServiceModule
