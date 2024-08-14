package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.Systrace
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.config.EmbraceConfigService
import io.embrace.android.embracesdk.internal.config.LocalConfigParser
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.worker.WorkerName

internal class ConfigModuleImpl(
    initModule: InitModule,
    coreModule: CoreModule,
    openTelemetryModule: OpenTelemetryModule,
    workerThreadModule: WorkerThreadModule,
    androidServicesModule: AndroidServicesModule,
    customAppId: String?,
    framework: AppFramework,
    private val configServiceProvider: (framework: AppFramework) -> ConfigService? = { null },
    private val foregroundAction: ConfigService.() -> Unit
) : ConfigModule {

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

    override val configService: ConfigService by singleton {
        Systrace.traceSynchronous("config-service-init") {
            configServiceProvider(framework)
                ?: EmbraceConfigService(
                    localConfig,
                    androidServicesModule.preferencesService,
                    initModule.clock,
                    initModule.logger,
                    workerThreadModule.backgroundWorker(WorkerName.BACKGROUND_REGISTRATION),
                    coreModule.isDebug,
                    framework,
                    foregroundAction
                )
        }
    }
}
