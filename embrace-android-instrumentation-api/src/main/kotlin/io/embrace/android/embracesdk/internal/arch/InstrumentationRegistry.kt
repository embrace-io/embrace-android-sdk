package io.embrace.android.embracesdk.internal.arch

import io.embrace.android.embracesdk.internal.arch.datasource.DataSource
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceFactory
import kotlin.reflect.KClass

/**
 * Registry for all features whose instrumentation should be orchestrated by the Embrace SDK.
 */
interface InstrumentationRegistry : SessionPartEndListener, SessionPartChangeListener, AutoCloseable {

    /**
     * Creates a data source from [state] and adds it to the registry, enabling data capture.
     * Returns the data source, or null if [state] did not create one or the registry has been closed.
     */
    fun <T : DataSource> add(state: DataSourceFactory<T>): T?

    /**
     * Finds a feature by its DataSource type. This is generally discouraged but may be
     * required in some cases (e.g. when a manual API call by a library consumer also adds data).
     */
    fun <T : DataSource> findByType(clazz: KClass<T>): T?

    /**
     * Loads instrumentation via SPI and registers it with the SDK.
     *
     * [onDataSourceAdded] is invoked with each data source right after it is added, before the next provider registers, on
     * the thread that registered its provider. It is for setup that must be in place as soon as a data source exists.
     */
    fun loadInstrumentations(
        instrumentationProviders: Iterable<InstrumentationProvider>,
        args: InstrumentationArgs,
        onDataSourceAdded: (DataSource) -> Unit = {},
    )

    /**
     * Return a [Map] of states and their current values
     */
    fun getCurrentStates(): Map<String, Any>

    /**
     * Permanently disables data capture for every registered data source. Data sources created after this
     * call (e.g. by async instrumentation init) are disabled immediately rather than registered.
     */
    override fun close()
}
