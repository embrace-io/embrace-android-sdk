package io.embrace.android.embracesdk.internal.otel.logs

import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.otel.export.InlineExporter
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.utils.UuidSource
import io.opentelemetry.kotlin.context.Context
import io.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.kotlin.logging.export.LogRecordProcessor
import io.opentelemetry.kotlin.logging.model.ReadWriteLogRecord
import io.opentelemetry.kotlin.logging.model.ReadableLogRecord
import io.opentelemetry.kotlin.semconv.LogAttributes

/**
 * A [LogRecordProcessor] that adds the attributes Embrace requires on every log record, then exports it.
 */
internal class EmbraceLogRecordProcessor(
    private val uuidSource: UuidSource,
    private val metadataProvider: Provider<Map<String, String>>,
    private val logRecordExporter: InlineExporter<ReadableLogRecord>,
) : LogRecordProcessor {

    override suspend fun forceFlush(): OperationResultCode = logRecordExporter.forceFlush()

    override fun onEmit(log: ReadWriteLogRecord, context: Context) {
        val attributes = log.attributes

        if (!attributes.containsKey(LogAttributes.LOG_RECORD_UID)) {
            log.setStringAttribute(LogAttributes.LOG_RECORD_UID, uuidSource.createUuid())
        }

        if (!attributes.belongsInCurrentProcess()) {
            // never override what the instrumentation has already set. Telemetry that knows which session it
            // belongs to - such as an app exit reported by a later process - sets the session attributes
            // itself, and leaves them blank if the session it describes is unknown.
            metadataProvider().forEach { (key, value) ->
                if (!attributes.containsKey(key)) {
                    log.setStringAttribute(key, value)
                }
            }
        }

        // exported on the calling thread: a crash log has to reach the sink before crash teardown
        // persists the payload and the process dies. See [InlineExporter].
        logRecordExporter.exportInline(listOf(log))
    }

    override suspend fun shutdown(): OperationResultCode = OperationResultCode.Success

    /**
     * Whether the record describes a process that has already died, in which case none of the current
     * metadata applies to it. A native crash is resurrected by a later process and carries the whole state of
     * the session that crashed - its session IDs, app state and session properties - so enriching it would
     * describe the wrong session entirely.
     */
    private fun Map<String, Any>.belongsInCurrentProcess(): Boolean =
        get(EmbType.System.NativeCrash.key) == EmbType.System.NativeCrash.value
}
