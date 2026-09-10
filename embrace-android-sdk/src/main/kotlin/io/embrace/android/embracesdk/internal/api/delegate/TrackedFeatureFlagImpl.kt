package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.experiments.TrackedFeatureFlag

internal class TrackedFeatureFlagImpl(
    override val id: String,
    override val startedAt: Long?,
) : TrackedFeatureFlag
