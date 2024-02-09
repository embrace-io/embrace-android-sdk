package io.embrace.android.embracesdk.arch

import androidx.annotation.CheckResult
import io.embrace.android.embracesdk.spans.EmbraceSpan

/**
 * A function that acts on an [EmbraceSpan] and mutates its state.
 */
internal typealias SpanMutator = EmbraceSpan.() -> Unit

/**
 * A function that provides a [DataSink] instance.
 */
internal typealias DataSinkProvider<T, S> = () -> DataSink<T, S>

/**
 * A [DataSource] can write information to a [DataSink] that will ultimately be added to a session.
 */
internal interface DataSink<in T : SpanEventMapper, out S> {

    /**
     * Adds an event that is stored in the sink as part of the session span.
     */
    fun addEvent(dataToStore: T)

    /**
     * Gets a snapshot of the current state of the sink.
     */
    @CheckResult
    fun getSnapshot(): S

    /**
     * Gets a snapshot of the current state of the sink & clears the sink.
     */
    @CheckResult
    fun flush(): S
}

/**
 * Defines the possible states for adding to the store.
 */
internal enum class StoreResult {
    SUCCESS,
    FAILURE
}
