package io.embrace.android.embracesdk.fakes

import android.app.Application
import io.embrace.android.embracesdk.internal.arch.DataCaptureOrchestrator
import io.embrace.android.embracesdk.internal.arch.EmbraceFeatureRegistry
import io.embrace.android.embracesdk.internal.arch.InstrumentationInstallArgs
import io.embrace.android.embracesdk.internal.injection.DataSourceModule

class FakeDataSourceModule(application: Application) : DataSourceModule {
    override val dataCaptureOrchestrator: DataCaptureOrchestrator =
        DataCaptureOrchestrator(
            fakeBackgroundWorker(),
            FakeEmbLogger()
        )
    override val embraceFeatureRegistry: EmbraceFeatureRegistry = dataCaptureOrchestrator
    override val instrumentationContext: InstrumentationInstallArgs = FakeInstrumentationInstallArgs(application)
}
