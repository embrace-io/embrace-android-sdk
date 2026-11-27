package io.embrace.android.embracesdk.experiments

import io.embrace.android.embracesdk.annotation.InternalApi

/**
 * An experiment this app instance has been bucketed into, with [variant] naming the bucket.
 */
@OptIn(ExperimentalSubclassOptIn::class)
@SubclassOptInRequired(InternalApi::class)
public interface TrackedExperiment : TrackedEntry
