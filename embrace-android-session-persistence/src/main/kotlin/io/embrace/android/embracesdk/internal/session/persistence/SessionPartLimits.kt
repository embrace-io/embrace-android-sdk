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

/**
 * Upper bound on the number of records read back from the span snapshots log. Records supersede one
 * another, so this bounds the decoding work a log can cost rather than the spans it holds.
 */
internal const val MAX_SNAPSHOT_RECORDS: Int = 4 * MAX_PERSISTED_SPANS

/**
 * Size the span snapshots log must reach before it is worth rolling up. Below this the live set is
 * small enough that rewriting it costs little, and rolling up on every append would defeat the log.
 */
internal const val MIN_ROLLUP_BYTES: Long = 32L * 1024

/**
 * Number of records the span snapshots log may hold before it is rolled up. Keeping it below
 * [MAX_SNAPSHOT_RECORDS] means a log written in full can always be read back in full.
 */
internal const val MAX_LOGGED_SNAPSHOT_RECORDS: Int = MAX_SNAPSHOT_RECORDS / 2

internal const val OVERSIZED_PART_FILE_MSG = "Session part file exceeds the maximum size"

internal const val TOO_MANY_PERSISTED_SPANS_MSG = "Session part holds more spans than can be delivered"

internal const val DROPPED_SPAN_SNAPSHOT_MSG = "Span snapshots dropped to keep the log within its limits"
