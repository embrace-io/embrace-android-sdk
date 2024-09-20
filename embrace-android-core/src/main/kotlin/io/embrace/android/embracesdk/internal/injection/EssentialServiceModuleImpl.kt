package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.Systrace
import io.embrace.android.embracesdk.internal.arch.destination.LogWriter
import io.embrace.android.embracesdk.internal.arch.destination.LogWriterImpl
import io.embrace.android.embracesdk.internal.capture.connectivity.EmbraceNetworkConnectivityService
import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.internal.capture.session.SessionPropertiesService
import io.embrace.android.embracesdk.internal.capture.session.SessionPropertiesServiceImpl
import io.embrace.android.embracesdk.internal.capture.user.EmbraceUserService
import io.embrace.android.embracesdk.internal.capture.user.UserService
import io.embrace.android.embracesdk.internal.comms.api.ApiClient
import io.embrace.android.embracesdk.internal.comms.api.ApiClientImpl
import io.embrace.android.embracesdk.internal.comms.api.ApiRequest
import io.embrace.android.embracesdk.internal.comms.api.ApiService
import io.embrace.android.embracesdk.internal.comms.api.ApiUrlBuilder
import io.embrace.android.embracesdk.internal.comms.api.EmbraceApiService
import io.embrace.android.embracesdk.internal.comms.api.EmbraceApiUrlBuilder
import io.embrace.android.embracesdk.internal.comms.delivery.EmbracePendingApiCallsSender
import io.embrace.android.embracesdk.internal.comms.delivery.PendingApiCallsSender
import io.embrace.android.embracesdk.internal.session.id.SessionIdTracker
import io.embrace.android.embracesdk.internal.session.id.SessionIdTrackerImpl
import io.embrace.android.embracesdk.internal.session.lifecycle.ActivityLifecycleTracker
import io.embrace.android.embracesdk.internal.session.lifecycle.EmbraceProcessStateService
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessStateService
import io.embrace.android.embracesdk.internal.worker.Worker

class EssentialServiceModuleImpl(
    initModule: InitModule,
    configModule: ConfigModule,
    openTelemetryModule: OpenTelemetryModule,
    coreModule: CoreModule,
    workerThreadModule: WorkerThreadModule,
    systemServiceModule: SystemServiceModule,
    androidServicesModule: AndroidServicesModule,
    storageModule: StorageModule
) : EssentialServiceModule {

    private val configService by lazy { configModule.configService }

    private val lazyDeviceId = lazy(androidServicesModule.preferencesService::deviceIdentifier)

    override val processStateService: ProcessStateService by singleton {
        Systrace.traceSynchronous("process-state-service-init") {
            EmbraceProcessStateService(initModule.clock, initModule.logger)
        }
    }

    override val activityLifecycleTracker: ActivityLifecycleTracker by singleton {
        ActivityLifecycleTracker(coreModule.application, initModule.logger)
    }

    override val urlBuilder: ApiUrlBuilder by singleton {
        Systrace.traceSynchronous("url-builder-init") {
            // We use SdkEndpointBehavior and localConfig directly to avoid a circular dependency
            // but we want to access behaviors from ConfigService when possible.
            val sdkEndpointBehavior = configService.sdkEndpointBehavior
            val appId = checkNotNull(configService.appId)
            val coreBaseUrl = sdkEndpointBehavior.getData(appId)
            val configBaseUrl = sdkEndpointBehavior.getConfig(appId)

            EmbraceApiUrlBuilder(
                coreBaseUrl = coreBaseUrl,
                configBaseUrl = configBaseUrl,
                appId = appId,
                lazyDeviceId = lazyDeviceId,
                lazyAppVersionName = lazy { coreModule.packageVersionInfo.versionName }
            )
        }
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
                workerThreadModule.backgroundWorker(Worker.NonIoRegWorker),
                initModule.logger,
                systemServiceModule.connectivityManager
            )
        }
    }

    override val pendingApiCallsSender: PendingApiCallsSender by singleton {
        Systrace.traceSynchronous("pending-call-sender-init") {
            EmbracePendingApiCallsSender(
                workerThreadModule.scheduledWorker(Worker.IoRegWorker),
                storageModule.deliveryCacheManager,
                initModule.clock,
                initModule.logger
            )
        }
    }

    override val apiService: ApiService? by singleton {
        val appId = configService.appId ?: return@singleton null
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
                backgroundWorker = workerThreadModule.backgroundWorker(Worker.NetworkRequestWorker),
                pendingApiCallsSender = pendingApiCallsSender,
                lazyDeviceId = lazyDeviceId,
                appId = appId,
                urlBuilder = urlBuilder
            )
        }
    }

    override val apiClient: ApiClient by singleton {
        ApiClientImpl()
    }

    override val sessionIdTracker: SessionIdTracker by singleton {
        SessionIdTrackerImpl(systemServiceModule.activityManager, initModule.logger)
    }

    override val sessionPropertiesService: SessionPropertiesService by singleton {
        Systrace.traceSynchronous("session-properties-init") {
            SessionPropertiesServiceImpl(
                preferencesService = androidServicesModule.preferencesService,
                configService = configService,
                logger = initModule.logger,
                writer = openTelemetryModule.currentSessionSpan
            )
        }
    }

    override val logWriter: LogWriter by singleton {
        LogWriterImpl(
            logger = openTelemetryModule.logger,
            sessionIdTracker = sessionIdTracker,
            processStateService = processStateService
        )
    }
}
