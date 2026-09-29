package io.embrace.android.embracesdk.internal.otel.logs

import io.embrace.android.embracesdk.internal.arch.schema.PrivateSpan
import io.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.kotlin.logging.data.LogRecordData
import io.opentelemetry.kotlin.logging.export.LogRecordExporter

/**
 * Wraps a [LogRecordExporter] supplied by the SDK user so that it never sees log records that are private
 * to Embrace.
 */
internal class PrivateTelemetryFilteringLogRecordExporter(
    private val delegate: LogRecordExporter,
) : LogRecordExporter by delegate {

    override suspend fun export(telemetry: List<LogRecordData>): OperationResultCode =
        delegate.export(telemetry.filterNot { it.attributes.containsKey(PrivateSpan.key) })
}
