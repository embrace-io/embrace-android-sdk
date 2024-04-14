package io.embrace.android.embracesdk.opentelemetry

import io.embrace.android.embracesdk.arch.schema.EmbraceAttributeKey

/**
 * A snapshot of the current call stack of the threads running in the app process per [ThreadInfo]
 */
internal val embAndroidThreads = EmbraceAttributeKey("android.threads")

/**
 * Attribute name for the app framework for which the telemetry is being logged
 */
internal val embAppFramework = EmbraceAttributeKey("app_framework")

/**
 * Sequence number for the number of crashes captured by Embrace on the device, reported on every crash
 */
internal val embCrashNumber = EmbraceAttributeKey("crash_number")

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

/**
 * Attribute name for the application state (foreground/background) at the time the log was recorded
 */
internal val embState = EmbraceAttributeKey("state")
