package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.capture.cpu.CpuInfoDelegate
import io.embrace.android.embracesdk.capture.cpu.EmbraceCpuInfoDelegate
import io.embrace.android.embracesdk.capture.metadata.EmbraceMetadataService
import io.embrace.android.embracesdk.capture.metadata.MetadataService
import io.embrace.android.embracesdk.capture.orientation.NoOpOrientationService
import io.embrace.android.embracesdk.capture.orientation.OrientationService
import io.embrace.android.embracesdk.capture.user.EmbraceUserService
import io.embrace.android.embracesdk.capture.user.UserService
import io.embrace.android.embracesdk.comms.api.ApiClient
import io.embrace.android.embracesdk.comms.api.ApiRequest
import io.embrace.android.embracesdk.comms.api.ApiResponseCache
import io.embrace.android.embracesdk.comms.api.ApiService
import io.embrace.android.embracesdk.comms.api.ApiUrlBuilder
import io.embrace.android.embracesdk.comms.api.EmbraceApiService
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.config.EmbraceConfigService
import io.embrace.android.embracesdk.config.local.LocalConfig
import io.embrace.android.embracesdk.gating.EmbraceGatingService
import io.embrace.android.embracesdk.gating.GatingService
import io.embrace.android.embracesdk.internal.BuildInfo
import io.embrace.android.embracesdk.internal.DeviceArchitecture
import io.embrace.android.embracesdk.internal.DeviceArchitectureImpl
import io.embrace.android.embracesdk.internal.SharedObjectLoader
import io.embrace.android.embracesdk.session.ActivityService
import io.embrace.android.embracesdk.session.EmbraceActivityService
import io.embrace.android.embracesdk.session.EmbraceMemoryCleanerService
import io.embrace.android.embracesdk.session.MemoryCleanerService
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
    val activityService: ActivityService
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

    private val backgroundExecutorService =
        workerThreadModule.backgroundExecutor(ExecutorName.BACKGROUND_REGISTRATION)

    override val memoryCleanerService: MemoryCleanerService by singleton {
        EmbraceMemoryCleanerService()
    }

    override val orientationService: OrientationService by singleton {
        // Embrace is not processing orientation changes on this moment, so return no-op service.
        NoOpOrientationService()
    }

    override val activityService: ActivityService by singleton {
        EmbraceActivityService(coreModule.application, orientationService, initModule.clock)
    }

    override val configService: ConfigService by singleton {
        configServiceProvider.invoke()
            ?: EmbraceConfigService(
                LocalConfig.fromResources(coreModule.resources, coreModule.context.packageName, customAppId, coreModule.jsonSerializer),
                { apiService },
                androidServicesModule.preferencesService,
                initModule.clock,
                coreModule.logger,
                backgroundExecutorService,
                coreModule.isDebug,
                configStopAction
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
            activityService,
            backgroundExecutorService,
            systemServiceModule.storageManager,
            systemServiceModule.windowManager,
            systemServiceModule.activityManager,
            initModule.clock,
            cpuInfoDelegate,
            deviceArchitecture
        )
    }

    override val urlBuilder by singleton {
        ApiUrlBuilder(
            configService,
            metadataService,
            enableIntegrationTesting,
            coreModule.isDebug
        )
    }

    override val cache by singleton {
        ApiResponseCache(
            coreModule.jsonSerializer,
            { File(coreModule.context.cacheDir, "emb_config_cache") }
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

    override val apiService: ApiService by singleton {
        EmbraceApiService(
            apiClient,
            urlBuilder,
            coreModule.jsonSerializer,
            { url: String, request: ApiRequest -> cache.retrieveCachedConfig(url, request) },
            coreModule.logger,
            metadataService,
            userService
        )
    }

    override val apiClient by singleton {
        ApiClient(
            coreModule.logger
        )
    }
}
