package io.embrace.android.embracesdk.internal.otel.logs

import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.internal.arch.schema.PrivateSpan
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.attributes.MutableAttributeContainer
import io.embrace.opentelemetry.kotlin.context.Context
import io.embrace.opentelemetry.kotlin.createNoopOpenTelemetry
import io.embrace.opentelemetry.kotlin.logging.Logger
import io.embrace.opentelemetry.kotlin.logging.model.SeverityNumber
import io.embrace.opentelemetry.kotlin.semconv.IncubatingApi
import io.embrace.opentelemetry.kotlin.semconv.LogAttributes
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@OptIn(ExperimentalApi::class, IncubatingApi::class)
class EventServiceImpl(
    private val sdkLoggerProvider: Provider<Logger>,
) : EventService {
    private val noopLogger = createNoopOpenTelemetry().loggerProvider.getLogger("noop")
    private val sdkLoggerRef: AtomicReference<Logger> = AtomicReference(noopLogger)
    private val metadataSupplierProviderRef = AtomicReference<Provider<Map<String, String>>> { emptyMap() }

    override fun initializeService(sdkInitStartTimeMs: Long) {
        sdkLoggerRef.set(sdkLoggerProvider())
    }

    override fun initialized(): Boolean = sdkLoggerRef.get() != noopLogger

    override fun log(
        logTimeMs: Long,
        schemaType: SchemaType,
        severity: Severity,
        message: String,
        isPrivate: Boolean,
        addCurrentMetadata: Boolean,
    ) {
        val severityNumber = when (severity) {
            Severity.INFO -> SeverityNumber.INFO
            Severity.WARNING -> SeverityNumber.WARN
            Severity.ERROR -> SeverityNumber.ERROR
        }
        val logTimeNanos = TimeUnit.MILLISECONDS.toNanos(logTimeMs)
        logRecord(
            impl = sdkLoggerRef.get(),
            body = message,
            timestamp = logTimeNanos,
            observedTimestamp = logTimeNanos,
            context = null,
            severityNumber = severityNumber,
            severityText = getSeverityText(severityNumber),
            addCurrentMetadata = addCurrentMetadata
        ) {
            if (isPrivate) {
                setStringAttribute(PrivateSpan.key.name, PrivateSpan.value)
            }

            with(schemaType) {
                setStringAttribute(telemetryType.key.name, telemetryType.value)
                attributes().forEach {
                    setStringAttribute(it.key, it.value)
                }
            }
        }
    }

    override fun logRecord(
        impl: Logger,
        body: String?,
        timestamp: Long?,
        observedTimestamp: Long?,
        context: Context?,
        severityNumber: SeverityNumber?,
        severityText: String?,
        addCurrentMetadata: Boolean,
        attributes: (MutableAttributeContainer.() -> Unit)?,
    ) {
        impl.record(
            body = body,
            timestamp = timestamp,
            observedTimestamp = observedTimestamp,
            context = context,
            severityNumber = severityNumber,
            severityText = severityText,
            addCurrentMetadata = addCurrentMetadata,
            eventAttributes = attributes
        )
    }

    override fun logEvent(
        impl: Logger,
        eventName: String,
        body: String?,
        timestamp: Long?,
        observedTimestamp: Long?,
        context: Context?,
        severityNumber: SeverityNumber?,
        severityText: String?,
        addCurrentMetadata: Boolean,
        attributes: (MutableAttributeContainer.() -> Unit)?,
    ) {
        impl.record(
            eventName = eventName,
            body = body,
            timestamp = timestamp,
            observedTimestamp = observedTimestamp,
            context = context,
            severityNumber = severityNumber,
            severityText = severityText,
            addCurrentMetadata = addCurrentMetadata,
            eventAttributes = attributes
        )
    }

    override fun setMetadataProvider(provider: Provider<Map<String, String>>) {
        metadataSupplierProviderRef.set(provider)
    }

    private fun getCurrentMetadata(): Map<String, String> = metadataSupplierProviderRef.get().invoke().toMap()

    private fun Logger.record(
        eventName: String? = null,
        body: String? = null,
        timestamp: Long? = null,
        observedTimestamp: Long? = null,
        context: Context? = null,
        severityNumber: SeverityNumber? = null,
        severityText: String? = null,
        addCurrentMetadata: Boolean = true,
        eventAttributes: (MutableAttributeContainer.() -> Unit)? = null,
    ) {
        val embraceAttributes: (MutableAttributeContainer.() -> Unit) = {
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
        if (eventName == null) {
            log(
                body = body,
                timestamp = timestamp,
                observedTimestamp = observedTimestamp,
                context = context,
                severityNumber = severityNumber,
                severityText = severityText,
                attributes = embraceAttributes
            )
        } else {
            logEvent(
                eventName = eventName,
                body = body,
                timestamp = timestamp,
                observedTimestamp = observedTimestamp,
                context = context,
                severityNumber = severityNumber,
                severityText = severityText,
                attributes = embraceAttributes
            )
        }
    }

    private fun getSeverityText(severity: SeverityNumber) = when (severity) {
        SeverityNumber.WARN -> "WARNING"
        else -> severity.name
    }
}
