package io.embrace.android.embracesdk.internal.otel.logs

import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.utils.UuidSource
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.opentelemetry.kotlin.context.Context
import io.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.kotlin.logging.export.LogRecordExporter
import io.opentelemetry.kotlin.logging.export.LogRecordProcessor
import io.opentelemetry.kotlin.logging.model.ReadWriteLogRecord
import io.opentelemetry.kotlin.semconv.LogAttributes
import kotlinx.coroutines.runBlocking

/**
 * A [LogRecordProcessor] that adds the attributes Embrace requires on every log record, then exports it.
 */
internal class EmbraceLogRecordProcessor(
    private val uuidSource: UuidSource,
    private val metadataProvider: Provider<Map<String, String>>,
    private val logRecordExporter: LogRecordExporter,
) : LogRecordProcessor {

    override suspend fun forceFlush(): OperationResultCode = OperationResultCode.Success

    override fun onEmit(log: ReadWriteLogRecord, context: Context) {
        val attributes = log.attributes

        if (!attributes.containsKey(LogAttributes.LOG_RECORD_UID)) {
            log.setStringAttribute(LogAttributes.LOG_RECORD_UID, uuidSource.createUuid())
        }

        if (!attributes.ownsSessionContext()) {
            metadataProvider().forEach { (key, value) ->
                log.setStringAttribute(key, value)
            }
        }

        runBlocking { logRecordExporter.export(mutableListOf(log)) }
    }

    override suspend fun shutdown(): OperationResultCode = OperationResultCode.Success

    /**
     * Whether the record already carries the session it belongs to, in which case the current metadata must
     * not be applied. Telemetry that describes a session other than the current one - a native crash or an
     * app exit reported by a later process - sets these attributes itself, and leaves them blank if the
     * session it describes is unknown.
     */
    private fun Map<String, Any>.ownsSessionContext(): Boolean =
        containsKey(EmbSessionAttributes.EMB_SESSION_PART_ID)
}
