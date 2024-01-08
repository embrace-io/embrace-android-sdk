package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.capture.connectivity.NoOpNetworkConnectivityService
import io.embrace.android.embracesdk.capture.cpu.CpuInfoDelegate
import io.embrace.android.embracesdk.capture.metadata.MetadataService
import io.embrace.android.embracesdk.capture.orientation.NoOpOrientationService
import io.embrace.android.embracesdk.capture.orientation.OrientationService
import io.embrace.android.embracesdk.capture.user.UserService
import io.embrace.android.embracesdk.comms.api.ApiClient
import io.embrace.android.embracesdk.comms.api.ApiService
import io.embrace.android.embracesdk.comms.api.ApiUrlBuilder
import io.embrace.android.embracesdk.comms.delivery.PendingApiCallsSender
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.fakes.FakeActivityTracker
import io.embrace.android.embracesdk.fakes.FakeAndroidMetadataService
import io.embrace.android.embracesdk.fakes.FakeApiClient
import io.embrace.android.embracesdk.fakes.FakeApiService
import io.embrace.android.embracesdk.fakes.FakeApiUrlBuilder
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeCpuInfoDelegate
import io.embrace.android.embracesdk.fakes.FakeDeviceArchitecture
import io.embrace.android.embracesdk.fakes.FakeGatingService
import io.embrace.android.embracesdk.fakes.FakeMemoryCleanerService
import io.embrace.android.embracesdk.fakes.FakePendingApiCallsSender
import io.embrace.android.embracesdk.fakes.FakeProcessStateService
import io.embrace.android.embracesdk.fakes.FakeUserService
import io.embrace.android.embracesdk.gating.GatingService
import io.embrace.android.embracesdk.injection.EssentialServiceModule
import io.embrace.android.embracesdk.internal.DeviceArchitecture
import io.embrace.android.embracesdk.internal.SharedObjectLoader
import io.embrace.android.embracesdk.session.MemoryCleanerService
import io.embrace.android.embracesdk.session.lifecycle.ActivityTracker
import io.embrace.android.embracesdk.session.lifecycle.ProcessStateService

internal class FakeEssentialServiceModule(
    override val processStateService: ProcessStateService = FakeProcessStateService(),
    override val activityLifecycleTracker: ActivityTracker = FakeActivityTracker(),
    override val metadataService: MetadataService = FakeAndroidMetadataService(),
    override val configService: ConfigService = FakeConfigService(),
    override val memoryCleanerService: MemoryCleanerService = FakeMemoryCleanerService(),
    override val gatingService: GatingService = FakeGatingService(),
    override val orientationService: OrientationService = NoOpOrientationService(),
    override val apiClient: ApiClient = FakeApiClient(),
    override val userService: UserService = FakeUserService(),
    override val sharedObjectLoader: SharedObjectLoader = SharedObjectLoader(),
    override val deviceArchitecture: DeviceArchitecture = FakeDeviceArchitecture(),
    override val apiService: ApiService = FakeApiService(),
    override val networkConnectivityService: NetworkConnectivityService = NoOpNetworkConnectivityService(),
    override val pendingApiCallsSender: PendingApiCallsSender = FakePendingApiCallsSender(),
    override val urlBuilder: ApiUrlBuilder = FakeApiUrlBuilder(),
) : EssentialServiceModule {

    override val cpuInfoDelegate: CpuInfoDelegate = FakeCpuInfoDelegate()
}
