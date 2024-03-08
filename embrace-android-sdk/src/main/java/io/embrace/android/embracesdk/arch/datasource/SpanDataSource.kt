package io.embrace.android.embracesdk.arch.datasource

import io.embrace.android.embracesdk.arch.destination.SpanEventData
import io.embrace.android.embracesdk.arch.destination.StartSpanData
import io.embrace.android.embracesdk.internal.spans.SpanService
import io.embrace.android.embracesdk.spans.EmbraceSpan

/**
 * A [DataSource] that adds or alters a new span on the [SpansService]
 */
internal interface SpanDataSource : DataSource<SpanService> {

    /**
     * The DataSource should call this function when it wants to start, stop, or mutate
     * an [EmbraceSpan]. [alterSessionSpan] should only be used for adding to the session span.
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
        captureAction: SpanService.() -> Unit
    ): Boolean
}

/**
 * Starts a span using the given [SpanEventData].
 */
internal fun <T> SpanService.startSpanCapture(obj: T, mapper: T.() -> StartSpanData): EmbraceSpan? {
    val data = obj.mapper()
    return createSpan(data.schemaType.name)?.apply {
        data.attributes.forEach {
            addAttribute(it.key, it.value)
        }
        start()
    }
}
