package io.opentelemetry.kotlin.tracing

import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.attributes.AttributesMutator

/**
 * Records an exception as a span event, adding standard exception attributes.
 */
@ExperimentalApi
fun Span.recordException(
    exception: Throwable,
    attributes: AttributesMutator.() -> Unit = {},
) {
    addEvent("exception") {
        setStringAttribute("exception.stacktrace", exception.stackTraceToString())
        exception.message?.let { setStringAttribute("exception.message", it) }
        exception.javaClass.name.let { setStringAttribute("exception.type", it) }
        attributes()
    }
}
