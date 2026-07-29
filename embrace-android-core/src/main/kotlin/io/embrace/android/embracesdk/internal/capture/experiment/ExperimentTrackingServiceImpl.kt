package io.embrace.android.embracesdk.internal.capture.experiment

import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.telemetry.AppliedLimitType
import io.embrace.android.embracesdk.internal.telemetry.TelemetryService

internal class ExperimentTrackingServiceImpl(
    configService: ConfigService,
    private val telemetryService: TelemetryService,
) : ExperimentTrackingService {

    private val maxActiveCount = configService.experimentBehavior.getMaxActiveCount()
    private val maxIdLength = configService.experimentBehavior.getMaxIdLength()
    private val maxVariantLength = configService.experimentBehavior.getMaxVariantLength()

    private val lock = Any()

    private val records = LinkedHashMap<String, ExperimentRecord>()
    private var activeCount = 0
    private var cacheValid = false
    private var cachedRecords: String? = null

    override fun track(data: List<TrackedData>) {
        synchronized(lock) {
            data.forEach { entry ->
                trackRecord(entry.toRecord())
            }
        }
    }

    override fun untrack(ids: List<String>, endTimeMs: Long) {
        synchronized(lock) {
            ids.forEach { id ->
                untrackRecord(id, endTimeMs)
            }
        }
    }

    override fun getRecords(): String? = synchronized(lock) {
        if (!cacheValid) {
            cachedRecords = if (records.isEmpty()) {
                null
            } else {
                records.values.joinToString(";") { it.serialize() }
            }
            cacheValid = true
        }
        cachedRecords
    }

    private fun trackRecord(record: ExperimentRecord) {
        synchronized(lock) {
            if (!isValid(record)) {
                return
            }

            if (records.containsKey(record.id)) {
                return
            }

            if (activeCount >= maxActiveCount) {
                telemetryService.trackAppliedLimit("experiment", AppliedLimitType.DROP)
                return
            }
            records[record.id] = record
            activeCount++
            cacheValid = false
        }
    }

    private fun untrackRecord(id: String, endTimeMs: Long) {
        synchronized(lock) {
            val record = records[id] ?: return
            if (record.endTimeMs == null) {
                records[id] = record.copy(endTimeMs = endTimeMs)
                activeCount--
                cacheValid = false
            }
        }
    }

    private fun TrackedData.toRecord(): ExperimentRecord = when (this) {
        is TrackedData.Experiment -> ExperimentRecord(
            kind = ExperimentKind.EXPERIMENT,
            id = id,
            variant = variant,
            startTimeMs = startTimeMs,
            endTimeMs = null,
        )

        is TrackedData.FeatureFlag -> ExperimentRecord(
            kind = ExperimentKind.FEATURE_FLAG,
            id = id,
            variant = null,
            startTimeMs = startTimeMs,
            endTimeMs = null,
        )
    }

    private fun isValid(record: ExperimentRecord): Boolean {
        if (record.id.isBlank()) {
            return false
        }
        if (record.id.length > maxIdLength) {
            return false
        }
        val variant = record.variant
        if (variant != null && variant.length > maxVariantLength) {
            return false
        }
        return true
    }
}
