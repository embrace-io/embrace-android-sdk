package io.embrace.android.embracesdk.internal.config

import android.os.Build
import io.embrace.android.embracesdk.core.BuildConfig
import io.embrace.android.embracesdk.internal.injection.CoreModule
import io.embrace.android.embracesdk.internal.injection.InitModule
import io.embrace.android.embracesdk.internal.injection.OpenTelemetryModule
import io.embrace.android.embracesdk.internal.injection.WorkerThreadModule
import io.embrace.android.embracesdk.internal.injection.singleton
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.worker.Worker

class ConfigModuleImpl(
    initModule: InitModule,
    coreModule: CoreModule,
    openTelemetryModule: OpenTelemetryModule,
    workerThreadModule: WorkerThreadModule,
) : ConfigModule {

    override val configService: ConfigService by singleton {
        EmbTrace.trace("config-service-init") {
            ConfigServiceImpl(
                instrumentedConfig = initModule.instrumentedConfig,
                worker = workerThreadModule.backgroundWorker(Worker.Background.IoRegWorker),
                serializer = initModule.jsonSerializer,
                okHttpClient = initModule.okHttpClient,
                hasConfiguredOtelExporters = openTelemetryModule.otelSdkConfig::hasConfiguredOtelExporters,
                sdkVersion = BuildConfig.VERSION_NAME,
                apiLevel = Build.VERSION.SDK_INT,
                filesDir = coreModule.context.filesDir,
                store = coreModule.store,
                abis = Build.SUPPORTED_ABIS,
                logger = initModule.logger,
            )
        }
    }
}
