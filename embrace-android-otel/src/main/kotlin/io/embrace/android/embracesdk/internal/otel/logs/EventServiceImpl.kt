package io.embrace.android.embracesdk.internal.otel.logs

import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.NoopOpenTelemetry
import io.opentelemetry.kotlin.attributes.MutableAttributeContainer
import io.opentelemetry.kotlin.context.Context
import io.opentelemetry.kotlin.logging.Logger
import io.opentelemetry.kotlin.logging.model.SeverityNumber
import io.opentelemetry.kotlin.semconv.IncubatingApi
import io.opentelemetry.kotlin.semconv.LogAttributes
import java.util.concurrent.atomic.AtomicReference

@OptIn(ExperimentalApi::class, IncubatingApi::class)
class EventServiceImpl(
    private val sdkLoggerProvider: Provider<Logger>,
) : EventService {
    private val noopLogger = NoopOpenTelemetry.loggerProvider.getLogger("noop")
    private val sdkLoggerRef: AtomicReference<Logger> = AtomicReference(noopLogger)
    private val metadataSupplierProviderRef = AtomicReference<Provider<Map<String, String>>> { emptyMap() }

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
        eventAttributes: (MutableAttributeContainer.() -> Unit)?,
    ) {
        val logger = impl ?: sdkLoggerRef.get()
        val additionalAttributes: (MutableAttributeContainer.() -> Unit) = {
            eventAttributes?.invoke(this)
            if (!attributes.contains(LogAttributes.LOG_RECORD_UID)) {
                setStringAttribute(LogAttributes.LOG_RECORD_UID, Uuid.getEmbUuid())
            }
            if (addCurrentMetadata) {
                getCurrentMetadata().forEach {
                    setStringAttribute(it.key, it.value)
                }
            }
        }

        logger.emit(
            body = body,
            eventName = eventName,
            timestamp = timestamp,
            observedTimestamp = observedTimestamp,
            context = context,
            severityNumber = severityNumber,
            severityText = severityText,
            attributes = additionalAttributes
        )
    }

    override fun setMetadataProvider(provider: Provider<Map<String, String>>) {
        metadataSupplierProviderRef.set(provider)
    }

    private fun getCurrentMetadata(): Map<String, String> = metadataSupplierProviderRef.get().invoke().toMap()
}
