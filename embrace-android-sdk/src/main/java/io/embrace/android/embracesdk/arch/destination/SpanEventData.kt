package io.embrace.android.embracesdk.arch.destination

/**
 * Represents a span event that can be added to the current session span.
 *
 * @param embType the type of the event. Used to differentiate data from different sources
 * by the backend.
 * @param spanName the name of the span event.
 * @param spanStartTimeMs the start time of the span event in milliseconds.
 * @param attributes the attributes of the span event. emb-type is automatically added to these.
 */
internal class SpanEventData(
    embType: String,
    val spanName: String,
    val spanStartTimeMs: Long,
    attributes: Map<String, String>? = null
) {
    val attributes = (attributes ?: emptyMap()).plus(Pair("emb.type", embType))
}
