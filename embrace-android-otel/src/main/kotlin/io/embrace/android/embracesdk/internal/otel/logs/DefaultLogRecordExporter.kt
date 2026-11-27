package io.embrace.android.embracesdk.internal.otel.logs

import io.embrace.android.embracesdk.internal.otel.export.ExternalExportDispatcher
import io.embrace.android.embracesdk.internal.otel.export.InlineExporter
import io.embrace.android.embracesdk.internal.otel.payload.toEmbraceAttributes
import io.embrace.android.embracesdk.internal.otel.payload.toEmbracePayload
import io.embrace.android.embracesdk.internal.otel.sdk.StoreDataResult
import io.embrace.android.embracesdk.internal.otel.state.StateTelemetry
import io.embrace.android.embracesdk.internal.payload.Log
import io.opentelemetry.kotlin.attributes.AttributesMutator
import io.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.kotlin.logging.data.LogRecordData
import io.opentelemetry.kotlin.logging.export.LogRecordExporter
import io.opentelemetry.kotlin.logging.model.ReadableLogRecord

/**
 * Exports the given log record to a [LogSink]
 */
internal class DefaultLogRecordExporter(
    private val logSink: LogSink,
    private val externalExporters: List<LogRecordExporter>,
    private val exportCheck: () -> Boolean,
    private val externalExportDispatcher: ExternalExportDispatcher,
    private val stateTelemetry: StateTelemetry? = null,
) : LogRecordExporter, InlineExporter<LogRecordData> {

    override fun exportInline(telemetry: List<LogRecordData>): OperationResultCode {
        if (!exportCheck()) {
            return OperationResultCode.Success
        }
        val result = when (stateTelemetry) {
            null -> logSink.storeLogs(telemetry.map(LogRecordData::toEmbracePayload))
            else -> storeStamped(stateTelemetry, telemetry)
        }

        if (result == StoreDataResult.SUCCESS && externalExporters.isNotEmpty()) {
            val snapshot = telemetry.map { (it as? ReadableLogRecord)?.toLogRecordData() ?: it }
            externalExportDispatcher.dispatch(externalExporters) { it.export(snapshot) }
        }

        return when (result) {
            StoreDataResult.SUCCESS -> OperationResultCode.Success
            StoreDataResult.FAILURE -> OperationResultCode.Failure
        }
    }

    /**
     * Stores [telemetry] for Embrace with the state stamp in its packed form, then writes the stamp on each record in the form the
     * app's own processors and exporters take, which are given the record after this.
     *
     * A log about what is happening now is stamped with the state now. A log about a process that has died keeps the state it was
     * given, which is that process's, and is never stamped with this one's. A change or a snapshot replayed for the app's own
     * exporters is not stamped, and is not stored for Embrace at all.
     */
    private fun storeStamped(state: StateTelemetry, telemetry: List<LogRecordData>): StoreDataResult {
        val stored = ArrayList<Log>(telemetry.size)
        for (record in telemetry) {
            // read once: in compat mode a getter on the live record copies it
            val attributes = record.attributes
            when (state.kindOf(record.eventName, attributes)) {
                StateTelemetry.LogKind.REPLAY -> Unit
                StateTelemetry.LogKind.DEAD_PROCESS -> {
                    stored.add(record.toEmbracePayload(state.packPlainStamp(attributes)))
                    state.writeDeadProcessSeq(record, attributes)
                }

                StateTelemetry.LogKind.LIVE -> {
                    val snapshot = state.recorder.current()
                    if (snapshot.seq == 0L) {
                        stored.add(record.toEmbracePayload(attributes.toEmbraceAttributes()))
                    } else {
                        stored.add(record.toEmbracePayload(state.withPackedStamp(attributes, snapshot)))
                        if (state.appReceivesLogs()) {
                            (record as? AttributesMutator)?.let { state.writeLogExportForm(it, snapshot) }
                        }
                    }
                }
            }
        }
        return if (stored.isEmpty()) StoreDataResult.SUCCESS else logSink.storeLogs(stored)
    }

    override suspend fun export(telemetry: List<LogRecordData>): OperationResultCode = exportInline(telemetry)

    override suspend fun forceFlush(): OperationResultCode {
        externalExportDispatcher.awaitPendingExports()
        return OperationResultCode.Success
    }

    override suspend fun shutdown(): OperationResultCode = OperationResultCode.Success
}
