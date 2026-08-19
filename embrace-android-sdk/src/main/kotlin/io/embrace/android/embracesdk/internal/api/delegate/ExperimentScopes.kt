package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.experiments.ExperimentTrackingScope
import io.embrace.android.embracesdk.experiments.ExperimentUntrackingScope
import io.embrace.android.embracesdk.internal.capture.experiment.ExperimentKind
import io.embrace.android.embracesdk.internal.capture.experiment.TrackedData
import io.embrace.android.embracesdk.internal.capture.experiment.UntrackedData

/**
 * Collects experiment entries declared in a DSL block.
 */
internal abstract class ExperimentScopeImpl<T>(
    private val now: () -> Long,
) {

    private val entries = mutableListOf<T>()
    private var callTimeMs: Long? = null
    private var sealed = false

    /**
     * Returns what was declared and prevents anything further being declared.
     */
    fun drain(): List<T> = synchronized(this) {
        sealed = true
        entries.toList()
    }

    /**
     * Resolves the timestamp used by declarations that omit one.
     */
    protected fun defaultTime(): Long = synchronized(this) {
        callTimeMs ?: now().also { callTimeMs = it }
    }

    protected fun add(entry: T) {
        synchronized(this) {
            if (!sealed) {
                entries.add(entry)
            }
        }
    }
}

internal class ExperimentTrackingScopeImpl(
    now: () -> Long,
) : ExperimentScopeImpl<TrackedData>(now), ExperimentTrackingScope {

    override fun experiment(id: String, variant: String?, startedAt: Long?) {
        add(TrackedData.Experiment(id = id, startTimeMs = startedAt ?: defaultTime(), variant = variant))
    }

    override fun featureFlag(id: String, startedAt: Long?) {
        add(TrackedData.FeatureFlag(id = id, startTimeMs = startedAt ?: defaultTime()))
    }
}

internal class ExperimentUntrackingScopeImpl(
    now: () -> Long,
) : ExperimentScopeImpl<UntrackedData>(now), ExperimentUntrackingScope {

    override fun experiment(id: String, endedAt: Long?) {
        add(UntrackedData(kind = ExperimentKind.EXPERIMENT, id = id, endTimeMs = endedAt ?: defaultTime()))
    }

    override fun featureFlag(id: String, endedAt: Long?) {
        add(UntrackedData(kind = ExperimentKind.FEATURE_FLAG, id = id, endTimeMs = endedAt ?: defaultTime()))
    }
}
