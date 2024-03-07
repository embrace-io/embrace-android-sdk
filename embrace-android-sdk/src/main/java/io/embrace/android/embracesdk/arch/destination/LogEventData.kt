package io.embrace.android.embracesdk.arch.destination

import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.arch.schema.TelemetryType

/**
 * Represents a Log event that can be added to the current session span.
 *
 *
 * @param embType the type of the span. Used to differentiate data from different sources
 * by the backend.
 * @param severity the severity of the log
 * @param message the message of the log
 * @param attributes the attributes of the span. emb-type is automatically added to these.
 */
internal class LogEventData(
    embType: TelemetryType,
    val severity: Severity,
    val message: String,
    attributes: Map<String, String>? = null
) {
    val attributes = (attributes ?: emptyMap()).plus(Pair("emb.type", embType.description))
}
