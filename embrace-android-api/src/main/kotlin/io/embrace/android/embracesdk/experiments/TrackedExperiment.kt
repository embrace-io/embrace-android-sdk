package io.embrace.android.embracesdk.experiments

/**
 * Describes a single experiment/feature-flag the app wants tracked: the device has been bucketed
 * into the given variant of the given experiment at the given time.
 */
public class TrackedExperiment(

    /**
     * The unique ID of the experiment the device has been bucketed into.
     */
    public val experimentId: String,

    /**
     * The name of the variant/bucket the device has been assigned within the experiment.
     */
    public val variantName: String,

    /**
     * The time at which the device was bucketed, in milliseconds since the epoch.
     */
    public val startTimeMs: Long,
)
