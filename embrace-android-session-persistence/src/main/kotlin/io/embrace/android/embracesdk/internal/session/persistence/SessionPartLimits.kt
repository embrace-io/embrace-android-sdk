package io.embrace.android.embracesdk.internal.session.persistence

/**
 * Upper bound on the size of a session part file. This is meant as an upper bound to prevent memory
 * exhaustion and is considered unlikely for the vast majority of runs.
 *
 * Part files hold uncompressed protobuf, so this is not comparable to the 3Mb ceiling on the
 * gzipped payload: a part is only compressed once it has been reconstructed into an envelope and
 * serialized to JSON, by which point it occupies an eighth or less of what it did here. That
 * ceiling is enforced where the compressed bytes can be measured, in the scheduling service.
 */
internal const val MAX_PART_FILE_BYTES: Long = 12L * 1024 * 1024

/**
 * Upper bound on the size of a single span record, fir both the completed spans file and the span
 * snapshots file.
 *
 * This is a backstop against memory exhaustion rather than a capture limit, so it sits above the
 * largest span the SDK's own limits can produce. A session part span with a lot of experiments,
 * user session properties, and breadcrumbs could plausibly measure around 940Kb. In reality we would
 * not expect many spans to hit this limit.
 */
internal const val MAX_RECORD_BYTES: Long = 1024L * 1024

/**
 * Upper bound on the number of spans materialised from one session part, counting completed spans
 * and span snapshots together but not the session span itself, which is always delivered.
 *
 * This is the sum of the per-session-part span limits the SDK enforces when capturing telemetry.
 */
internal const val MAX_PERSISTED_SPANS: Int = 4000

internal const val OVERSIZED_PART_FILE_MSG = "Session part file exceeds the maximum size"

internal const val TOO_MANY_PERSISTED_SPANS_MSG = "Session part holds more spans than can be delivered"

internal const val DROPPED_SPAN_SNAPSHOT_MSG = "Span snapshots dropped to keep the log within its limits"

internal const val DROPPED_COMPLETED_SPAN_MSG = "Completed span dropped to keep the log within its limits"
