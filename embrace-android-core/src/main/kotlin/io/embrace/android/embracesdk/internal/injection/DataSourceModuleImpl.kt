package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.InstrumentationInstallArgsImpl
import io.embrace.android.embracesdk.internal.arch.DataCaptureOrchestrator
import io.embrace.android.embracesdk.internal.arch.EmbraceFeatureRegistry
import io.embrace.android.embracesdk.internal.worker.Worker

internal class DataSourceModuleImpl(
    initModule: InitModule,
    workerThreadModule: WorkerThreadModule,
    configModule: ConfigModule,
    essentialServiceModule: EssentialServiceModule,
    androidServicesModule: AndroidServicesModule,
    coreModule: CoreModule,
) : DataSourceModule {

    override val dataCaptureOrchestrator: DataCaptureOrchestrator by singleton {
        DataCaptureOrchestrator(
            workerThreadModule.backgroundWorker(Worker.Background.NonIoRegWorker),
            initModule.logger
        )
    }

    override val embraceFeatureRegistry: EmbraceFeatureRegistry = dataCaptureOrchestrator

    override val instrumentationContext by singleton {
        InstrumentationInstallArgsImpl(
            configService = configModule.configService,
            logger = initModule.logger,
            clock = initModule.clock,
            context = coreModule.context,
            application = coreModule.application,
            destination = essentialServiceModule.telemetryDestination,
            workerThreadModule = workerThreadModule,
            store = androidServicesModule.store,
        )
    }
}
