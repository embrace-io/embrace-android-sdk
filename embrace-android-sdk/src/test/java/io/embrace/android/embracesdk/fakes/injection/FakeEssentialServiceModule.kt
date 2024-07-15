package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.capture.cpu.CpuInfoDelegate
import io.embrace.android.embracesdk.capture.metadata.HostedSdkVersionInfo
import io.embrace.android.embracesdk.capture.metadata.MetadataService
import io.embrace.android.embracesdk.capture.user.UserService
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
import io.embrace.android.embracesdk.fakes.FakeUserService
import io.embrace.android.embracesdk.fakes.NoOpNetworkConnectivityService
import io.embrace.android.embracesdk.fakes.fakeEmbraceSessionProperties
import io.embrace.android.embracesdk.injection.EssentialServiceModule
import io.embrace.android.embracesdk.internal.DeviceArchitecture
import io.embrace.android.embracesdk.internal.SharedObjectLoader
import io.embrace.android.embracesdk.internal.arch.destination.LogWriter
import io.embrace.android.embracesdk.internal.comms.api.ApiClient
import io.embrace.android.embracesdk.internal.comms.api.ApiService
import io.embrace.android.embracesdk.internal.comms.api.ApiUrlBuilder
import io.embrace.android.embracesdk.internal.comms.delivery.PendingApiCallsSender
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.gating.GatingService
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.internal.session.MemoryCleanerService
import io.embrace.android.embracesdk.internal.session.id.SessionIdTracker
import io.embrace.android.embracesdk.internal.session.lifecycle.ActivityTracker
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessStateService
import io.embrace.android.embracesdk.internal.session.properties.EmbraceSessionProperties

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
    override val sessionProperties: EmbraceSessionProperties = fakeEmbraceSessionProperties(),
    override val logWriter: LogWriter = FakeLogWriter()
) : EssentialServiceModule {

    override val cpuInfoDelegate: CpuInfoDelegate = FakeCpuInfoDelegate()
}
