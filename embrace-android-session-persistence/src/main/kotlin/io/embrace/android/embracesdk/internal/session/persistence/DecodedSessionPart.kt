package io.embrace.android.embracesdk.internal.session.persistence

import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload

/**
 * What a session part's files decode to: the [envelope] to deliver, and [lastWrittenMs], the latest time any
 * of its files was written by the SDK clock, or null if none of them records one.
 *
 * [lastWrittenMs] is only needed to resurrect a part left by a process that died, but whether a part is dead
 * is only known once it has been decoded, so it is read for every part along with its envelope.
 *
 * [stateJournal] is the part's state journal, from which its change log is rendered, or null if it has none.
 */
class DecodedSessionPart(
    val envelope: Envelope<SessionPartPayload>,
    val lastWrittenMs: Long?,
    val stateJournal: DecodedStateJournal? = null,
)
