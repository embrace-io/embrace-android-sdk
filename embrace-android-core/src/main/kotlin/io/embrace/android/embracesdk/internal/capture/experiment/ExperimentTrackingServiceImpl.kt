package io.embrace.android.embracesdk.internal.capture.experiment

import io.embrace.android.embracesdk.internal.telemetry.AppliedLimitType
import io.embrace.android.embracesdk.internal.telemetry.TelemetryService

internal class ExperimentTrackingServiceImpl(
    private val telemetryService: TelemetryService,
) : ExperimentTrackingService {

    private val lock = Any()
    private val records = mutableListOf<ExperimentRecord>()
    private var listener: (() -> Unit)? = null

    override fun trackExperiments(experiments: List<TrackedExperimentData>): Boolean =
        trackRecords(
            newRecords = experiments.filter { it.experimentId.isNotBlank() }.map {
                ExperimentRecord(
                    type = ExperimentRecordType.EXPERIMENT,
                    id = it.experimentId,
                    variant = it.variantName,
                    startTimeMs = it.startTimeMs,
                    endTimeMs = null,
                )
            },
            requestedCount = experiments.size,
        )

    override fun untrackExperiments(experimentIds: List<String>, endTimeMs: Long): Boolean =
        untrackRecords(ExperimentRecordType.EXPERIMENT, experimentIds, endTimeMs)

    override fun trackFeatureFlags(flags: List<TrackedFeatureFlagData>): Boolean =
        trackRecords(
            newRecords = flags.filter { it.flagId.isNotBlank() }.map {
                ExperimentRecord(
                    type = ExperimentRecordType.FEATURE_FLAG,
                    id = it.flagId,
                    variant = null,
                    startTimeMs = it.startTimeMs,
                    endTimeMs = null,
                )
            },
            requestedCount = flags.size,
        )

    override fun untrackFeatureFlags(flagIds: List<String>, endTimeMs: Long): Boolean =
        untrackRecords(ExperimentRecordType.FEATURE_FLAG, flagIds, endTimeMs)

    override fun getRecords(): String? = synchronized(lock) {
        serializeRecords()
    }

    override fun addChangeListener(listener: () -> Unit) {
        this.listener = listener
    }

    private fun trackRecords(newRecords: List<ExperimentRecord>, requestedCount: Int): Boolean {
        synchronized(lock) {
            // flush-then-mutate: cut the in-flight log batch under the OLD state before the new
            // records become visible. Safe to re-enter this monitor from the same thread (the
            // flush resolves envelope metadata via getRecords()); the built envelope captures
            // the old records string.
            listener?.invoke()
            var allAccepted = newRecords.size == requestedCount
            val trackedKeys = records.mapTo(mutableSetOf()) { it.key() }
            newRecords.forEach { record ->
                if (record.key() !in trackedKeys && trackedKeys.size >= EXPERIMENT_ID_CAP) {
                    telemetryService.trackAppliedLimit("experiment", AppliedLimitType.DROP)
                    allAccepted = false
                } else {
                    trackedKeys.add(record.key())
                    records.add(record)
                }
            }
            return allAccepted
        }
    }

    private fun untrackRecords(type: ExperimentRecordType, ids: List<String>, endTimeMs: Long): Boolean {
        synchronized(lock) {
            listener?.invoke()
            var allAccepted = true
            ids.forEach { id ->
                val key = "${type.code}:$id"
                // untracking requires an actively-tracked record: close the latest one by setting
                // its end time in place rather than appending a separate record
                val index = records.indexOfLast { it.key() == key && it.endTimeMs == null }
                if (index >= 0) {
                    records[index] = records[index].copy(endTimeMs = endTimeMs)
                } else {
                    allAccepted = false
                }
            }
            return allAccepted
        }
    }

    private fun serializeRecords(): String? = records.ifEmpty { null }?.joinToString(";") { it.serialize() }

    private companion object {

        // shared cap on distinct (type, id) entries — experiments and feature flags combined.
        // prototype: remote-configurable via a new ExperimentBehavior (max_experiment_count) in the real impl
        private const val EXPERIMENT_ID_CAP = 500
    }
}
