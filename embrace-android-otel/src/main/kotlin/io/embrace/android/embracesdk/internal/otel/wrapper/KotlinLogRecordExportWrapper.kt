package io.embrace.android.embracesdk.internal.otel.wrapper

import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaLogRecordExporter
import io.embrace.opentelemetry.kotlin.export.OperationResultCode
import io.embrace.opentelemetry.kotlin.logging.export.LogRecordExporter
import io.embrace.opentelemetry.kotlin.logging.model.ReadableLogRecord

@OptIn(ExperimentalApi::class)
class KotlinLogRecordExportWrapper(
    private val impl: OtelJavaLogRecordExporter,
) : LogRecordExporter {

    override fun export(telemetry: List<ReadableLogRecord>): OperationResultCode {
        return impl.export(telemetry.map(ReadableLogRecord::toLogRecordData)).toOperationResultCode()
    }

    override fun forceFlush(): OperationResultCode = impl.flush().toOperationResultCode()
    override fun shutdown(): OperationResultCode = impl.shutdown().toOperationResultCode()
}
