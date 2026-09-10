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

internal const val OVERSIZED_PART_FILE_MSG = "Session part file exceeds the maximum size"
