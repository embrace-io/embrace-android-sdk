package io.embrace.android.embracesdk.internal.capture.state

import io.embrace.android.embracesdk.internal.session.persistence.StateRecord
import io.embrace.android.embracesdk.internal.session.persistence.StateValue

/**
 * Told by a [StateRecorder] of what it records, as it records it.
 *
 * Both functions are called under the recorder's lock, on whichever thread reported the change or changed the session
 * part. So they are called in the order of the sequence, and they must do no more than queue what they are given.
 */
interface StateChangeListener {

    /**
     * A state took a new value. [record] is the change, and [snapshot] the value of every state after it. [previous] is the
     * value the state had before, or null if this is its first. A change the recorder counts and does not record, because
     * its journal is full, is not reported.
     */
    fun onChange(record: StateRecord, snapshot: StateSnapshot, previous: StateValue?)

    /**
     * A session part started at [timeMs], when the value of every state was [snapshot].
     */
    fun onPartStart(timeMs: Long, snapshot: StateSnapshot)
}
