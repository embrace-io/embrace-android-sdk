package io.embrace.android.embracesdk.internal.api

import io.embrace.android.embracesdk.experiments.ExperimentTrackingScope
import io.embrace.android.embracesdk.experiments.ExperimentUntrackingScope

/**
 * The public API used to track experiment and feature-flag state on a given device for the current app instance. IDs are unique within
 * each kind, so an experiment and a feature flag may share an ID. The SDK does not persist this state, and users need to call this each
 * time the app starts up.
 */
public interface ExperimentApi {

    /**
     * Tracks the experiment memberships and enabled feature flags declared by [action], which are applied when [action] returns. For
     * example:
     *
     * ```
     * embrace.trackExperiments {
     *     experiment(id = "checkout-flow", variant = "variant-b")
     *     featureFlag(id = "new-nav")
     * }
     * ```
     *
     * Declarations are recorded in the order they were made. Declaring nothing has no effect. If [action] throws, whatever was declared
     * before the throw is still recorded and the throwable is rethrown to the caller. As [action] is not inlined, a bare `return` cannot
     * be used to leave it early.
     *
     * Any declaration that omits a start time uses the time at which this call was made. The [ExperimentTrackingScope] must only be used
     * inside [action]; declarations made through it afterwards are ignored.
     */
    public fun trackExperiments(action: ExperimentTrackingScope.() -> Unit)

    /**
     * Tracks a single experiment membership that this app instance has been bucketed into. A null [startedAt] means the time at which
     * this call is made will be used.
     */
    public fun trackExperiment(id: String, variant: String? = null, startedAt: Long? = null): Unit =
        trackExperiments { experiment(id, variant, startedAt) }

    /**
     * Tracks a single enabled feature flag that applies to this app instance. A null [startedAt] means the time at which this call
     * is made will be used.
     */
    public fun trackFeatureFlag(id: String, startedAt: Long? = null): Unit =
        trackExperiments { featureFlag(id, startedAt) }

    /**
     * Stops tracking the experiments and feature flags declared by [action], which are applied when [action] returns. For example:
     *
     * ```
     * embrace.untrackExperiments {
     *     experiment(id = "checkout-flow")
     *     featureFlag(id = "new-nav")
     * }
     * ```
     *
     * An experiment or feature flag can only be untracked after it has first been tracked, and untracking one that has not been tracked
     * has no effect. Declaring nothing has no effect. If [action] throws, whatever was declared before the throw is still applied and
     * the throwable is rethrown to the caller. As [action] is not inlined, a bare `return` cannot be used to leave it early.
     *
     * Any declaration that omits an end time uses the time at which this call was made. The [ExperimentUntrackingScope] must only be
     * used inside [action]; declarations made through it afterwards are ignored.
     */
    public fun untrackExperiments(action: ExperimentUntrackingScope.() -> Unit)

    /**
     * Stops tracking a single experiment on this app instance. A null [endedAt] means the time at which this call is made will be used.
     */
    public fun untrackExperiment(id: String, endedAt: Long? = null): Unit =
        untrackExperiments { experiment(id, endedAt) }

    /**
     * Stops tracking a single feature flag enablement on this app instance. A null [endedAt] means the time at which this call is made
     * will be used.
     */
    public fun untrackFeatureFlag(id: String, endedAt: Long? = null): Unit =
        untrackExperiments { featureFlag(id, endedAt) }
}
