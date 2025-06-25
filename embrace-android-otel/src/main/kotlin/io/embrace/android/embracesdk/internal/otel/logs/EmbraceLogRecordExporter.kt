package io.embrace.android.embracesdk.internal.otel.logs

import io.embrace.android.embracesdk.internal.otel.schema.PrivateSpan
import io.embrace.android.embracesdk.internal.otel.sdk.hasEmbraceAttribute
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaCompletableResultCode
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaLogRecordData
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaLogRecordExporter

/**
 * Exports the given [LogRecordData] to a [LogSink]
 */
internal class EmbraceLogRecordExporter(
    private val logSink: LogSink,
    private val externalLogRecordExporter: OtelJavaLogRecordExporter?,
    private val exportCheck: () -> Boolean,
) : OtelJavaLogRecordExporter {

    override fun export(logs: Collection<OtelJavaLogRecordData>): OtelJavaCompletableResultCode {
        if (!exportCheck()) {
            return OtelJavaCompletableResultCode.ofSuccess()
        }
        val result = logSink.storeLogs(logs.toList())
        if (externalLogRecordExporter != null && result == OtelJavaCompletableResultCode.ofSuccess()) {
            return externalLogRecordExporter.export(
                logs.filterNot {
                    it.attributes.hasEmbraceAttribute(PrivateSpan)
                }
            )
        }

        return result
    }

    override fun flush(): OtelJavaCompletableResultCode = OtelJavaCompletableResultCode.ofSuccess()

    override fun shutdown(): OtelJavaCompletableResultCode = OtelJavaCompletableResultCode.ofSuccess()
}
