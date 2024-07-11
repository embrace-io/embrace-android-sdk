package io.embrace.android.embracesdk.arch

import io.embrace.android.embracesdk.arch.datasource.DataSourceState

/**
 * Registry for all features whose instrumentation should be orchestrated by the Embrace SDK.
 */
internal interface EmbraceFeatureRegistry {

    /**
     * Adds a feature to the registry. The SDK will control when a feature is enabled/disabled
     * based on the declared values in the [state] parameter.
     */
    fun add(state: DataSourceState<*>)
}
