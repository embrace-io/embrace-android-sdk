package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.otel.export.LogInlineExporter
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.kotlin.logging.data.LogRecordData
import io.opentelemetry.kotlin.logging.export.LogRecordExporter
import java.util.IdentityHashMap

class FakeLogRecordExporter : LogRecordExporter, LogInlineExporter {

    val exportedLogs: MutableList<LogRecordData> = mutableListOf()

    /**
     * The attributes each log was stored with beyond its own, by the log, for a log exported with some.
     */
    val payloadAttributes: MutableMap<LogRecordData, List<Attribute>> = IdentityHashMap()

    /**
     * What each log's attributes were when it was stored, by the log, for a log exported with payload attributes.
     */
    val attributesAtStore: MutableMap<LogRecordData, Map<String, Any>> = IdentityHashMap()

    /**
     * Whether an export with payload attributes calls its `afterStore`, as the real exporter does unless export is disabled.
     */
    var callsAfterStore: Boolean = true
    var forceFlushCount: Int = 0
        private set
    var shutdownCount: Int = 0
        private set

    override fun exportInline(telemetry: List<LogRecordData>): OperationResultCode {
        exportedLogs += telemetry
        return OperationResultCode.Success
    }

    override fun exportInline(
        log: LogRecordData,
        payloadAttributes: List<Attribute>,
        afterStore: () -> Unit,
    ): OperationResultCode {
        this.payloadAttributes[log] = payloadAttributes
        attributesAtStore[log] = log.attributes.toMap()
        if (callsAfterStore) {
            afterStore()
        }
        return exportInline(listOf(log))
    }

    override suspend fun export(telemetry: List<LogRecordData>): OperationResultCode = exportInline(telemetry)

    override suspend fun shutdown(): OperationResultCode {
        shutdownCount++
        return OperationResultCode.Success
    }

    override suspend fun forceFlush(): OperationResultCode {
        forceFlushCount++
        return OperationResultCode.Success
    }
}
