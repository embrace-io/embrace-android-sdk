package io.embrace.android.embracesdk.fakes

import android.app.Application
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationRegistry
import io.embrace.android.embracesdk.internal.arch.InstrumentationRegistryImpl
import io.embrace.android.embracesdk.internal.injection.InstrumentationModule

class FakeInstrumentationModule(
    application: Application,
    private val logger: FakeInternalLogger = FakeInternalLogger(),
    override val instrumentationArgs: InstrumentationArgs = FakeInstrumentationArgs(application, logger = logger)
) : InstrumentationModule {
    override val instrumentationRegistry: InstrumentationRegistry = InstrumentationRegistryImpl(
        logger
    )
}
