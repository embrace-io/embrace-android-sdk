package io.embrace.android.embracesdk.internal.otel.state

/**
 * How telemetry is associated with state, where it is: every span carries the sequence number of the last state change
 * before it started and before it ended, in Embrace's payload and in what an app's own exporters and processors are
 * handed.
 *
 * @param stateProvider the value of every state now, or null while nothing records state. It is what a span is linked to.
 * @param valuesOnExportedSpans whether what an app's own exporters and processors are handed of a span also carries the
 * value of every state when it started, and of each state that differed when it ended. Embrace's payload never does.
 * @param exportsEvents whether events that describe state are emitted to an app's own exporters and processors, and the
 * Resource they see says which instance of the process the states belong to.
 * @param stateAt the value of every state at a time in the past, in milliseconds by the SDK's clock, or null if that is
 * not known: nothing records state, or the time is further back than whatever records it remembers. It is asked for a
 * span that starts or ends at a time from before the last change, on the thread that starts or ends the span, so it must
 * not wait for anything.
 * @param logStateProvider the value of every state to stamp on a log emitted now, or null for a log that is to carry no
 * state: while nothing records state, and before the SDK has finished starting, when a log carries none of the session's
 * context either.
 */
class StateAssociation(
    val stateProvider: () -> StateStamp?,
    val valuesOnExportedSpans: Boolean,
    val exportsEvents: Boolean,
    val stateAt: (timeMs: Long) -> StateStamp? = { null },
    val logStateProvider: () -> StateStamp? = stateProvider,
)
