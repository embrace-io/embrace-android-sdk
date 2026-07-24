package io.embrace.android.embracesdk.internal.otel.logs

import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.utils.UuidSource
import io.opentelemetry.kotlin.context.Context
import io.opentelemetry.kotlin.context.ContextKey
import io.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.kotlin.logging.export.LogRecordProcessor
import io.opentelemetry.kotlin.logging.model.ReadWriteLogRecord
import io.opentelemetry.kotlin.semconv.LogAttributes
import java.util.concurrent.atomic.AtomicReference

/**
 * Enriches every log record emitted through the SDK with a [LogAttributes.LOG_RECORD_UID] (unless
 * the caller already set one) and a snapshot of the current SDK metadata. This runs as the first
 * [LogRecordProcessor] in the pipeline so the enrichment is visible to the Embrace exporter and any
 * externally configured processors. It handles logs recorded via both the Embrace API and the OTel
 * API, as they all flow through the SDK's logger.
 *
 * Metadata enrichment can be suppressed for a specific log record by emitting it with a [Context]
 * that has [skipMetadataContextKey] set to true. This is used for logs that describe a past session
 * (e.g. native crashes replayed on the next launch), which carry their own session identity and must
 * not be stamped with the current session's metadata.
 */
class EmbraceLogRecordProcessor(
    private val uuidSource: UuidSource,
    private val skipMetadataContextKey: Provider<ContextKey<Boolean>>,
) : LogRecordProcessor {

    private val metadataProviderRef = AtomicReference<Provider<Map<String, String>>> { emptyMap() }

    override fun onEmit(log: ReadWriteLogRecord, context: Context) {
        if (!log.attributes.containsKey(LogAttributes.LOG_RECORD_UID)) {
            log.setStringAttribute(LogAttributes.LOG_RECORD_UID, uuidSource.createUuid())
        }
        if (context.get(skipMetadataContextKey()) != true) {
            metadataProviderRef.get().invoke().forEach { (key, value) ->
                log.setStringAttribute(key, value)
            }
        }
    }

    /**
     * Sets a provider that supplies a snapshot of the current metadata that describes the state of the SDK
     */
    fun setMetadataProvider(provider: Provider<Map<String, String>>) {
        metadataProviderRef.set(provider)
    }

    override suspend fun forceFlush(): OperationResultCode = OperationResultCode.Success

    override suspend fun shutdown(): OperationResultCode = OperationResultCode.Success
}
