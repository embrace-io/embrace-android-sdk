package io.embrace.android.embracesdk.internal.arch.datasource

import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.spans.PersistableEmbraceSpan
import io.embrace.android.embracesdk.internal.spans.SpanService
import io.embrace.android.embracesdk.spans.EmbraceSpan

/**
 * A [DataSource] that adds or alters a span.
 */
internal interface SpanDataSource : DataSource<SpanService> {

    /**
     * The DataSource should call this function when it wants to start, stop, or mutate
     * an [EmbraceSpan]. [captureData] should only be used for adding to the session span.
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

internal fun SpanService.startSpanCapture(schemaType: SchemaType, startTimeMs: Long): PersistableEmbraceSpan? {
    return startSpan(
        name = schemaType.fixedObjectName,
        startTimeMs = startTimeMs,
        type = schemaType.telemetryType
    )?.apply {
        schemaType.attributes().forEach {
            addAttribute(it.key, it.value)
        }
    }
}
