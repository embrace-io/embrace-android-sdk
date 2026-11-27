package io.embrace.android.embracesdk.experiments

import io.embrace.android.embracesdk.annotation.InternalApi

/**
 * A single feature flag that has been enabled on this app instance, with [variant] optionally naming the variation of the flag that
 * was applied.
 */
@OptIn(ExperimentalSubclassOptIn::class)
@SubclassOptInRequired(InternalApi::class)
public interface TrackedFeatureFlag : TrackedEntry
