package io.embrace.android.embracesdk.internal.session.persistence

import java.io.IOException

/**
 * The records read back from a state journal, in the order they were appended. [lastWrittenMs] is
 * the latest time a write to the journal recorded, or null if none did, and [corruption] is the first
 * problem found while reading, if any.
 */
internal class DecodedStateJournal(
    val records: List<StateRecord>,
    val lastWrittenMs: Long?,
    val corruption: IOException?,
)
