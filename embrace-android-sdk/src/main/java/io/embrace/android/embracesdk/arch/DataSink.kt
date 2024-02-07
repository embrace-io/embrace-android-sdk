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
internal interface DataSink
