package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.DeviceArchitecture
import io.embrace.android.embracesdk.internal.DeviceArchitectureImpl
import io.embrace.android.embracesdk.internal.SharedObjectLoader
import io.embrace.android.embracesdk.internal.Systrace
import io.embrace.android.embracesdk.internal.arch.destination.LogWriter
import io.embrace.android.embracesdk.internal.arch.destination.LogWriterImpl
import io.embrace.android.embracesdk.internal.capture.connectivity.EmbraceNetworkConnectivityService
import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.internal.capture.cpu.CpuInfoDelegate
import io.embrace.android.embracesdk.internal.capture.cpu.EmbraceCpuInfoDelegate
import io.embrace.android.embracesdk.internal.capture.metadata.AppEnvironment
import io.embrace.android.embracesdk.internal.capture.metadata.EmbraceMetadataService
import io.embrace.android.embracesdk.internal.capture.metadata.MetadataService
import io.embrace.android.embracesdk.internal.capture.session.EmbraceSessionProperties
import io.embrace.android.embracesdk.internal.capture.user.EmbraceUserService
import io.embrace.android.embracesdk.internal.capture.user.UserService
import io.embrace.android.embracesdk.internal.comms.api.ApiClient
import io.embrace.android.embracesdk.internal.comms.api.ApiClientImpl
import io.embrace.android.embracesdk.internal.comms.api.ApiRequest
import io.embrace.android.embracesdk.internal.comms.api.ApiService
import io.embrace.android.embracesdk.internal.comms.api.EmbraceApiService
import io.embrace.android.embracesdk.internal.comms.api.EmbraceApiUrlBuilder
import io.embrace.android.embracesdk.internal.comms.delivery.EmbracePendingApiCallsSender
import io.embrace.android.embracesdk.internal.comms.delivery.PendingApiCallsSender
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.config.EmbraceConfigService
import io.embrace.android.embracesdk.internal.config.LocalConfigParser
import io.embrace.android.embracesdk.internal.config.behavior.BehaviorThresholdCheck
import io.embrace.android.embracesdk.internal.config.behavior.SdkEndpointBehaviorImpl
import io.embrace.android.embracesdk.internal.envelope.metadata.HostedSdkVersionInfo
import io.embrace.android.embracesdk.internal.gating.EmbraceGatingService
import io.embrace.android.embracesdk.internal.gating.GatingService
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.session.EmbraceMemoryCleanerService
import io.embrace.android.embracesdk.internal.session.MemoryCleanerService
import io.embrace.android.embracesdk.internal.session.id.SessionIdTracker
import io.embrace.android.embracesdk.internal.session.id.SessionIdTrackerImpl
import io.embrace.android.embracesdk.internal.session.lifecycle.ActivityLifecycleTracker
import io.embrace.android.embracesdk.internal.session.lifecycle.EmbraceProcessStateService
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessStateService
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.worker.WorkerName

/**
 * Default string value for app info missing strings
 */
private const val UNKNOWN_VALUE = "UNKNOWN"

internal class EssentialServiceModuleImpl(
    initModule: InitModule,
    openTelemetryModule: OpenTelemetryModule,
    coreModule: CoreModule,
    workerThreadModule: WorkerThreadModule,
    systemServiceModule: SystemServiceModule,
    androidServicesModule: AndroidServicesModule,
    storageModule: StorageModule,
    customAppId: String?,
    customerLogModuleProvider: Provider<CustomerLogModule>,
    dataSourceModuleProvider: Provider<DataSourceModule>,
    framework: AppFramework,
    private val configServiceProvider: (framework: AppFramework) -> ConfigService? = { null }
) : EssentialServiceModule {

    // Many of these properties are temporarily here to break a circular dependency between services.
    // When possible, we should try to move them into a new service or module.
    private val localConfig = Systrace.traceSynchronous("local-config-init") {
        LocalConfigParser.fromResources(
            coreModule.resources,
            coreModule.context.packageName,
            customAppId,
            initModule.jsonSerializer,
            openTelemetryModule.openTelemetryConfiguration,
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

    override val processStateService: ProcessStateService by singleton {
        Systrace.traceSynchronous("process-state-service-init") {
            EmbraceProcessStateService(initModule.clock, initModule.logger)
        }
    }

    override val activityLifecycleTracker: ActivityLifecycleTracker by singleton {
        ActivityLifecycleTracker(coreModule.application, initModule.logger)
    }

    override val configService: ConfigService by singleton {
        Systrace.traceSynchronous("config-service-init") {
            configServiceProvider(framework)
                ?: EmbraceConfigService(
                    localConfig,
                    apiService,
                    androidServicesModule.preferencesService,
                    initModule.clock,
                    initModule.logger,
                    backgroundWorker,
                    coreModule.isDebug,
                    framework,
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
            configService.appFramework
        )
    }

    override val metadataService: MetadataService by singleton {
        Systrace.traceSynchronous("metadata-service-init") {
            EmbraceMetadataService.ofContext(
                coreModule.context,
                AppEnvironment(coreModule.context.applicationInfo).environment,
                initModule.systemInfo,
                coreModule.buildInfo,
                configService,
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
            val sdkEndpointBehavior = SdkEndpointBehaviorImpl(
                thresholdCheck = thresholdCheck,
                localSupplier = localConfig.sdkConfig::baseUrls,
            )
            checkNotNull(appId)
            val coreBaseUrl = sdkEndpointBehavior.getData(appId)
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
        EmbraceGatingService(
            configService,
            customerLogModuleProvider().logService,
            initModule.logger
        )
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
            EmbraceNetworkConnectivityService(
                coreModule.context,
                initModule.clock,
                backgroundWorker,
                initModule.logger,
                systemServiceModule.connectivityManager,
                { dataSourceModuleProvider().networkStatusDataSource.dataSource }
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

    override val apiService: ApiService? by singleton {
        if (appId == null) {
            return@singleton null
        }
        Systrace.traceSynchronous("api-service-init") {
            EmbraceApiService(
                apiClient = apiClient,
                serializer = initModule.jsonSerializer,
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

    override val sessionProperties: EmbraceSessionProperties by singleton {
        Systrace.traceSynchronous("session-properties-init") {
            EmbraceSessionProperties(
                androidServicesModule.preferencesService,
                configService,
                initModule.logger
            )
        }
    }

    override val logWriter: LogWriter by singleton {
        LogWriterImpl(
            logger = openTelemetryModule.logger,
            sessionIdTracker = sessionIdTracker,
            metadataService = metadataService,
        )
    }
}
