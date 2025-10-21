package io.embrace.android.embracesdk.internal.config.behavior

/**
 * Determines the SDK's behavior at runtime. These values are immutable for the process lifetime.
 */
interface ConfigBehavior<L, R> {

    /**
     * The local config.
     */
    val local: L

    /**
     * The remote config.
     */
    val remote: R?
}
