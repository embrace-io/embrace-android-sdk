package io.embrace.android.embracesdk.opentelemetry

import io.embrace.android.embracesdk.internal.arch.schema.EmbraceAttributeKey
import io.embrace.android.embracesdk.payload.ThreadInfo

/**
 * A snapshot of the current call stack of the threads running in the app process per [ThreadInfo]
 */
internal val embAndroidThreads = EmbraceAttributeKey("android.threads")

/**
 * Sequence number for the number of crashes captured by Embrace on the device, reported on every crash
 */
internal val embCrashNumber = EmbraceAttributeKey("android.crash_number")

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

/**
 * Attribute name for whether the session is a cold start
 */
internal val embColdStart = EmbraceAttributeKey("cold_start")

/**
 * Attribute name for session number (integer sequence ID)
 */
internal val embSessionNumber = EmbraceAttributeKey("session_number")

/**
 * Attribute name that indicates whether the session was ended by the SDK or an unexpected termination
 */
internal val embCleanExit = EmbraceAttributeKey("clean_exit")

/**
 * Attribute name that indicates whether the session was terminated
 */
internal val embTerminated = EmbraceAttributeKey("terminated")

/**
 * Attribute name that represents last known time that the session existed (nanoseconds since epoch)
 */
internal val embHeartbeatTimeUnixNano = EmbraceAttributeKey("heartbeat_time_unix_nano")

/**
 * Attribute name that identifies the crash report tied to the session
 */
internal val embCrashId = EmbraceAttributeKey("crash_id")

/**
 * Attribute name that identifies the session start type
 */
internal val embSessionStartType = EmbraceAttributeKey("session_start_type")

/**
 * Attribute name that identifies the session end type
 */
internal val embSessionEndType = EmbraceAttributeKey("session_end_type")

/**
 * Attribute name that identifies the startup duration
 */
internal val embSessionStartupDuration = EmbraceAttributeKey("startup_duration")

/**
 * Attribute name that identifies the startup threshold
 */
internal val embSessionStartupThreshold = EmbraceAttributeKey("threshold")

/**
 * Attribute name that identifies the SDK duration
 */
internal val embSdkStartupDuration = EmbraceAttributeKey("sdk_startup_duration")

/**
 * Attribute name that identifies the error log count in a session
 */
internal val embErrorLogCount = EmbraceAttributeKey("error_log_count")

/**
 * Attribute name that identifies the number of free bytes on disk
 */
internal val embFreeDiskBytes = EmbraceAttributeKey("disk_free_bytes")
