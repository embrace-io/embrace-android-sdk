package io.embrace.android.embracesdk.internal.arch

import io.embrace.android.embracesdk.internal.arch.attrs.EmbraceAttributeKey
import io.embrace.android.embracesdk.internal.arch.datasource.DataSource
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState
import kotlin.reflect.KClass

/**
 * Registry for all features whose instrumentation should be orchestrated by the Embrace SDK.
 */
interface InstrumentationRegistry : SessionEndListener, SessionChangeListener {

    /**
     * Adds a feature to the registry. The SDK will control when a feature is enabled/disabled
     * based on the declared values in the [state] parameter.
     */
    fun add(state: DataSourceState<*>)

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
    fun getCurrentStates(): Map<EmbraceAttributeKey, Any>
}
