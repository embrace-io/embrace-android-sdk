package io.embrace.android.embracesdk.opentelemetry

import io.embrace.android.embracesdk.arch.schema.EmbraceAttributeKey

/**
 * Monotonically increasing sequence ID given to completed span that is expected to be sent to the server
 */
internal val embProcessIdentifier: EmbraceAttributeKey = EmbraceAttributeKey("process_identifier")

/**
 * Attribute name for the unique ID assigned to each app instance
 */
internal val embSequenceId: EmbraceAttributeKey = EmbraceAttributeKey("sequence_id")
