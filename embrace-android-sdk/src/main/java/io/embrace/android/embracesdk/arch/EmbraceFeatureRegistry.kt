package io.embrace.android.embracesdk.arch

import io.embrace.android.embracesdk.annotation.InternalApi
import io.embrace.android.embracesdk.arch.datasource.DataSourceState

/**
 * Registry for all features whose instrumentation should be orchestrated by the Embrace SDK.
 */
@InternalApi
public interface EmbraceFeatureRegistry {

    /**
     * Adds a feature to the registry. The SDK will control when a feature is enabled/disabled
     * based on the declared values in the [state] parameter.
     */
    public fun add(state: DataSourceState<*>)
}
