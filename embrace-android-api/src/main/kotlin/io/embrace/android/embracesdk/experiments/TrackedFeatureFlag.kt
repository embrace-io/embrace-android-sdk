package io.embrace.android.embracesdk.experiments

/**
 * A single feature flag that has been enabled on this app instance.
 */
public class TrackedFeatureFlag(

    /**
     * The unique ID of the feature flag.
     */
    public val id: String,

    /**
     * The time at which the flag started applying to the device, in milliseconds since the epoch.
     */
    public val startTimeMs: Long,
)
