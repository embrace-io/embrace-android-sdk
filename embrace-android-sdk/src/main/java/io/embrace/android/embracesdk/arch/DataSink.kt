package io.embrace.android.embracesdk.arch

import io.embrace.android.embracesdk.spans.EmbraceSpan

/**
 * A function that acts on an [EmbraceSpan] and mutates its state.
 */
internal typealias SpanMutator = EmbraceSpan.() -> Unit

/**
 * A function that provides a [DataSink] instance.
 */
internal typealias DataSinkProvider = () -> DataSink

/**
 * A [DataSource] can write information to a [DataSink] that will ultimately be added to a session.
 */
internal interface DataSink {

    /**
     * Given a span ID, mutate the span with the given action. If the span cannot be found,
     * the action will not be executed.
     *
     * The [EmbraceSpan] should NOT be cached and used outside of the action.
     */
    fun mutateSpan(spanId: String, action: SpanMutator)

    /**
     * Mutate the current session span with the given action. If the span cannot be found,
     * the action will not be executed.
     *
     * The [EmbraceSpan] should NOT be cached and used outside of the action.
     */
    fun mutateSessionSpan(action: SpanMutator)

    /**
     * Start a new span and mutates it with the given action.
     * The returned span ID can be retained & used either to further alter the span
     * via [mutateSpan] or to stop it via [stopSpan]. If the span couldn't be created,
     * the return value will be null, and the action will not be executed.
     */
    fun startSpan(
        name: String,
        action: SpanMutator
    ): String?

    /**
     * Mutates a span with the given action and then stops it. If the span cannot be found
     * the action will not be executed. If the span cannot be stopped this function will return
     * false.
     *
     * Attempting to stop the root session span is disallowed & will be a no-op.
     */
    fun stopSpan(
        spanId: String,
        action: SpanMutator
    ): Boolean
}
