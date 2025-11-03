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
    fun register(args: InstrumentationArgs): DataSourceState<*>?

    /**
     * The priority at which this instrumentation should be loaded. This is specified so that
     * instrumentation can be initialized in a deterministic order, if required. If no value is supplied,
     * the default value of 10000 will be used.
     *
     * 0 is the highest priority. Please try to use increments of 100 to make it easier to alter priorities
     * across multiple instrumentation in the future.
     */
    val priority: Int
        get() = 10000
}
