package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.experiments.TrackedExperiment

internal class TrackedExperimentImpl(
    override val id: String,
    override val variant: String?,
    override val startedAt: Long?,
) : TrackedExperiment
