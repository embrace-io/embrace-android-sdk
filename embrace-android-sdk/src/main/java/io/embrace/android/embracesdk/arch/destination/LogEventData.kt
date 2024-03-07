package io.embrace.android.embracesdk.arch.destination

import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.arch.schema.SchemaType

/**
 * Represents a Log event that can be added to the current session span.
 *
 *
 * @param schemaType the type of the span. Used to differentiate data from different sources
 * by the backend.
 * @param severity the severity of the log
 * @param message the message of the log
 */
internal class LogEventData(
    val schemaType: SchemaType,
    val severity: Severity,
    val message: String
) {
    val attributes = schemaType.attrs.plus(Pair("emb.type", schemaType.telemetryType.description))
}
