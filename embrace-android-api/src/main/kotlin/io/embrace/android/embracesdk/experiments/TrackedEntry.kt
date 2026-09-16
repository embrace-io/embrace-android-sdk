package io.embrace.android.embracesdk.experiments

/**
 * The state shared by every experiment or feature flag tracked on this app instance. Use [TrackedExperiment] or [TrackedFeatureFlag]
 * to say which of the two is being tracked.
 */
public interface TrackedEntry {

    /**
     * The unique ID of the experiment or feature flag. IDs are unique within each kind, so an experiment and a feature flag may share
     * an ID.
     */
    public val id: String

    /**
     * Optional name of the variant that applies to this app instance: the bucket of an experiment, or the variation of a feature flag
     * that is not just enabled or disabled.
     */
    public val variant: String?

    /**
     * The time at which this started applying to the device, in milliseconds since the epoch. If null, the time at which the SDK is
     * told to track this entry will be used.
     */
    public val startedAt: Long?
}
