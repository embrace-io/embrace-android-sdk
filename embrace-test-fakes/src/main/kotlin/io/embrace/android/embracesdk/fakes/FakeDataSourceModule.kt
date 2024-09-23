package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.DataCaptureOrchestrator
import io.embrace.android.embracesdk.internal.arch.EmbraceFeatureRegistry
import io.embrace.android.embracesdk.internal.injection.DataSourceModule

class FakeDataSourceModule : DataSourceModule {
    override val dataCaptureOrchestrator: DataCaptureOrchestrator =
        DataCaptureOrchestrator(
            FakeConfigService(),
            fakeBackgroundWorker(),
            FakeEmbLogger()
        )
    override val embraceFeatureRegistry: EmbraceFeatureRegistry = dataCaptureOrchestrator
}
