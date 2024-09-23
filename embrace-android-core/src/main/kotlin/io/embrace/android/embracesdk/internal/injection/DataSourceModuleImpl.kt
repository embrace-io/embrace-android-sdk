package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.arch.DataCaptureOrchestrator
import io.embrace.android.embracesdk.internal.arch.EmbraceFeatureRegistry
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.worker.Worker

internal class DataSourceModuleImpl(
    initModule: InitModule,
    configService: ConfigService,
    workerThreadModule: WorkerThreadModule,
) : DataSourceModule {

    override val dataCaptureOrchestrator: DataCaptureOrchestrator by singleton {
        DataCaptureOrchestrator(
            configService,
            workerThreadModule.backgroundWorker(Worker.Background.NonIoRegWorker),
            initModule.logger
        )
    }

    override val embraceFeatureRegistry: EmbraceFeatureRegistry = dataCaptureOrchestrator
}
