package io.embrace.android.embracesdk.internal.arch

import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState

/**
 * Provides instrumentation that can be initialized using SPI.
 */
interface InstrumentationProvider {

    /**
     * Registers instrumentation and returns a [DataSourceState] that can be controlled by the SDK.
     */
    fun register(args: InstrumentationInstallArgs): DataSourceState<*>
}
