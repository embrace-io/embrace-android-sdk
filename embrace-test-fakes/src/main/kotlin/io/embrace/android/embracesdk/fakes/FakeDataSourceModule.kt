package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.concurrency.BlockableExecutorService
import io.embrace.android.embracesdk.internal.arch.DataCaptureOrchestrator
import io.embrace.android.embracesdk.internal.arch.EmbraceFeatureRegistry
import io.embrace.android.embracesdk.internal.injection.DataSourceModule
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker

class FakeDataSourceModule : DataSourceModule {
    override val dataCaptureOrchestrator: DataCaptureOrchestrator =
        DataCaptureOrchestrator(
            FakeConfigService(),
            BackgroundWorker(BlockableExecutorService()),
            FakeEmbLogger()
        )
    override val embraceFeatureRegistry: EmbraceFeatureRegistry = dataCaptureOrchestrator
}
