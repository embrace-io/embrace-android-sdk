package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationRegistry

/**
 * Declares dependencies that manage all the instrumentation data sources used within the Embrace SDK.
 */
interface InstrumentationModule {
    val instrumentationRegistry: InstrumentationRegistry
    val instrumentationArgs: InstrumentationArgs
}
