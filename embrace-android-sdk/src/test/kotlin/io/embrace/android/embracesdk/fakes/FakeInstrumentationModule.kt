package io.embrace.android.embracesdk.fakes

import android.app.Application
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationRegistry
import io.embrace.android.embracesdk.internal.arch.InstrumentationRegistryImpl
import io.embrace.android.embracesdk.internal.injection.InstrumentationModule

class FakeInstrumentationModule(
    application: Application,
    private val errorHandler: FakeErrorHandler = FakeErrorHandler(),
    override val instrumentationArgs: InstrumentationArgs =
        FakeInstrumentationArgs(application, errorHandler = errorHandler),
) : InstrumentationModule {
    override val instrumentationRegistry: InstrumentationRegistry = InstrumentationRegistryImpl(
        errorHandler,
    )
}
