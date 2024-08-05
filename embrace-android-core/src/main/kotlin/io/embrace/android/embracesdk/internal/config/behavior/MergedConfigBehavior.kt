package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.internal.utils.Provider

/**
 * Merges multiple sources of config and tells the SDK how its functionality should behave. This
 * means the caller doesn't need to worry about whether the remote config has been fetched or its
 * precedence rules - it just gets told whether it should enable something or not.
 *
 * There are three sources of config: remote (from the config endpoint); local (from the
 * embrace-config.json); and default (defined in subclasses of this type).
 *
 * Config is typically evaluated in the following precedence: Remote > Local > Default. Remote/local
 * configs might not exist for every single field, as it doesn't always make sense for every value
 * to be configurable by end-users. However, there should always be a default value.
 */
public open class MergedConfigBehavior<L, R>(

    /**
     * Checks whether percent-based thresholds should be enabled or not. We should always return
     * booleans about whether functionality is enabled - and should never expose percentages etc
     * to the caller.
     */
    protected val thresholdCheck: BehaviorThresholdCheck,

    /**
     * Supplier for local config, from the embrace-config.json file.
     */
    private val localSupplier: Provider<L?> = { null },

    /**
     * Supplier for remote config, from the config endpoint.
     */
    private val remoteSupplier: Provider<R?> = { null }
) {

    /**
     * The local config. This property always returns the most up-to-date value, or null if
     * no local config is available.
     */
    protected val local: L?
        get() = localSupplier()

    /**
     * The remote config. This property always returns the most up-to-date value, or null if
     * no remote config is available.
     */
    protected val remote: R?
        get() = remoteSupplier()
}
