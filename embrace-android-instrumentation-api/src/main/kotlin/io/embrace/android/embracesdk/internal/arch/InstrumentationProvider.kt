package io.embrace.android.embracesdk.internal.arch

import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState

/**
 * Provides instrumentation that can be initialized using SPI.
 */
interface InstrumentationProvider {

    /**
     * Registers instrumentation and returns a [DataSourceState] that can be controlled by the SDK.
     *
     * If instrumentation cannot be created for an expected reason (e.g. if the API level of the device
     * is too low), then it's permissible to return null
     */
    fun register(args: InstrumentationInstallArgs): DataSourceState<*>?
}
