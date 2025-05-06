package io.embrace.android.embracesdk.internal.otel.attrs

import io.embrace.android.embracesdk.internal.payload.ThreadInfo

/**
 * A snapshot of the current call stack of the threads running in the app process per [ThreadInfo]
 */
val embAndroidThreads: EmbraceAttributeKey = EmbraceAttributeKey.create("android.threads")

/**
 * Sequence number for the number of crashes captured by Embrace on the device, reported on every crash
 */
val embCrashNumber: EmbraceAttributeKey = EmbraceAttributeKey.create("android.crash_number")

/**
 * Sequence number for the number of AEI crashes captured by Embrace on the device, reported on every AEI crash
 */
val embAeiNumber: EmbraceAttributeKey = EmbraceAttributeKey.create("android.aei_crash_number")

/**
 * Attribute name for the exception handling type - whether it's handled or unhandled
 */
val embExceptionHandling: EmbraceAttributeKey = EmbraceAttributeKey.create("exception_handling")

/**
 * Monotonically increasing sequence ID given to completed span that is expected to be sent to the server
 */
val embProcessIdentifier: EmbraceAttributeKey = EmbraceAttributeKey.create("process_identifier")

/**
 * Attribute name for the unique ID assigned to each app instance
 */
val embSequenceId: EmbraceAttributeKey = EmbraceAttributeKey.create(id = "sequence_id", isPrivate = true)

/**
 * Attribute name for the application state (foreground/background) at the time the log was recorded
 */
val embState: EmbraceAttributeKey = EmbraceAttributeKey.create("state")

/**
 * Attribute name for whether the session is a cold start
 */
val embColdStart: EmbraceAttributeKey = EmbraceAttributeKey.create("cold_start")

/**
 * Attribute name for session number (integer sequence ID)
 */
val embSessionNumber: EmbraceAttributeKey = EmbraceAttributeKey.create("session_number")

/**
 * Attribute name that indicates whether the session was ended by the SDK or an unexpected termination
 */
val embCleanExit: EmbraceAttributeKey = EmbraceAttributeKey.create("clean_exit")

/**
 * Attribute name that indicates whether the session was terminated
 */
val embTerminated: EmbraceAttributeKey = EmbraceAttributeKey.create("terminated")

/**
 * Attribute name that represents last known time that the session existed (nanoseconds since epoch)
 */
val embHeartbeatTimeUnixNano: EmbraceAttributeKey = EmbraceAttributeKey.create("heartbeat_time_unix_nano")

/**
 * Attribute name that identifies the crash report tied to the session
 */
val embCrashId: EmbraceAttributeKey = EmbraceAttributeKey.create("crash_id")

/**
 * Attribute name that identifies the session start type
 */
val embSessionStartType: EmbraceAttributeKey = EmbraceAttributeKey.create("session_start_type")

/**
 * Attribute name that identifies the session end type
 */
val embSessionEndType: EmbraceAttributeKey = EmbraceAttributeKey.create("session_end_type")

/**
 * Attribute name that identifies the startup duration
 */
val embSessionStartupDuration: EmbraceAttributeKey = EmbraceAttributeKey.create("startup_duration")

/**
 * Attribute name that identifies the startup threshold
 */
val embSessionStartupThreshold: EmbraceAttributeKey = EmbraceAttributeKey.create("threshold")

/**
 * Attribute name that identifies the SDK duration
 */
val embSdkStartupDuration: EmbraceAttributeKey = EmbraceAttributeKey.create("sdk_startup_duration")

/**
 * Attribute name that identifies the error log count in a session
 */
val embErrorLogCount: EmbraceAttributeKey = EmbraceAttributeKey.create("error_log_count")

/**
 * Attribute name that identifies the number of free bytes on disk
 */
val embFreeDiskBytes: EmbraceAttributeKey = EmbraceAttributeKey.create("disk_free_bytes")

/**
 * Attribute name that identifies how a signal should be delivered to the Embrace backend
 */
val embSendMode: EmbraceAttributeKey = EmbraceAttributeKey.create(id = "send_mode", isPrivate = true)

/**
 * The size of a log message attachment in bytes
 */
val embAttachmentSize: EmbraceAttributeKey = EmbraceAttributeKey.create("attachment_size")

/**
 * The URL of a user-hosted log message attachment
 */
val embAttachmentUrl: EmbraceAttributeKey = EmbraceAttributeKey.create("attachment_url")

/**
 * The ID of a user-hosted log message attachment
 */
val embAttachmentId: EmbraceAttributeKey = EmbraceAttributeKey.create("attachment_id")

/**
 * The error code associated with a failed log message attachment
 */
val embAttachmentErrorCode: EmbraceAttributeKey = EmbraceAttributeKey.create("attachment_error_code")

/**
 * The name of the Activity that app startup completed in
 */
val embStartupActivityName: EmbraceAttributeKey = EmbraceAttributeKey.create("startup_activity")
