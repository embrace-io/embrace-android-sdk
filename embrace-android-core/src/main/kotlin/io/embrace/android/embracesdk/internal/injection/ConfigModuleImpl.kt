package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.Systrace
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.config.EmbraceConfigService
import io.embrace.android.embracesdk.internal.config.RemoteConfigSource
import io.embrace.android.embracesdk.internal.config.RemoteConfigSourceImpl
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.worker.Worker

internal class ConfigModuleImpl(
    initModule: InitModule,
    openTelemetryModule: OpenTelemetryModule,
    workerThreadModule: WorkerThreadModule,
    androidServicesModule: AndroidServicesModule,
    framework: AppFramework,
    foregroundAction: () -> Unit,
    remoteConfigSourceProvider: Provider<RemoteConfigSource?> = { null },
) : ConfigModule {

    override val configService: ConfigService by singleton {
        Systrace.traceSynchronous("config-service-init") {
            EmbraceConfigService(
                openTelemetryCfg = openTelemetryModule.openTelemetryConfiguration,
                preferencesService = androidServicesModule.preferencesService,
                suppliedFramework = framework,
                instrumentedConfig = initModule.instrumentedConfig,
                configProvider = remoteConfigSource::getConfig,
            )
        }
    }

    override val remoteConfigSource by singleton {
        remoteConfigSourceProvider() ?: RemoteConfigSourceImpl(
            clock = initModule.clock,
            backgroundWorker = workerThreadModule.backgroundWorker(Worker.Background.IoRegWorker),
            foregroundAction = foregroundAction,
        )
    }
}
