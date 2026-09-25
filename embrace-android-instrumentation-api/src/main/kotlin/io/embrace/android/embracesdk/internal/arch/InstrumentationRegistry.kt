package io.embrace.android.embracesdk.internal.arch

import io.embrace.android.embracesdk.internal.arch.datasource.DataSource
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState
import kotlin.reflect.KClass

/**
 * Registry for all features whose instrumentation should be orchestrated by the Embrace SDK.
 */
interface InstrumentationRegistry : SessionPartEndListener, SessionPartChangeListener {

    /**
     * Creates a data source from [state] and adds it to the registry, enabling data capture.
     * Returns the data source, or null if [state] did not create one.
     */
    fun <T : DataSource> add(state: DataSourceState<T>): T?

    /**
     * Finds a feature by its DataSource type. This is generally discouraged but may be
     * required in some cases (e.g. when a manual API call by a library consumer also adds data).
     */
    fun <T : DataSource> findByType(clazz: KClass<T>): T?

    /**
     * Loads instrumentation via SPI and registers it with the SDK.
     */
    fun loadInstrumentations(
        instrumentationProviders: Iterable<InstrumentationProvider>,
        args: InstrumentationArgs,
    )

    /**
     * Return a [Map] of states and their current values
     */
    fun getCurrentStates(): Map<String, Any>
}
