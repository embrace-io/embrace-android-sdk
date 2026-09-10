package io.embrace.android.embracesdk.internal.session.persistence

/**
 * Upper bound on the size of a session part file. This is meant as an upper bound to prevent memory
 * exhaustion and is considered unlikely for the vast majority of runs.
 */
internal const val MAX_PART_FILE_BYTES: Long = 3L * 1024 * 1024

/**
 * Upper bound on the size of a single record in the completed spans file.
 */
internal const val MAX_RECORD_BYTES: Long = 512L * 1024

/**
 * Upper bound on the number of spans materialised from one session part, counting completed spans
 * and span snapshots together but not the session span itself, which is always delivered.
 *
 * This is the sum of the per-session-part span limits the SDK enforces when capturing telemetry.
 */
internal const val MAX_PERSISTED_SPANS: Int = 4000

internal const val OVERSIZED_PART_FILE_MSG = "Session part file exceeds the maximum size"

internal const val TOO_MANY_PERSISTED_SPANS_MSG = "Session part holds more spans than can be delivered"
