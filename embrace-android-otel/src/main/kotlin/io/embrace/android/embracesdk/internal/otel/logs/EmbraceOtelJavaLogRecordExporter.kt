package io.embrace.android.embracesdk.internal.otel.logs

import io.embrace.android.embracesdk.internal.otel.payload.toEmbracePayload
import io.embrace.android.embracesdk.internal.otel.schema.PrivateSpan
import io.embrace.android.embracesdk.internal.otel.sdk.StoreDataResult
import io.embrace.android.embracesdk.internal.otel.sdk.hasEmbraceAttribute
import io.embrace.android.embracesdk.internal.otel.sdk.toCompleteableResultCode
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaCompletableResultCode
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaLogRecordData
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaLogRecordExporter

/**
 * Exports the given [LogRecordData] to a [LogSink]
 */
internal class EmbraceOtelJavaLogRecordExporter(
    private val logSink: LogSink,
    private val externalLogRecordExporter: OtelJavaLogRecordExporter?,
    private val exportCheck: () -> Boolean,
) : OtelJavaLogRecordExporter {

    override fun export(logs: Collection<OtelJavaLogRecordData>): OtelJavaCompletableResultCode {
        if (!exportCheck()) {
            return StoreDataResult.SUCCESS.toCompleteableResultCode()
        }
        val result = logSink.storeLogs(logs.map(OtelJavaLogRecordData::toEmbracePayload))
        if (externalLogRecordExporter != null && result == StoreDataResult.SUCCESS) {
            return externalLogRecordExporter.export(
                logs.filterNot {
                    it.attributes.hasEmbraceAttribute(PrivateSpan)
                }
            )
        }
        return result.toCompleteableResultCode()
    }

    override fun flush(): OtelJavaCompletableResultCode = StoreDataResult.SUCCESS.toCompleteableResultCode()

    override fun shutdown(): OtelJavaCompletableResultCode = StoreDataResult.SUCCESS.toCompleteableResultCode()
}
