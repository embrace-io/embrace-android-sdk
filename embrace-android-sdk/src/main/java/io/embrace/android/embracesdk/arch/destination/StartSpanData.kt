package io.embrace.android.embracesdk.arch.destination

import io.embrace.android.embracesdk.arch.schema.TelemetryType

/**
 * Holds the information required to start a span.
 *
 * @param embType the type of the span. Used to differentiate data from different sources
 * by the backend.
 * @param spanName the name of the span.
 * @param spanStartTimeMs the start time of the span event in milliseconds.
 * @param attributes the attributes of the span. emb-type is automatically added to these.
 */
internal class StartSpanData(
    embType: TelemetryType,
    val spanName: String,
    val spanStartTimeMs: Long,
    attributes: Map<String, String>? = null
) {
    val attributes = (attributes ?: emptyMap()).plus(Pair("emb.type", embType.description))
}
