package io.embrace.android.embracesdk.internal.otel.state

import io.opentelemetry.kotlin.attributes.AttributesMutator
import io.opentelemetry.kotlin.context.Context
import io.opentelemetry.kotlin.context.ContextKey
import io.opentelemetry.kotlin.context.createContextKey
import io.opentelemetry.kotlin.logging.Logger
import io.opentelemetry.kotlin.logging.SeverityNumber

/**
 * Emits events that describe state through the OpenTelemetry logging pipeline, so that an app's own log exporters and
 * log record processors receive them.
 *
 * An event is emitted with a context that marks it, which is how Embrace's own log record processor knows to leave it
 * out of Embrace's payload and to stamp nothing on it: the event describes the process, not the session part in
 * progress or the moment it happens to be emitted at.
 */
class StateEventLogger(
    private val loggerProvider: () -> Logger,
    private val rootContextProvider: () -> Context,
) {

    private val context: Context by lazy { rootContextProvider().set(stateEventKey, true) }

    /**
     * Emits an event named [eventName] that happened at [timestampNanos], with no body.
     */
    fun emit(eventName: String, timestampNanos: Long, attributes: AttributesMutator.() -> Unit) {
        loggerProvider().emit(
            body = null,
            eventName = eventName,
            timestamp = timestampNanos,
            observedTimestamp = timestampNanos,
            context = context,
            severityNumber = SeverityNumber.INFO,
            severityText = SeverityNumber.INFO.name,
            attributes = attributes,
        )
    }
}

private val stateEventKey: ContextKey<Boolean> = createContextKey("embrace-state-event")

/**
 * Whether a log record emitted with this context is an event that describes state.
 */
internal fun Context.isStateEvent(): Boolean = get(stateEventKey) == true
