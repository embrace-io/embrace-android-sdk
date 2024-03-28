package io.embrace.android.embracesdk.injection

import android.os.Debug
import io.embrace.android.embracesdk.capture.connectivity.EmbraceNetworkConnectivityService
import io.embrace.android.embracesdk.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.capture.cpu.CpuInfoDelegate
import io.embrace.android.embracesdk.capture.cpu.EmbraceCpuInfoDelegate
import io.embrace.android.embracesdk.capture.metadata.AppEnvironment
import io.embrace.android.embracesdk.capture.metadata.EmbraceMetadataService
import io.embrace.android.embracesdk.capture.metadata.HostedSdkVersionInfo
import io.embrace.android.embracesdk.capture.metadata.MetadataService
import io.embrace.android.embracesdk.capture.orientation.NoOpOrientationService
import io.embrace.android.embracesdk.capture.orientation.OrientationService
import io.embrace.android.embracesdk.capture.user.EmbraceUserService
import io.embrace.android.embracesdk.capture.user.UserService
import io.embrace.android.embracesdk.comms.api.ApiClient
import io.embrace.android.embracesdk.comms.api.ApiClientImpl
import io.embrace.android.embracesdk.comms.api.ApiRequest
import io.embrace.android.embracesdk.comms.api.ApiService
import io.embrace.android.embracesdk.comms.api.ApiUrlBuilder
import io.embrace.android.embracesdk.comms.api.EmbraceApiService
import io.embrace.android.embracesdk.comms.api.EmbraceApiUrlBuilder
import io.embrace.android.embracesdk.comms.delivery.EmbracePendingApiCallsSender
import io.embrace.android.embracesdk.comms.delivery.PendingApiCallsSender
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.config.EmbraceConfigService
import io.embrace.android.embracesdk.config.LocalConfigParser
import io.embrace.android.embracesdk.config.behavior.AutoDataCaptureBehavior
import io.embrace.android.embracesdk.config.behavior.BehaviorThresholdCheck
import io.embrace.android.embracesdk.config.behavior.SdkEndpointBehavior
import io.embrace.android.embracesdk.gating.EmbraceGatingService
import io.embrace.android.embracesdk.gating.GatingService
import io.embrace.android.embracesdk.internal.DeviceArchitecture
import io.embrace.android.embracesdk.internal.DeviceArchitectureImpl
import io.embrace.android.embracesdk.internal.SharedObjectLoader
import io.embrace.android.embracesdk.internal.Systrace
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.session.EmbraceMemoryCleanerService
import io.embrace.android.embracesdk.session.MemoryCleanerService
import io.embrace.android.embracesdk.session.id.SessionIdTracker
import io.embrace.android.embracesdk.session.id.SessionIdTrackerImpl
import io.embrace.android.embracesdk.session.lifecycle.ActivityLifecycleTracker
import io.embrace.android.embracesdk.session.lifecycle.ActivityTracker
import io.embrace.android.embracesdk.session.lifecycle.EmbraceProcessStateService
import io.embrace.android.embracesdk.session.lifecycle.ProcessStateService
import io.embrace.android.embracesdk.worker.WorkerName
import io.embrace.android.embracesdk.worker.WorkerThreadModule

/**
 * This module contains services that are essential for bootstrapping other functionality in
 * the SDK during initialization.
 */
internal interface EssentialServiceModule {

    val memoryCleanerService: MemoryCleanerService
    val orientationService: OrientationService
    val processStateService: ProcessStateService
    val activityLifecycleTracker: ActivityTracker
    val metadataService: MetadataService
    val hostedSdkVersionInfo: HostedSdkVersionInfo
    val configService: ConfigService
    val gatingService: GatingService
    val userService: UserService
    val urlBuilder: ApiUrlBuilder
    val apiClient: ApiClient
    val apiService: ApiService
    val sharedObjectLoader: SharedObjectLoader
    val cpuInfoDelegate: CpuInfoDelegate
    val deviceArchitecture: DeviceArchitecture
    val networkConnectivityService: NetworkConnectivityService
    val pendingApiCallsSender: PendingApiCallsSender
    val sessionIdTracker: SessionIdTracker
}

internal class EssentialServiceModuleImpl(
    initModule: InitModule,
    coreModule: CoreModule,
    workerThreadModule: WorkerThreadModule,
    systemServiceModule: SystemServiceModule,
    androidServicesModule: AndroidServicesModule,
    storageModule: StorageModule,
    customAppId: String?,
    enableIntegrationTesting: Boolean,
    private val configServiceProvider: Provider<ConfigService?> = { null }
) : EssentialServiceModule {

    // Many of these properties are temporarily here to break a circular dependency between services.
    // When possible, we should try to move them into a new service or module.
    private val localConfig = Systrace.traceSynchronous("local-config-init") {
        LocalConfigParser.fromResources(
            coreModule.resources,
            coreModule.context.packageName,
            customAppId,
            coreModule.jsonSerializer,
            initModule.logger
        )
    }

    private val lazyPackageInfo = lazy {
        coreModule.packageInfo
    }

    private val lazyAppVersionName = lazy {
        try {
            // some customers have trailing white-space for the app version.
            lazyPackageInfo.value.versionName.toString().trim { it <= ' ' }
        } catch (e: Exception) {
            UNKNOWN_VALUE
        }
    }

    @Suppress("DEPRECATION")
    private val lazyAppVersionCode: Lazy<String> = lazy {
        try {
            lazyPackageInfo.value.versionCode.toString()
        } catch (e: Exception) {
            UNKNOWN_VALUE
        }
    }

    private val appId = localConfig.appId

    private val lazyDeviceId = lazy(androidServicesModule.preferencesService::deviceIdentifier)

    private val thresholdCheck: BehaviorThresholdCheck =
        BehaviorThresholdCheck(
            androidServicesModule.preferencesService::deviceIdentifier
        )

    private val backgroundWorker =
        workerThreadModule.backgroundWorker(WorkerName.BACKGROUND_REGISTRATION)

    private val networkRequestWorker =
        workerThreadModule.backgroundWorker(WorkerName.NETWORK_REQUEST)

    private val pendingApiCallsWorker =
        workerThreadModule.scheduledWorker(WorkerName.BACKGROUND_REGISTRATION)

    override val memoryCleanerService: MemoryCleanerService by singleton {
        EmbraceMemoryCleanerService(logger = initModule.logger)
    }

    override val orientationService: OrientationService by singleton {
        // Embrace is not processing orientation changes on this moment, so return no-op service.
        NoOpOrientationService()
    }

    override val processStateService: ProcessStateService by singleton {
        Systrace.traceSynchronous("process-state-service-init") {
            EmbraceProcessStateService(initModule.clock, initModule.logger)
        }
    }

    override val activityLifecycleTracker: ActivityLifecycleTracker by singleton {
        ActivityLifecycleTracker(coreModule.application, orientationService, initModule.logger)
    }

    override val configService: ConfigService by singleton {
        Systrace.traceSynchronous("config-service-init") {
            configServiceProvider.invoke()
                ?: EmbraceConfigService(
                    localConfig,
                    apiService,
                    androidServicesModule.preferencesService,
                    initModule.clock,
                    initModule.logger,
                    backgroundWorker,
                    coreModule.isDebug,
                    thresholdCheck
                )
        }
    }

    override val sharedObjectLoader: SharedObjectLoader by singleton {
        SharedObjectLoader(initModule.logger)
    }

    override val cpuInfoDelegate: CpuInfoDelegate by singleton {
        EmbraceCpuInfoDelegate(sharedObjectLoader, initModule.logger)
    }

    override val deviceArchitecture: DeviceArchitecture by singleton {
        DeviceArchitectureImpl()
    }

    override val hostedSdkVersionInfo: HostedSdkVersionInfo by singleton {
        HostedSdkVersionInfo(
            androidServicesModule.preferencesService,
            coreModule.appFramework
        )
    }

    override val metadataService: MetadataService by singleton {
        Systrace.traceSynchronous("metadata-service-init") {
            EmbraceMetadataService.ofContext(
                coreModule.context,
                AppEnvironment(coreModule.context.applicationInfo).environment,
                coreModule.buildInfo,
                configService,
                coreModule.appFramework,
                androidServicesModule.preferencesService,
                processStateService,
                backgroundWorker,
                systemServiceModule.storageManager,
                systemServiceModule.windowManager,
                initModule.clock,
                cpuInfoDelegate,
                deviceArchitecture,
                lazyAppVersionName,
                lazyAppVersionCode,
                hostedSdkVersionInfo,
                initModule.logger
            )
        }
    }

    override val urlBuilder by singleton {
        Systrace.traceSynchronous("url-builder-init") {
            // We use SdkEndpointBehavior and localConfig directly to avoid a circular dependency
            // but we want to access behaviors from ConfigService when possible.
            val sdkEndpointBehavior = SdkEndpointBehavior(
                thresholdCheck = thresholdCheck,
                localSupplier = localConfig.sdkConfig::baseUrls,
            )

            val isDebug = coreModule.isDebug &&
                enableIntegrationTesting &&
                (Debug.isDebuggerConnected() || Debug.waitingForDebugger())

            val coreBaseUrl = if (isDebug) {
                sdkEndpointBehavior.getDataDev(appId)
            } else {
                sdkEndpointBehavior.getData(appId)
            }

            val configBaseUrl = sdkEndpointBehavior.getConfig(appId)

            EmbraceApiUrlBuilder(
                coreBaseUrl = coreBaseUrl,
                configBaseUrl = configBaseUrl,
                appId = appId,
                lazyDeviceId = lazyDeviceId,
                lazyAppVersionName = lazyAppVersionName
            )
        }
    }

    override val gatingService: GatingService by singleton {
        EmbraceGatingService(configService, initModule.logger)
    }

    override val userService: UserService by singleton {
        Systrace.traceSynchronous("user-service-init") {
            EmbraceUserService(
                androidServicesModule.preferencesService,
                initModule.logger
            )
        }
    }

    override val networkConnectivityService: NetworkConnectivityService by singleton {
        Systrace.traceSynchronous("network-connectivity-service-init") {
            val autoDataCaptureBehavior = AutoDataCaptureBehavior(
                thresholdCheck = thresholdCheck,
                localSupplier = { localConfig },
                remoteSupplier = { null }
            )
            EmbraceNetworkConnectivityService(
                coreModule.context,
                initModule.clock,
                backgroundWorker,
                initModule.logger,
                systemServiceModule.connectivityManager,
                autoDataCaptureBehavior.isNetworkConnectivityServiceEnabled()
            )
        }
    }

    override val pendingApiCallsSender: PendingApiCallsSender by singleton {
        Systrace.traceSynchronous("pending-call-sender-init") {
            EmbracePendingApiCallsSender(
                networkConnectivityService,
                pendingApiCallsWorker,
                storageModule.deliveryCacheManager,
                initModule.clock,
                initModule.logger
            )
        }
    }

    override val apiService: ApiService by singleton {
        Systrace.traceSynchronous("api-service-init") {
            EmbraceApiService(
                apiClient = apiClient,
                serializer = coreModule.jsonSerializer,
                cachedConfigProvider = { url: String, request: ApiRequest ->
                    Systrace.traceSynchronous("provide-cache-config") {
                        storageModule.cache.retrieveCachedConfig(url, request)
                    }
                },
                logger = initModule.logger,
                backgroundWorker = networkRequestWorker,
                cacheManager = Systrace.traceSynchronous("cache-manager") { storageModule.deliveryCacheManager },
                pendingApiCallsSender = pendingApiCallsSender,
                lazyDeviceId = lazyDeviceId,
                appId = appId,
                urlBuilder = urlBuilder,
                networkConnectivityService = Systrace.traceSynchronous("network-connectivity") { networkConnectivityService }
            )
        }
    }

    override val apiClient: ApiClient by singleton {
        ApiClientImpl(
            initModule.logger
        )
    }

    override val sessionIdTracker: SessionIdTracker by singleton {
        SessionIdTrackerImpl(systemServiceModule.activityManager, initModule.logger)
    }
}

/**
 * Default string value for app info missing strings
 */
private const val UNKNOWN_VALUE = "UNKNOWN"
