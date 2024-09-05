package io.embrace.android.embracesdk.internal.opentelemetry

import io.embrace.android.embracesdk.internal.arch.schema.EmbraceAttributeKey
import io.embrace.android.embracesdk.internal.payload.ThreadInfo

/**
 * A snapshot of the current call stack of the threads running in the app process per [ThreadInfo]
 */
public val embAndroidThreads: EmbraceAttributeKey = EmbraceAttributeKey("android.threads")

/**
 * Sequence number for the number of crashes captured by Embrace on the device, reported on every crash
 */
public val embCrashNumber: EmbraceAttributeKey = EmbraceAttributeKey("android.crash_number")

/**
 * Attribute name for the exception handling type - whether it's handled or unhandled
 */
public val embExceptionHandling: EmbraceAttributeKey = EmbraceAttributeKey("exception_handling")

/**
 * Monotonically increasing sequence ID given to completed span that is expected to be sent to the server
 */
public val embProcessIdentifier: EmbraceAttributeKey = EmbraceAttributeKey("process_identifier")

/**
 * Attribute name for the unique ID assigned to each app instance
 */
public val embSequenceId: EmbraceAttributeKey = EmbraceAttributeKey(id = "sequence_id", isPrivate = true)

/**
 * Attribute name for the application state (foreground/background) at the time the log was recorded
 */
public val embState: EmbraceAttributeKey = EmbraceAttributeKey("state")

/**
 * Attribute name for whether the session is a cold start
 */
public val embColdStart: EmbraceAttributeKey = EmbraceAttributeKey("cold_start")

/**
 * Attribute name for session number (integer sequence ID)
 */
public val embSessionNumber: EmbraceAttributeKey = EmbraceAttributeKey("session_number")

/**
 * Attribute name that indicates whether the session was ended by the SDK or an unexpected termination
 */
public val embCleanExit: EmbraceAttributeKey = EmbraceAttributeKey("clean_exit")

/**
 * Attribute name that indicates whether the session was terminated
 */
public val embTerminated: EmbraceAttributeKey = EmbraceAttributeKey("terminated")

/**
 * Attribute name that represents last known time that the session existed (nanoseconds since epoch)
 */
public val embHeartbeatTimeUnixNano: EmbraceAttributeKey = EmbraceAttributeKey("heartbeat_time_unix_nano")

/**
 * Attribute name that identifies the crash report tied to the session
 */
public val embCrashId: EmbraceAttributeKey = EmbraceAttributeKey("crash_id")

/**
 * Attribute name that identifies the session start type
 */
public val embSessionStartType: EmbraceAttributeKey = EmbraceAttributeKey("session_start_type")

/**
 * Attribute name that identifies the session end type
 */
public val embSessionEndType: EmbraceAttributeKey = EmbraceAttributeKey("session_end_type")

/**
 * Attribute name that identifies the startup duration
 */
public val embSessionStartupDuration: EmbraceAttributeKey = EmbraceAttributeKey("startup_duration")

/**
 * Attribute name that identifies the startup threshold
 */
public val embSessionStartupThreshold: EmbraceAttributeKey = EmbraceAttributeKey("threshold")

/**
 * Attribute name that identifies the SDK duration
 */
public val embSdkStartupDuration: EmbraceAttributeKey = EmbraceAttributeKey("sdk_startup_duration")

/**
 * Attribute name that identifies the error log count in a session
 */
public val embErrorLogCount: EmbraceAttributeKey = EmbraceAttributeKey("error_log_count")

/**
 * Attribute name that identifies the number of free bytes on disk
 */
public val embFreeDiskBytes: EmbraceAttributeKey = EmbraceAttributeKey("disk_free_bytes")

/**
 * Attribute name that identifies how a signal should be delivered to the Embrace backend
 */
public val embSendMode: EmbraceAttributeKey = EmbraceAttributeKey(id = "send_mode", isPrivate = true)
