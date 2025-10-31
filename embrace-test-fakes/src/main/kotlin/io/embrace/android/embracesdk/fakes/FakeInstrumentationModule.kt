package io.embrace.android.embracesdk.fakes

import android.app.Application
import io.embrace.android.embracesdk.internal.arch.DataCaptureOrchestrator
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationRegistry
import io.embrace.android.embracesdk.internal.injection.InstrumentationModule

class FakeInstrumentationModule(application: Application) : InstrumentationModule {
    override val instrumentationRegistry: InstrumentationRegistry = DataCaptureOrchestrator(
        fakeBackgroundWorker(),
        FakeEmbLogger()
    )
    override val instrumentationArgs: InstrumentationArgs = FakeInstrumentationArgs(application)
}
