package io.embrace.android.embracesdk.experiments

/**
 * Declares the experiments and feature flags that apply to this app instance.
 */
public interface ExperimentTrackingScope {

    /**
     * Declares an experiment membership with the unique ID [id], and optionally the [variant] in which this app instance has been
     * bucketed. [startedAt] is a timestamp in milliseconds from epoch observed from the client-side that denotes when the experiment
     * variant was first applied. If it is null, the time at which the enclosing call was made is used.
     */
    public fun experiment(id: String, variant: String? = null, startedAt: Long? = null)

    /**
     * Declares that the feature flag with the unique ID [id] is enabled on this app instance. [startedAt] is a timestamp in milliseconds
     * from epoch observed from the client-side that denotes when the flag was first applied. If it is null, the time at which the
     * enclosing call was made is used.
     */
    public fun featureFlag(id: String, startedAt: Long? = null)
}
