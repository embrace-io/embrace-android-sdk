package io.embrace.android.embracesdk.internal.otel.logs

import io.embrace.android.embracesdk.internal.utils.Provider
import io.opentelemetry.kotlin.NoopOpenTelemetry
import io.opentelemetry.kotlin.attributes.AttributesMutator
import io.opentelemetry.kotlin.context.Context
import io.opentelemetry.kotlin.context.ContextKey
import io.opentelemetry.kotlin.logging.Logger
import io.opentelemetry.kotlin.logging.SeverityNumber
import java.util.concurrent.atomic.AtomicReference

class EventServiceImpl(
    private val sdkLoggerProvider: Provider<Logger>,
    private val skipMetadataContextKey: Provider<ContextKey<Boolean>>,
    private val implicitContextProvider: Provider<Context>,
) : EventService {
    private val noopLogger = NoopOpenTelemetry.loggerProvider.getLogger("noop")
    private val sdkLoggerRef: AtomicReference<Logger> = AtomicReference(noopLogger)

    override fun initializeService(sdkInitStartTimeMs: Long) {
        sdkLoggerRef.set(sdkLoggerProvider())
    }

    override fun initialized(): Boolean = sdkLoggerRef.get() != noopLogger

    override fun log(
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
        // Emit with the supplied context (falling back to the current context when none is given),
        // adding the key that signals the enrichment processor to skip stamping the current SDK
        // metadata when metadata should not be added.
        val emitContext = if (addCurrentMetadata) {
            context
        } else {
            (context ?: implicitContextProvider()).set(skipMetadataContextKey(), true)
        }
        sdkLoggerRef.get().emit(
            body = body,
            eventName = eventName,
            timestamp = timestamp,
            observedTimestamp = observedTimestamp,
            context = emitContext,
            severityNumber = severityNumber,
            severityText = severityText,
            attributes = eventAttributes,
        )
    }
}
