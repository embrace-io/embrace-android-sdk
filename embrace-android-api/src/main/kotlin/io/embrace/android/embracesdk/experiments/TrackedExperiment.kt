package io.embrace.android.embracesdk.experiments

/**
 * An experiment this app instance has been bucketed into.
 */
public interface TrackedExperiment {

    /**
     * The unique ID of the experiment.
     */
    public val id: String

    /**
     * Optional name of the variant in which this app instance has been bucketed into this experiment under.
     */
    public val variant: String?

    /**
     * The time at which membership in this experiment began, in milliseconds since the epoch. If null, the time at which the SDK is
     * told to track this experiment will be used.
     */
    public val startedAt: Long?
}

internal class TrackedExperimentImpl(
    override val id: String,
    override val variant: String?,
    override val startedAt: Long?,
) : TrackedExperiment
