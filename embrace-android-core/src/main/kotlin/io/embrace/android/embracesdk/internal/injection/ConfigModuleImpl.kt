package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.Systrace
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.config.EmbraceConfigService
import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfig
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.worker.Worker

internal class ConfigModuleImpl(
    initModule: InitModule,
    openTelemetryModule: OpenTelemetryModule,
    workerThreadModule: WorkerThreadModule,
    androidServicesModule: AndroidServicesModule,
    framework: AppFramework,
    private val configServiceProvider: (framework: AppFramework) -> ConfigService? = { null },
    private val foregroundAction: ConfigService.() -> Unit,
    private val appIdFromConfig: String? = InstrumentedConfig.project.getAppId(),
) : ConfigModule {

    override val configService: ConfigService by singleton {
        Systrace.traceSynchronous("config-service-init") {
            configServiceProvider(framework)
                ?: EmbraceConfigService(
                    openTelemetryCfg = openTelemetryModule.openTelemetryConfiguration,
                    preferencesService = androidServicesModule.preferencesService,
                    clock = initModule.clock,
                    logger = initModule.logger,
                    backgroundWorker = workerThreadModule.backgroundWorker(Worker.Background.IoRegWorker),
                    suppliedFramework = framework,
                    foregroundAction = foregroundAction,
                    appIdFromConfig = appIdFromConfig,
                )
        }
    }
}
