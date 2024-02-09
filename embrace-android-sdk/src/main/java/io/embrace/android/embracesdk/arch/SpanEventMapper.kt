package io.embrace.android.embracesdk.arch

import io.embrace.android.embracesdk.spans.EmbraceSpanEvent

internal fun interface SpanEventMapper {

    /**
     * A function that maps an object to an [EmbraceSpanEvent].
     */
    fun toSpanEvent(timestampNanos: Long): EmbraceSpanEvent
}
