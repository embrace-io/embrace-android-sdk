package io.embrace.android.embracesdk.internal.capture.state

import io.embrace.android.embracesdk.internal.arch.datasource.StateUpdate
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.instrumented.OtelLimitsConfigImpl
import io.embrace.android.embracesdk.internal.limits.SpanLimits
import io.embrace.android.embracesdk.internal.session.persistence.StateChange
import io.embrace.android.embracesdk.internal.session.persistence.StateCheckpoint
import io.embrace.android.embracesdk.internal.session.persistence.StatePartEnd
import io.embrace.android.embracesdk.internal.session.persistence.StatePartStart
import io.embrace.android.embracesdk.internal.session.persistence.StateRecord
import io.embrace.android.embracesdk.internal.session.persistence.StateValue
import io.embrace.android.embracesdk.internal.utils.event.EventHandler

/**
 * Decides the order of state changes, and holds the value of every state.
 *
 * Every [StateUpdate] passes through one lock, whichever thread reports it. Under that lock an update that changes its
 * state's value takes the next sequence number, replaces [snapshot] with one that holds the new value, and is handed to
 * the journal. So the order of the journal, the sequence, and the values anything else reads from [snapshot] cannot
 * disagree.
 *
 * Nothing waits for the journal to be persisted. Handing a record to the journal is expected to queue it, and as records
 * are handed over in sequence order, what has been persisted at any moment is the start of the sequence with nothing
 * missing from it.
 *
 * A journal belongs to a session part, and holds at most [maxChanges] changes, a limit on what a part's payload should
 * carry. Changes past it are counted, and the journal ends with every state's value in their place.
 *
 * Changes made while no part is in progress are held, and open the journal of the next part to start. At most
 * [maxHeldChanges] are held. Past that the oldest is folded into the values that journal opens with, so the journal still
 * leads from its opening values to the present with no change missing, and holds the changes nearest the part.
 *
 * [listener] is told of every change that is recorded as it is made, whether or not a part is in progress, and of every
 * part that starts. It is called under the same lock, so the order it is told things in is the order of the sequence.
 *
 * @param keepRecent whether this notes when each change takes effect and remembers the snapshots of the most recent
 * changes, so that [snapshotAt] can say which values held at a time in the recent past. That is for telemetry
 * associated with state, and nothing else reads it.
 */
class StateRecorder(
    private val clock: Clock,
    private val maxChanges: Int = MAX_JOURNAL_CHANGES,
    private val maxHeldChanges: Int = maxChanges / HELD_SHARE,
    private val onChangeDropped: () -> Unit = {},
    private val listener: StateChangeListener? = null,
    private val keepRecent: Boolean = false,
) : EventHandler<StateUpdate> {

    private val lock = Any()

    /**
     * The number of the last change, guarded by [lock].
     */
    private var sequence = 0L

    @Volatile
    private var current: StateSnapshot = StateSnapshot.NONE

    /**
     * The last [RECENT_SNAPSHOTS] snapshots where [keepRecent], oldest first and [current] last, and empty otherwise. It
     * begins with the one that holds no values, which is what every state had before the first change.
     *
     * It holds that many references and no more, so the memory it keeps does not grow with the number of changes. A
     * snapshot is a map with an entry for each state. It shares each value with the snapshots next to it, but for the
     * one value that changed, and keeps whatever was built from it while it was current: its attributes, if a log was
     * stamped or a span was exported with them.
     *
     * The array is never written to once it is here. A change replaces it with a copy under [lock], so [snapshotAt] reads
     * it without the lock.
     */
    @Volatile
    private var recent: Array<StateSnapshot> = if (keepRecent) arrayOf(StateSnapshot.NONE) else emptyArray()

    /**
     * Where the records of the session part in progress are handed, guarded by [lock]. Null while no part is in progress.
     */
    private var journal: ((List<StateRecord>) -> Unit)? = null

    /**
     * The values the next journal opens with, guarded by [lock]: every state's value when the last journal ended, with the
     * changes folded into it since.
     */
    private var opening: StateSnapshot = StateSnapshot.NONE

    /**
     * When [opening] held, guarded by [lock].
     */
    private var openingTimeMs: Long = clock.now()

    /**
     * The changes made since [opening], oldest first, guarded by [lock].
     */
    private val held = ArrayDeque<StateRecord>()

    /**
     * How many changes have been folded into [opening] since the last journal ended, guarded by [lock].
     */
    private var folded = 0

    /**
     * How many changes the journal in progress holds, counting those it opened with, guarded by [lock].
     */
    private var recorded = 0

    /**
     * How many changes the journal in progress does not hold because [recorded] had reached [maxChanges], guarded by [lock].
     */
    private var dropped = 0

    /**
     * The value of every state now.
     */
    fun snapshot(): StateSnapshot = current

    /**
     * The value of every state at [timeMs], in milliseconds by this recorder's clock: the snapshot of the last change
     * recorded at or before that time. Null if that is not known, which is when the time is from before the oldest
     * snapshot remembered took effect, or nothing is remembered because not [keepRecent].
     *
     * A time equal to the time a change was recorded at is after that change, so it gets that change's snapshot. Where
     * several changes were recorded in the same millisecond, it gets the last of them.
     *
     * It takes no lock, so it can be called on a thread that starts or ends a span.
     */
    fun snapshotAt(timeMs: Long): StateSnapshot? {
        val recent = recent
        for (index in recent.size - 1 downTo 0) {
            val snapshot = recent[index]
            if (snapshot.sinceMs <= timeMs) {
                return snapshot
            }
        }
        return null
    }

    override fun onEvent(event: StateUpdate) {
        val value = event.value.toString().take(MAX_VALUE_LENGTH)
        val system = SchemaType.State.valueType(event.value) != null
        synchronized(lock) {
            val previous = current.values[event.state]
            if (previous != null && previous.value_ == value && previous.system == system) {
                return
            }
            val stateValue = StateValue(state = event.state, value_ = value, system = system)
            sequence++
            val snapshot = if (keepRecent) {
                current.with(sequence, stateValue, clock.now()).also(::remember)
            } else {
                // nothing reads when a change took effect, so the clock is not read for it
                current.with(sequence, stateValue)
            }
            current = snapshot

            val journal = journal
            if (journal != null && recorded >= maxChanges) {
                dropped++
                onChangeDropped()
                return
            }
            val record = StateRecord(
                sequence = sequence,
                time_ms = event.timeMs,
                change = StateChange(value_ = stateValue, attributes = event.attributes.limited()),
            )
            if (journal != null) {
                recorded++
                journal(listOf(record))
            } else {
                hold(record)
            }
            listener?.onChange(record, snapshot)
        }
    }

    /**
     * Begins the journal of a session part that started at [timeMs]. [journal] is handed every record of the part, in
     * order, starting with the changes held since the last part ended, and must not block.
     */
    fun beginJournal(timeMs: Long, journal: (List<StateRecord>) -> Unit) {
        synchronized(lock) {
            // a part should always end before the next one starts, but a journal left open must still be closed
            endJournalUnderLock(timeMs)

            val records = ArrayList<StateRecord>(held.size + 2)
            records.add(
                StateRecord(
                    sequence = opening.sequence,
                    time_ms = openingTimeMs,
                    checkpoint = StateCheckpoint(values = opening.values.values.toList()),
                ),
            )
            records.addAll(held)
            records.add(
                StateRecord(
                    sequence = sequence,
                    time_ms = timeMs,
                    part_start = StatePartStart(dropped_changes = folded),
                ),
            )
            recorded = held.size
            dropped = 0
            held.clear()
            folded = 0
            this.journal = journal
            journal(records)
            listener?.onPartStart(timeMs, current)
        }
    }

    /**
     * Ends the journal of the session part that ended at [timeMs]. Changes made from here on are held for the next.
     */
    fun endJournal(timeMs: Long) {
        synchronized(lock) {
            endJournalUnderLock(timeMs)
        }
    }

    private fun endJournalUnderLock(timeMs: Long) {
        val journal = journal ?: return
        val snapshot = current
        journal(
            listOf(
                StateRecord(
                    sequence = sequence,
                    time_ms = timeMs,
                    part_end = StatePartEnd(
                        dropped_changes = dropped,
                        values = if (dropped > 0) snapshot.values.values.toList() else emptyList(),
                    ),
                ),
            ),
        )
        this.journal = null
        opening = snapshot
        openingTimeMs = timeMs
        recorded = 0
        dropped = 0
    }

    /**
     * Adds [snapshot] to [recent], in place of the oldest if that is one more than is remembered. Called under [lock].
     */
    private fun remember(snapshot: StateSnapshot) {
        val before = recent
        val kept = minOf(before.size, RECENT_SNAPSHOTS - 1)
        val dropped = before.size - kept
        recent = Array(kept + 1) { index -> if (index < kept) before[dropped + index] else snapshot }
    }

    /**
     * Keeps [record] for the next journal, folding the oldest change held into [opening] if that is one more than is kept.
     */
    private fun hold(record: StateRecord) {
        held.addLast(record)
        if (held.size > maxHeldChanges) {
            val oldest = held.removeFirst()
            oldest.change?.value_?.let { value -> opening = opening.with(oldest.sequence, value) }
            openingTimeMs = oldest.time_ms
            folded++
            onChangeDropped()
        }
    }

    private fun Map<String, String>.limited(): Map<String, String> {
        if (isEmpty()) {
            return this
        }
        val limited = LinkedHashMap<String, String>(minOf(size, MAX_ATTRIBUTES))
        for ((key, value) in this) {
            if (limited.size == MAX_ATTRIBUTES) {
                break
            }
            limited[key.take(MAX_KEY_LENGTH)] = value.take(MAX_VALUE_LENGTH)
        }
        return limited
    }

    companion object {
        /**
         * The most changes one journal holds. The limit is on what a session part's payload should carry, not on what can
         * be recorded.
         */
        const val MAX_JOURNAL_CHANGES: Int = 10_000

        /**
         * The share of a journal's changes that can be from before its session part started: one in this many.
         */
        private const val HELD_SHARE = 10

        /**
         * How many snapshots are remembered for [snapshotAt]: the current one and the 63 before it. A span that starts or
         * ends at a time from before the oldest of them took effect is not linked to that time.
         */
        internal const val RECENT_SNAPSHOTS = 64

        /**
         * The attributes an event is given when a change is delivered as one, beyond the change's own: its new value, and
         * the type of that value.
         */
        private const val ATTRIBUTES_OF_AN_EVENT = 2

        // the limits a state's value and a change's attributes would have met as a span event the SDK records itself
        private val MAX_VALUE_LENGTH = SpanLimits.Internal.maxAttributeValueLength
        private val MAX_KEY_LENGTH = SpanLimits.Internal.maxAttributeKeyLength
        private val MAX_ATTRIBUTES = OtelLimitsConfigImpl.getMaxEventAttributeCount() - ATTRIBUTES_OF_AN_EVENT
    }
}
