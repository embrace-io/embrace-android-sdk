package io.embrace.android.embracesdk.internal.arch.attrs

import io.embrace.android.embracesdk.semconv.EmbAndroidAttributes
import io.embrace.android.embracesdk.semconv.EmbAttachmentAttributes
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.embrace.android.embracesdk.semconv.EmbSpanAttributes
import io.embrace.android.embracesdk.semconv.EmbStateTransitionAttributes
import io.embrace.android.embracesdk.semconv.EmbTelemetryAttributes

/**
 * A snapshot of the current call stack of the threads running in the app process per [io.embrace.android.embracesdk.internal.payload.ThreadInfo]
 */
val embAndroidThreads: EmbraceAttributeKey = EmbraceAttributeKey(EmbAndroidAttributes.EMB_ANDROID_THREADS)

/**
 * Sequence number for the number of crashes captured by Embrace on the device, reported on every crash
 */
val embCrashNumber: EmbraceAttributeKey = EmbraceAttributeKey(EmbAndroidAttributes.EMB_ANDROID_CRASH_NUMBER)

/**
 * Sequence number for the number of AEI crashes captured by Embrace on the device, reported on every AEI crash
 */
val embAeiNumber: EmbraceAttributeKey = EmbraceAttributeKey(EmbAndroidAttributes.EMB_ANDROID_AEI_CRASH_NUMBER)

/**
 * Attribute name for the exception handling type - whether it's handled or unhandled
 */
val embExceptionHandling: EmbraceAttributeKey = EmbraceAttributeKey(EmbAndroidAttributes.EMB_EXCEPTION_HANDLING)

/**
 * Monotonically increasing sequence ID given to completed span that is expected to be sent to the server
 */
val embProcessIdentifier: EmbraceAttributeKey = EmbraceAttributeKey(EmbSessionAttributes.EMB_PROCESS_IDENTIFIER)

/**
 * Attribute name for the unique ID assigned to each app instance
 */
val embSequenceId: EmbraceAttributeKey = EmbraceAttributeKey(EmbSessionAttributes.EMB_PRIVATE_SEQUENCE_ID)

/**
 * Attribute name for the application state (foreground/background) at the time the log was recorded
 */
val embState: EmbraceAttributeKey = EmbraceAttributeKey(EmbSessionAttributes.EMB_STATE)

/**
 * Attribute name for whether the session is a cold start
 */
val embColdStart: EmbraceAttributeKey = EmbraceAttributeKey(EmbSessionAttributes.EMB_COLD_START)

/**
 * Attribute name for session number (integer sequence ID)
 */
val embSessionNumber: EmbraceAttributeKey = EmbraceAttributeKey(EmbSessionAttributes.EMB_SESSION_NUMBER)

/**
 * Attribute name that indicates whether the session was ended by the SDK or an unexpected termination
 */
val embCleanExit: EmbraceAttributeKey = EmbraceAttributeKey(EmbSessionAttributes.EMB_CLEAN_EXIT)

/**
 * Attribute name that indicates whether the session was terminated
 */
val embTerminated: EmbraceAttributeKey = EmbraceAttributeKey(EmbSessionAttributes.EMB_TERMINATED)

/**
 * Attribute name that represents last known time that the session existed (nanoseconds since epoch)
 */
val embHeartbeatTimeUnixNano: EmbraceAttributeKey =
    EmbraceAttributeKey(EmbSessionAttributes.EMB_HEARTBEAT_TIME_UNIX_NANO)

/**
 * Attribute name that identifies the crash report tied to the session
 */
val embCrashId: EmbraceAttributeKey = EmbraceAttributeKey(EmbSessionAttributes.EMB_CRASH_ID)

/**
 * Attribute name that identifies the session start type
 */
val embSessionStartType: EmbraceAttributeKey = EmbraceAttributeKey(EmbSessionAttributes.EMB_SESSION_START_TYPE)

/**
 * Attribute name that identifies the session end type
 */
val embSessionEndType: EmbraceAttributeKey = EmbraceAttributeKey(EmbSessionAttributes.EMB_SESSION_END_TYPE)

/**
 * Attribute name that identifies the startup duration
 */
val embSessionStartupDuration: EmbraceAttributeKey = EmbraceAttributeKey(EmbSessionAttributes.EMB_STARTUP_DURATION)

/**
 * Attribute name that identifies the error log count in a session
 */
val embErrorLogCount: EmbraceAttributeKey = EmbraceAttributeKey(EmbSessionAttributes.EMB_ERROR_LOG_COUNT)

/**
 * Attribute name that identifies the number of free bytes on disk
 */
val embFreeDiskBytes: EmbraceAttributeKey = EmbraceAttributeKey(EmbSessionAttributes.EMB_DISK_FREE_BYTES)

/**
 * Attribute name that identifies how a signal should be delivered to the Embrace backend
 */
val embSendMode: EmbraceAttributeKey = EmbraceAttributeKey(EmbSessionAttributes.EMB_PRIVATE_SEND_MODE)

/**
 * The size of a log message attachment in bytes
 */
val embAttachmentSize: EmbraceAttributeKey = EmbraceAttributeKey(EmbAttachmentAttributes.EMB_ATTACHMENT_SIZE)

/**
 * The URL of a user-hosted log message attachment
 */
val embAttachmentUrl: EmbraceAttributeKey = EmbraceAttributeKey(EmbAttachmentAttributes.EMB_ATTACHMENT_URL)

/**
 * The ID of a user-hosted log message attachment
 */
val embAttachmentId: EmbraceAttributeKey = EmbraceAttributeKey(EmbAttachmentAttributes.EMB_ATTACHMENT_ID)

/**
 * The error code associated with a failed log message attachment
 */
val embAttachmentErrorCode: EmbraceAttributeKey = EmbraceAttributeKey(EmbAttachmentAttributes.EMB_ATTACHMENT_ERROR_CODE)

/**
 * The name of the Activity that app startup completed in
 */
val embStartupActivityName: EmbraceAttributeKey = EmbraceAttributeKey(EmbSessionAttributes.EMB_STARTUP_ACTIVITY)

/**
 * The initial value a state began with
 */
val embStateInitialValue: EmbraceAttributeKey =
    EmbraceAttributeKey(EmbStateTransitionAttributes.EMB_STATE_INITIAL_VALUE)

/**
 * The value a state transitioned into in the given event
 */
val embStateNewValue: EmbraceAttributeKey = EmbraceAttributeKey(EmbStateTransitionAttributes.EMB_STATE_NEW_VALUE)

/**
 * The total number of transitions recorded explicitly in the given state span
 */
val embStateTransitionCount: EmbraceAttributeKey =
    EmbraceAttributeKey(EmbStateTransitionAttributes.EMB_STATE_TRANSITION_COUNT)

/**
 * The number of transitions dropped because they didn't occur during a session since the last time a transition event was recorded
 */
val embStateNotInSession: EmbraceAttributeKey =
    EmbraceAttributeKey(EmbStateTransitionAttributes.EMB_STATE_NOT_IN_SESSION)

/**
 * The number of transitions dropped by the instrumentation since the last time a transition event was recorded
 */
val embStateDroppedByInstrumentation: EmbraceAttributeKey = EmbraceAttributeKey(
    EmbStateTransitionAttributes.EMB_STATE_DROPPED_BY_INSTRUMENTATION
)

/**
 * Semantic meaning of a span link, defining the relationship between the containing span and the linked span
 */
val embLinkType: EmbraceAttributeKey = EmbraceAttributeKey(EmbSpanAttributes.EMB_LINK_TYPE)

/**
 * Storage used by Embrace in bytes
 */
val embStorageUsed: EmbraceAttributeKey = EmbraceAttributeKey(EmbTelemetryAttributes.EMB_STORAGE_USED)

/**
 * Storage available on the device in bytes
 */
val embStorageAvailable: EmbraceAttributeKey = EmbraceAttributeKey(EmbTelemetryAttributes.EMB_STORAGE_AVAILABLE)

/**
 * Whether OkHttp3 is present on the classpath
 */
val embOkhttp3: EmbraceAttributeKey = EmbraceAttributeKey(EmbTelemetryAttributes.EMB_OKHTTP3)

/**
 * The version of OkHttp3 found on the classpath
 */
val embOkhttp3OnClasspath: EmbraceAttributeKey = EmbraceAttributeKey(EmbTelemetryAttributes.EMB_OKHTTP3_ON_CLASSPATH)

/**
 * The version of Kotlin found on the classpath
 */
val embKotlinOnClasspath: EmbraceAttributeKey = EmbraceAttributeKey(EmbTelemetryAttributes.EMB_KOTLIN_ON_CLASSPATH)

/**
 * Whether the app is running on an emulator
 */
val embIsEmulator: EmbraceAttributeKey = EmbraceAttributeKey(EmbTelemetryAttributes.EMB_IS_EMULATOR)
