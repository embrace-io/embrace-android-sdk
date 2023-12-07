package io.embrace.android.embracesdk.injection

import android.os.Debug
import io.embrace.android.embracesdk.capture.connectivity.EmbraceNetworkConnectivityService
import io.embrace.android.embracesdk.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.capture.cpu.CpuInfoDelegate
import io.embrace.android.embracesdk.capture.cpu.EmbraceCpuInfoDelegate
import io.embrace.android.embracesdk.capture.metadata.EmbraceMetadataService
import io.embrace.android.embracesdk.capture.metadata.MetadataService
import io.embrace.android.embracesdk.capture.orientation.NoOpOrientationService
import io.embrace.android.embracesdk.capture.orientation.OrientationService
import io.embrace.android.embracesdk.capture.user.EmbraceUserService
import io.embrace.android.embracesdk.capture.user.UserService
import io.embrace.android.embracesdk.comms.api.ApiClient
import io.embrace.android.embracesdk.comms.api.ApiClientImpl
import io.embrace.android.embracesdk.comms.api.ApiRequest
import io.embrace.android.embracesdk.comms.api.ApiResponseCache
import io.embrace.android.embracesdk.comms.api.ApiService
import io.embrace.android.embracesdk.comms.api.ApiUrlBuilder
import io.embrace.android.embracesdk.comms.api.EmbraceApiService
import io.embrace.android.embracesdk.comms.api.EmbraceApiUrlBuilder
import io.embrace.android.embracesdk.comms.delivery.CacheService
import io.embrace.android.embracesdk.comms.delivery.DeliveryCacheManager
import io.embrace.android.embracesdk.comms.delivery.EmbraceCacheService
import io.embrace.android.embracesdk.comms.delivery.EmbraceDeliveryCacheManager
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
import io.embrace.android.embracesdk.internal.BuildInfo
import io.embrace.android.embracesdk.internal.DeviceArchitecture
import io.embrace.android.embracesdk.internal.DeviceArchitectureImpl
import io.embrace.android.embracesdk.internal.SharedObjectLoader
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger.Companion.logDeveloper
import io.embrace.android.embracesdk.session.EmbraceMemoryCleanerService
import io.embrace.android.embracesdk.session.MemoryCleanerService
import io.embrace.android.embracesdk.session.lifecycle.ActivityLifecycleTracker
import io.embrace.android.embracesdk.session.lifecycle.ActivityTracker
import io.embrace.android.embracesdk.session.lifecycle.EmbraceProcessStateService
import io.embrace.android.embracesdk.session.lifecycle.ProcessStateService
import io.embrace.android.embracesdk.worker.ExecutorName
import io.embrace.android.embracesdk.worker.WorkerThreadModule
import java.io.File

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
    val configService: ConfigService
    val gatingService: GatingService
    val userService: UserService
    val urlBuilder: ApiUrlBuilder
    val cache: ApiResponseCache
    val apiClient: ApiClient
    val apiService: ApiService
    val sharedObjectLoader: SharedObjectLoader
    val cpuInfoDelegate: CpuInfoDelegate
    val deviceArchitecture: DeviceArchitecture
    val networkConnectivityService: NetworkConnectivityService
    val cacheService: CacheService
    val deliveryCacheManager: DeliveryCacheManager
    val pendingApiCallsSender: PendingApiCallsSender
    val storageDirectory: Lazy<File>
}

internal class EssentialServiceModuleImpl(
    initModule: InitModule,
    coreModule: CoreModule,
    workerThreadModule: WorkerThreadModule,
    systemServiceModule: SystemServiceModule,
    androidServicesModule: AndroidServicesModule,
    buildInfo: BuildInfo,
    customAppId: String?,
    enableIntegrationTesting: Boolean,
    private val configStopAction: () -> Unit,
    private val configServiceProvider: () -> ConfigService? = { null },
    override val deviceArchitecture: DeviceArchitecture = DeviceArchitectureImpl()
) : EssentialServiceModule {

    // Many of these properties are temporarily here to break a circular dependency between services.
    // When possible, we should try to move them into a new service or module.
    private val localConfig = LocalConfigParser.fromResources(
        coreModule.resources,
        coreModule.context.packageName,
        customAppId,
        coreModule.jsonSerializer
    )

    private val lazyPackageInfo = lazy {
        coreModule.context.packageManager.getPackageInfo(coreModule.context.packageName, 0)
    }

    private val lazyAppVersionName = lazy {
        try {
            // some customers have trailing white-space for the app version.
            lazyPackageInfo.value.versionName.toString().trim { it <= ' ' }
        } catch (e: Exception) {
            logDeveloper("EssentialServiceModule", "Cannot set appVersionName, setting UNKNOWN_VALUE", e)
            UNKNOWN_VALUE
        }
    }

    private val lazyAppVersionCode: Lazy<String> = lazy {
        try {
            lazyPackageInfo.value.versionCode.toString()
        } catch (e: Exception) {
            logDeveloper("EssentialServiceModule", "Cannot set appVersionCode, setting UNKNOWN_VALUE", e)
            UNKNOWN_VALUE
        }
    }

    private val appId = localConfig.appId

    private val lazyDeviceId = lazy(androidServicesModule.preferencesService::deviceIdentifier)

    private val thresholdCheck: BehaviorThresholdCheck =
        BehaviorThresholdCheck(androidServicesModule.preferencesService::deviceIdentifier)

    private val backgroundExecutorService =
        workerThreadModule.backgroundExecutor(ExecutorName.BACKGROUND_REGISTRATION)

    private val apiRetryExecutor =
        workerThreadModule.scheduledExecutor(ExecutorName.API_RETRY)

    private val deliveryCacheExecutorService =
        workerThreadModule.backgroundExecutor(ExecutorName.DELIVERY_CACHE)

    override val memoryCleanerService: MemoryCleanerService by singleton {
        EmbraceMemoryCleanerService()
    }

    override val orientationService: OrientationService by singleton {
        // Embrace is not processing orientation changes on this moment, so return no-op service.
        NoOpOrientationService()
    }

    override val processStateService: ProcessStateService by singleton {
        EmbraceProcessStateService(initModule.clock)
    }

    override val activityLifecycleTracker: ActivityLifecycleTracker by singleton {
        ActivityLifecycleTracker(coreModule.application, orientationService)
    }

    override val configService: ConfigService by singleton {
        configServiceProvider.invoke()
            ?: EmbraceConfigService(
                localConfig,
                apiService,
                androidServicesModule.preferencesService,
                initModule.clock,
                coreModule.logger,
                backgroundExecutorService,
                coreModule.isDebug,
                configStopAction,
                thresholdCheck
            )
    }

    override val sharedObjectLoader: SharedObjectLoader by singleton {
        SharedObjectLoader()
    }

    override val cpuInfoDelegate: CpuInfoDelegate by singleton {
        EmbraceCpuInfoDelegate(sharedObjectLoader, coreModule.logger)
    }

    override val metadataService: MetadataService by singleton {
        EmbraceMetadataService.ofContext(
            coreModule.context,
            buildInfo,
            configService,
            coreModule.appFramework,
            androidServicesModule.preferencesService,
            processStateService,
            backgroundExecutorService,
            systemServiceModule.storageManager,
            systemServiceModule.windowManager,
            systemServiceModule.activityManager,
            initModule.clock,
            cpuInfoDelegate,
            deviceArchitecture,
            lazyAppVersionName,
            lazyAppVersionCode
        )
    }

    override val urlBuilder by singleton {
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

    override val cache by singleton {
        ApiResponseCache(
            coreModule.jsonSerializer,
            { File(storageDirectory.value, "emb_config_cache") }
        )
    }

    override val gatingService: GatingService by singleton {
        EmbraceGatingService(configService)
    }

    override val userService: UserService by singleton {
        EmbraceUserService(
            androidServicesModule.preferencesService,
            coreModule.logger
        )
    }

    override val networkConnectivityService: NetworkConnectivityService by singleton {
        val autoDataCaptureBehavior = AutoDataCaptureBehavior(
            thresholdCheck = thresholdCheck,
            localSupplier = { localConfig },
            remoteSupplier = { null }
        )
        EmbraceNetworkConnectivityService(
            coreModule.context,
            initModule.clock,
            backgroundExecutorService,
            coreModule.logger,
            systemServiceModule.connectivityManager,
            autoDataCaptureBehavior.isNetworkConnectivityServiceEnabled()
        )
    }

    override val cacheService: CacheService by singleton {
        EmbraceCacheService(storageDirectory, coreModule.jsonSerializer, coreModule.logger)
    }

    override val deliveryCacheManager: DeliveryCacheManager by singleton {
        EmbraceDeliveryCacheManager(
            cacheService,
            deliveryCacheExecutorService,
            coreModule.logger,
            initModule.clock,
            coreModule.jsonSerializer
        )
    }

    override val pendingApiCallsSender: PendingApiCallsSender by singleton {
        EmbracePendingApiCallsSender(
            networkConnectivityService,
            apiRetryExecutor,
            deliveryCacheManager,
            initModule.clock
        )
    }

    override val apiService: ApiService by singleton {
        EmbraceApiService(
            apiClient = apiClient,
            serializer = coreModule.jsonSerializer,
            cachedConfigProvider = { url: String, request: ApiRequest -> cache.retrieveCachedConfig(url, request) },
            logger = coreModule.logger,
            scheduledExecutorService = apiRetryExecutor,
            cacheManager = deliveryCacheManager,
            pendingApiCallsSender = pendingApiCallsSender,
            lazyDeviceId = lazyDeviceId,
            appId = appId,
            urlBuilder = urlBuilder,
            networkConnectivityService = networkConnectivityService
        )
    }

    override val apiClient: ApiClient by singleton {
        ApiClientImpl(
            coreModule.logger
        )
    }

    override val storageDirectory: Lazy<File> = lazy {
        coreModule.context.cacheDir
    }
}

/**
 * Default string value for app info missing strings
 */
private const val UNKNOWN_VALUE = "UNKNOWN"
