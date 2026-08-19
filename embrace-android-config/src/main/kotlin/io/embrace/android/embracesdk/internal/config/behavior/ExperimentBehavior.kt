package io.embrace.android.embracesdk.internal.config.behavior

/**
 * Configuration for the Experiments API
 */
interface ExperimentBehavior {

    /**
     * The combined maximum number of tracked active experiments and feature flags.
     */
    fun getMaxActiveCount(): Int

    /**
     * The maximum length of an ID for the experiments API.
     */
    fun getMaxIdLength(): Int

    /**
     * The maximum length of an experiment variant.
     */
    fun getMaxVariantLength(): Int
}
