package io.embrace.android.embracesdk.experiments

/**
 * A single feature flag that has been enabled on this app instance.
 */
public interface TrackedFeatureFlag {

    /**
     * The unique ID of the feature flag.
     */
    public val id: String

    /**
     * The time at which the flag started applying to the device, in milliseconds since the epoch. If null, the time at which the SDK is
     * told to track this feature flag will be used.
     */
    public val startedAt: Long?
}

internal class TrackedFeatureFlagImpl(
    override val id: String,
    override val startedAt: Long?,
) : TrackedFeatureFlag
