package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.Systrace
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.config.EmbraceConfigService
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.worker.WorkerName

internal class ConfigModuleImpl(
    initModule: InitModule,
    openTelemetryModule: OpenTelemetryModule,
    workerThreadModule: WorkerThreadModule,
    androidServicesModule: AndroidServicesModule,
    customAppId: String?,
    framework: AppFramework,
    private val configServiceProvider: (framework: AppFramework) -> ConfigService? = { null },
    private val foregroundAction: ConfigService.() -> Unit
) : ConfigModule {

    override val configService: ConfigService by singleton {
        Systrace.traceSynchronous("config-service-init") {
            configServiceProvider(framework)
                ?: EmbraceConfigService(
                    customAppId,
                    openTelemetryModule.openTelemetryConfiguration,
                    androidServicesModule.preferencesService,
                    initModule.clock,
                    initModule.logger,
                    workerThreadModule.backgroundWorker(WorkerName.BACKGROUND_REGISTRATION),
                    framework,
                    foregroundAction
                )
        }
    }
}
