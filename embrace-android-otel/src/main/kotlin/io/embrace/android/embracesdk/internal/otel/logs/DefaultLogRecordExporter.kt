package io.embrace.android.embracesdk.internal.otel.logs

import io.embrace.android.embracesdk.internal.otel.payload.toEmbracePayload
import io.embrace.android.embracesdk.internal.otel.schema.PrivateSpan
import io.embrace.android.embracesdk.internal.otel.sdk.StoreDataResult
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.export.OperationResultCode
import io.embrace.opentelemetry.kotlin.logging.export.LogRecordExporter
import io.embrace.opentelemetry.kotlin.logging.model.ReadableLogRecord

/**
 * Exports the given log record to a [LogSink]
 */
@ExperimentalApi
internal class DefaultLogRecordExporter(
    private val logSink: LogSink,
    private val externalExporters: List<LogRecordExporter>,
    private val exportCheck: () -> Boolean,
) : LogRecordExporter {

    override fun export(telemetry: List<ReadableLogRecord>): OperationResultCode {
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

    override fun forceFlush(): OperationResultCode = OperationResultCode.Success
    override fun shutdown(): OperationResultCode = OperationResultCode.Success
}
