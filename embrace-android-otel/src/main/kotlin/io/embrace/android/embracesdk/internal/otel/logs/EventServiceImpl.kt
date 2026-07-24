package io.embrace.android.embracesdk.internal.otel.logs

import io.embrace.android.embracesdk.internal.otel.impl.EmbAttributesMutator
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.utils.UuidSource
import io.embrace.android.embracesdk.semconv.EmbSpanAttributes
import io.opentelemetry.kotlin.NoopOpenTelemetry
import io.opentelemetry.kotlin.attributes.AttributesMutator
import io.opentelemetry.kotlin.context.Context
import io.opentelemetry.kotlin.logging.Logger
import io.opentelemetry.kotlin.logging.SeverityNumber
import io.opentelemetry.kotlin.semconv.LogAttributes
import java.util.concurrent.atomic.AtomicReference

class EventServiceImpl(
    private val sdkLoggerProvider: Provider<Logger>,
    private val uuidSource: UuidSource,
) : EventService {
    private val noopLogger = NoopOpenTelemetry.loggerProvider.getLogger("noop")
    private val sdkLoggerRef: AtomicReference<Logger> = AtomicReference(noopLogger)
    private val metadataProviderRef = AtomicReference<EventMetadataProvider>(EventMetadataProvider { emptyMap() })

    override fun initializeService(sdkInitStartTimeMs: Long) {
        sdkLoggerRef.set(sdkLoggerProvider())
    }

    override fun initialized(): Boolean = sdkLoggerRef.get() != noopLogger

    override fun log(
        impl: Logger?,
        eventName: String?,
        body: String?,
        timestamp: Long?,
        observedTimestamp: Long?,
        context: Context?,
        severityNumber: SeverityNumber?,
        severityText: String?,
        addCurrentMetadata: Boolean,
        eventAttributes: (AttributesMutator.() -> Unit)?,
    ) {
        val logger = impl ?: sdkLoggerRef.get()
        val container = EmbAttributesMutator()
        eventAttributes?.invoke(container)
        if (!container.attributes.containsKey(LogAttributes.LOG_RECORD_UID)) {
            container.setStringAttribute(LogAttributes.LOG_RECORD_UID, uuidSource.createUuid())
        }
        if (addCurrentMetadata) {
            val provider = metadataProviderRef.get()
            if (!container.attributes.containsKey(EmbSpanAttributes.EMB_PRIVATE)) {
                provider.nonPrivateAttributes().forEach { (k, v) -> container.setStringAttribute(k, v) }
            }
            // merged last so that these win on a key collision with scoped attributes
            provider.allTelemetryAttributes().forEach { (k, v) -> container.setStringAttribute(k, v) }
        }

        logger.emit(
            body = body,
            eventName = eventName,
            timestamp = timestamp,
            observedTimestamp = observedTimestamp,
            context = context,
            severityNumber = severityNumber,
            severityText = severityText,
            attributes = {
                container.attributes.forEach { (k, v) -> setStringAttribute(k, v.toString()) }
            },
        )
    }

    override fun setMetadataProvider(provider: EventMetadataProvider) {
        metadataProviderRef.set(provider)
    }
}
