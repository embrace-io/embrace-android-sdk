package io.embrace.android.embracesdk.internal.otel.wrapper

import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaLogRecordExporter
import io.embrace.opentelemetry.kotlin.logging.export.LogRecordExporter
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.logs.data.LogRecordData

@OptIn(ExperimentalApi::class)
class KotlinLogRecordExporterWrapper(
    private val impl: LogRecordExporter,
) : OtelJavaLogRecordExporter { // TODO: tests

    override fun export(logs: MutableCollection<LogRecordData>): CompletableResultCode {
        val code = impl.export(logs.map(LogRecordData::toReadableLogRecord))
        return code.toCompletableResultCode()
    }

    override fun flush(): CompletableResultCode = impl.forceFlush().toCompletableResultCode()

    override fun shutdown(): CompletableResultCode = impl.shutdown().toCompletableResultCode()
}
