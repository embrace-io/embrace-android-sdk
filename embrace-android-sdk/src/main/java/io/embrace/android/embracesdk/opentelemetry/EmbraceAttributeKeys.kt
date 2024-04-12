package io.embrace.android.embracesdk.opentelemetry

import io.embrace.android.embracesdk.arch.schema.EmbraceAttributeKey

/**
 * Attribute name for the app framework for which the telemetry is being logged
 */
internal val embAppFramework = EmbraceAttributeKey("app_framework")

/**
 * Attribute name for the exception handling type - whether it's handled or unhandled
 */
internal val embExceptionHandling = EmbraceAttributeKey("exception_handling")

/**
 * Monotonically increasing sequence ID given to completed span that is expected to be sent to the server
 */
internal val embProcessIdentifier: EmbraceAttributeKey = EmbraceAttributeKey("process_identifier")

/**
 * Attribute name for the unique ID assigned to each app instance
 */
internal val embSequenceId: EmbraceAttributeKey = EmbraceAttributeKey(id = "sequence_id", isPrivate = true)

/**
 * Attribute name for the Embrace Sesssion
 */
internal val embSessionId: EmbraceAttributeKey = EmbraceAttributeKey("session_id")

