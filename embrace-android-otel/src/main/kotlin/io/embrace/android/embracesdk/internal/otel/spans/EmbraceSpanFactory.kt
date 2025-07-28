package io.embrace.android.embracesdk.internal.otel.spans

/**
 * Creates instances of [EmbraceSdkSpan] for internal usage. Using this factory is preferred to invoking the constructor
 * because of the it requires several services that may not be easily available.
 */
interface EmbraceSpanFactory {
    fun create(otelSpanStartArgs: OtelSpanStartArgs): EmbraceSdkSpan
}
