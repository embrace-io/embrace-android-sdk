package io.embrace.android.embracesdk.internal.arch.datasource

import io.embrace.android.embracesdk.internal.arch.destination.TraceWriter

/**
 * A [DataSource] that adds or alters a span.
 */
interface SpanDataSource : DataSource<TraceWriter> {

    /**
     * The DataSource should call this function when it wants to start, stop, or mutate
     * a span. [captureData] should only be used for adding to the session span.
     *
     * The [countsTowardsLimits] parameter should be true if the [captureAction] will add data
     * that should count towards the limits.
     *
     * The [inputValidation] parameter should return true if the user inputs are valid.
     * (e.g. an empty string is not valid for a breadcrumb message).
     *
     * The [captureAction] parameter is a lambda that captures the data and sends it to the
     * destination. It will be called only if [inputValidation] returns true & no data capture
     * limits have been exceeded.
     *
     * This function returns true if data was successfully captured & false if not.
     * This is assumed to be the case if [captureAction] completed without throwing.
     */
    fun captureSpanData(
        countsTowardsLimits: Boolean,
        inputValidation: () -> Boolean,
        captureAction: TraceWriter.() -> Unit,
    ): Boolean
}
