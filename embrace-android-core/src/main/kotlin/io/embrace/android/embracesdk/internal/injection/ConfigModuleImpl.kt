package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.Systrace
import io.embrace.android.embracesdk.internal.comms.api.ApiUrlBuilder
import io.embrace.android.embracesdk.internal.comms.api.EmbraceApiUrlBuilder
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.config.ConfigServiceImpl
import io.embrace.android.embracesdk.internal.config.source.RemoteConfigSource
import io.embrace.android.embracesdk.internal.config.source.RemoteConfigSourceImpl
import io.embrace.android.embracesdk.internal.config.store.RemoteConfigStore
import io.embrace.android.embracesdk.internal.config.store.RemoteConfigStoreImpl
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.worker.Worker
import java.io.File

internal class ConfigModuleImpl(
    initModule: InitModule,
    coreModule: CoreModule,
    openTelemetryModule: OpenTelemetryModule,
    workerThreadModule: WorkerThreadModule,
    androidServicesModule: AndroidServicesModule,
    framework: AppFramework,
    foregroundAction: () -> Unit,
    remoteConfigSourceProvider: Provider<RemoteConfigSource?> = { null },
) : ConfigModule {

    override val configService: ConfigService by singleton {
        Systrace.traceSynchronous("config-service-init") {
            ConfigServiceImpl(
                openTelemetryCfg = openTelemetryModule.openTelemetryConfiguration,
                preferencesService = androidServicesModule.preferencesService,
                suppliedFramework = framework,
                instrumentedConfig = initModule.instrumentedConfig,
                configProvider = { remoteConfigSource?.getConfig() },
            )
        }
    }

    override val remoteConfigSource by singleton {
        urlBuilder ?: return@singleton null
        remoteConfigSourceProvider() ?: RemoteConfigSourceImpl(
            clock = initModule.clock,
            backgroundWorker = workerThreadModule.backgroundWorker(Worker.Background.IoRegWorker),
            foregroundAction = foregroundAction,
        )
    }

    override val remoteConfigStore: RemoteConfigStore by singleton {
        RemoteConfigStoreImpl(
            serializer = initModule.jsonSerializer,
            storageDir = File(coreModule.context.filesDir, "embrace_remote_config"),
        )
    }

    override val urlBuilder: ApiUrlBuilder? by singleton {
        configService.appId ?: return@singleton null
        Systrace.traceSynchronous("url-builder-init") {
            EmbraceApiUrlBuilder(
                deviceId = androidServicesModule.preferencesService.deviceIdentifier,
                appVersionName = coreModule.packageVersionInfo.versionName,
                instrumentedConfig = initModule.instrumentedConfig,
            )
        }
    }
}
