package io.embrace.android.embracesdk.experiments

/**
 * Describes a single feature flag the app wants tracked: the flag applies to the device as of the
 * given time. Unlike an experiment, a feature flag has no variant.
 */
public class TrackedFeatureFlag(

    /**
     * The unique ID of the feature flag that applies to the device.
     */
    public val flagId: String,

    /**
     * The time at which the flag started applying to the device, in milliseconds since the epoch.
     */
    public val startTimeMs: Long,
)
