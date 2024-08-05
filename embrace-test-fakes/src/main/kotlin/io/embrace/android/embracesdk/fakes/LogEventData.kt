package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.opentelemetry.api.logs.Severity

/**
 * Represents a Log event that can be added to the current session span.
 *
 *
 * @param schemaType the type of the span. Used to differentiate data from different sources
 * by the backend.
 * @param severity the severity of the log
 * @param message the message of the log
 */
public class LogEventData(
    public val schemaType: SchemaType,
    public val severity: Severity,
    public val message: String
)
