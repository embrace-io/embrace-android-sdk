@file:OptIn(ExperimentalSemconv::class)

package io.embrace.android.embracesdk.internal.capture.experiment

import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.telemetry.AppliedLimitType
import io.embrace.android.embracesdk.internal.telemetry.TelemetryService
import io.embrace.android.embracesdk.semconv.EmbCommonAttributes
import io.embrace.android.embracesdk.semconv.ExperimentalSemconv

internal class ExperimentTrackingServiceImpl(
    configService: ConfigService,
    private val telemetryService: TelemetryService,
    private val telemetryDestination: TelemetryDestination,
) : ExperimentTrackingService {

    private val maxRecordCount = configService.experimentBehavior.getMaxExperimentCount()
    private val maxIdLength = configService.experimentBehavior.getMaxIdLength()
    private val maxVariantLength = configService.experimentBehavior.getMaxVariantLength()

    private val lock = Any()

    private val records = LinkedHashMap<RecordKey, ExperimentRecord>()
    private var cacheValid = false
    private var cachedRecords: String? = null

    override fun track(data: List<TrackedData>) {
        var updated = false
        synchronized(lock) {
            data.forEach { entry ->
                if (trackRecord(entry.toRecord())) {
                    updated = true
                }
            }
        }
        if (updated) {
            publishRecords()
        }
    }

    override fun untrack(kind: ExperimentKind, ids: List<String>, endTimeMs: Long) {
        var updated = false
        synchronized(lock) {
            ids.forEach { id ->
                if (untrackRecord(RecordKey(kind, id.stripWhitespace()), endTimeMs)) {
                    updated = true
                }
            }
        }
        if (updated) {
            publishRecords()
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

    private fun trackRecord(record: ExperimentRecord): Boolean {
        val key = RecordKey(record.kind, record.id)
        if (!isValid(record) || records.containsKey(key)) {
            return false
        }

        if (records.size >= maxRecordCount) {
            telemetryService.trackAppliedLimit("experiments", AppliedLimitType.DROP)
            return false
        }
        records[key] = record
        cacheValid = false
        return true
    }

    private fun untrackRecord(key: RecordKey, endTimeMs: Long): Boolean {
        val record = records[key]
        if (record == null || record.endTimeMs != null) {
            return false
        }
        records[key] = record.copy(endTimeMs = endTimeMs)
        cacheValid = false
        return true
    }

    private fun publishRecords() {
        getRecords()?.let { value ->
            telemetryDestination.addSessionPartAttribute(EmbCommonAttributes.EMB_EXPERIMENTS, value)
        }
    }

    private fun TrackedData.toRecord(): ExperimentRecord = when (this) {
        is TrackedData.Experiment -> ExperimentRecord(
            kind = ExperimentKind.EXPERIMENT,
            id = id.stripWhitespace(),
            variant = variant?.stripWhitespace()?.ifEmpty { null },
            startTimeMs = startTimeMs,
            endTimeMs = null,
        )

        is TrackedData.FeatureFlag -> ExperimentRecord(
            kind = ExperimentKind.FEATURE_FLAG,
            id = id.stripWhitespace(),
            variant = null,
            startTimeMs = startTimeMs,
            endTimeMs = null,
        )
    }

    private fun isValid(record: ExperimentRecord): Boolean {
        if (record.id.isEmpty() || record.id.length > maxIdLength) {
            return false
        }

        val variant = record.variant
        return (variant == null || variant.length <= maxVariantLength)
    }

    // Strips exactly the six ASCII whitespace code points (U+0009-U+000D and U+0020) from both ends. Deliberately
    // narrower than Char.isWhitespace(), which also matches code points the wire contract requires to be kept
    // (e.g. U+001C-U+001F, U+0085, U+00A0, U+3000).
    private fun String.stripWhitespace(): String = trim { it in '\u0009'..'\u000D' || it == ' ' }

    private data class RecordKey(
        val kind: ExperimentKind,
        val id: String,
    )
}
