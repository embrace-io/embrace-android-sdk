package io.embrace.android.embracesdk.internal.otel.logs

import io.embrace.android.embracesdk.internal.arch.schema.PrivateSpan
import io.embrace.android.embracesdk.internal.otel.payload.toEmbracePayload
import io.embrace.android.embracesdk.internal.otel.sdk.StoreDataResult
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.kotlin.logging.export.LogRecordExporter
import io.opentelemetry.kotlin.logging.model.ReadableLogRecord

/**
 * Exports the given log record to a [LogSink]
 */
@ExperimentalApi
internal class DefaultLogRecordExporter(
    private val logSink: LogSink,
    private val externalExporters: List<LogRecordExporter>,
    private val exportCheck: () -> Boolean,
) : LogRecordExporter {

    override suspend fun export(telemetry: List<ReadableLogRecord>): OperationResultCode {
        if (!exportCheck()) {
            return OperationResultCode.Success
        }
        var result = logSink.storeLogs(telemetry.map(ReadableLogRecord::toEmbracePayload))

        EmbTrace.trace("otel-external-export") {
            if (externalExporters.isNotEmpty() && result == StoreDataResult.SUCCESS) {
                externalExporters.forEach { exporter ->
                    try {
                        exporter.export(
                            telemetry.filterNot {
                                it.attributes.containsKey(PrivateSpan.key.name)
                            }
                        )
                    } catch (ignored: Throwable) {
                        result = StoreDataResult.FAILURE
                    }
                }
            }
        }
        return when (result) {
            StoreDataResult.SUCCESS -> OperationResultCode.Success
            StoreDataResult.FAILURE -> OperationResultCode.Failure
        }
    }

    override suspend fun forceFlush(): OperationResultCode = OperationResultCode.Success
    override suspend fun shutdown(): OperationResultCode = OperationResultCode.Success
}
