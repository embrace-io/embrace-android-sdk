package io.embrace.android.embracesdk.experiments

/**
 * An experiment this app instance has been bucketed into.
 */
public class TrackedExperiment(

    /**
     * The unique ID of the experiment.
     */
    public val id: String,

    /**
     * The time at which membership in this experiment began, in milliseconds since the epoch.
     */
    public val startTimeMs: Long,

    /**
     * Optional name of the variant in which this app instance has been bucketed into this experiment under.
     */
    public val variant: String? = null,
)
