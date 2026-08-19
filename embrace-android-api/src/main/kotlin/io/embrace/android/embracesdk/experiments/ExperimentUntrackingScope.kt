package io.embrace.android.embracesdk.experiments

/**
 * Declares the experiments and feature flags that no longer apply to this app instance.
 */
public interface ExperimentUntrackingScope {

    /**
     * Stops tracking the experiment with the unique ID [id]. An experiment can only be untracked after it has first been tracked, so
     * untracking an experiment that has not been tracked has no effect. [endedAt] is a timestamp in milliseconds from epoch observed
     * from the client-side that denotes when membership in the experiment ended. If it is null, the time at which the enclosing call
     * was made is used.
     */
    public fun experiment(id: String, endedAt: Long? = null)

    /**
     * Stops tracking the feature flag with the unique ID [id]. A feature flag can only be untracked after it has first been tracked, so
     * untracking a feature flag that has not been enabled has no effect. [endedAt] is a timestamp in milliseconds from epoch observed
     * from the client-side that denotes when the flag stopped applying. If it is null, the time at which the enclosing call was made is
     * used.
     */
    public fun featureFlag(id: String, endedAt: Long? = null)
}
