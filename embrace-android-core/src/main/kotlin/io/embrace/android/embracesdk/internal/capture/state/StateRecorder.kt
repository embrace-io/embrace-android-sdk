package io.embrace.android.embracesdk.internal.capture.state

import io.embrace.android.embracesdk.internal.arch.datasource.StateUpdate
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.instrumented.OtelLimitsConfigImpl
import io.embrace.android.embracesdk.internal.limits.SpanLimits
import io.embrace.android.embracesdk.internal.session.persistence.StateChange
import io.embrace.android.embracesdk.internal.session.persistence.StateCheckpoint
import io.embrace.android.embracesdk.internal.session.persistence.StateDroppedCount
import io.embrace.android.embracesdk.internal.session.persistence.StateLimitReached
import io.embrace.android.embracesdk.internal.session.persistence.StatePartEnd
import io.embrace.android.embracesdk.internal.session.persistence.StatePartStart
import io.embrace.android.embracesdk.internal.session.persistence.StateRecord
import io.embrace.android.embracesdk.internal.session.persistence.StateValue
import io.embrace.android.embracesdk.internal.utils.PropertyUtils
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
 * A journal belongs to a session part. Two limits bound what it lists. Each state has its own, which its update carries
 * as [StateUpdate.maxChangesInPart]: the most changes of that state made while the part is in progress. The journal as a
 * whole holds at most [maxChanges] changes, counting those it opened with, a limit on what a part's payload should
 * carry. A change either limit refuses still takes its number and still replaces its state's value, so everything read
 * from [snapshot] stays right. It is not handed to the journal or to [listener]. It is counted, for its state and for the
 * journal, and the journal ends with those counts, with each limit that refused a change, a state's own and the
 * journal's, and with the value of each state that had a change refused. So the numbers of the changes a journal lists
 * can skip anywhere after its part start.
 *
 * The first time in a part that a limit refuses a change, the journal is also handed a record of that limit there and
 * then: the state and its limit, or the journal's limit with no state. So a journal that never gets its end, because the
 * process died, still says which limits were reached, and so whose changes its gaps can be. That record is not a change.
 * It takes no number of its own, counts towards no limit, and [listener] is not told of it.
 *
 * An update can also say how many changes its reporter never passed on, as [StateUpdate.droppedChanges]. Those take no
 * number, as nothing says when they happened, and are counted for their state alone, in the journal in progress, or in
 * the next to start where none is.
 *
 * Changes made while no part is in progress are held, and open the journal of the next part to start. At most
 * [maxHeldChanges] are held. Past that the oldest is folded into the values that journal opens with, so the journal still
 * leads from its opening values to the present with no change missing, and holds the changes nearest the part.
 *
 * [listener] is told of every change that is recorded as it is made, whether or not a part is in progress, and of every
 * part that starts. It is called under the same lock, so the order it is told things in is the order of the sequence.
 *
 * @param onChangeDropped called for each change the limit on a journal as a whole refuses, and for each held change that
 * is folded.
 * @param onStateLimitApplied called with a state's name for each change of it that the state's own limit refuses.
 * @param keepRecent whether this notes when each change takes effect and remembers the snapshots of the most recent
 * changes, so that [snapshotAt] can say which values held at a time in the recent past. That is for telemetry
 * associated with state, and nothing else reads it.
 */
class StateRecorder(
    private val clock: Clock,
    private val maxChanges: Int = MAX_JOURNAL_CHANGES,
    private val maxHeldChanges: Int = maxChanges / HELD_SHARE,
    private val onChangeDropped: () -> Unit = {},
    private val onStateLimitApplied: (state: String) -> Unit = {},
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
     * How many changes made during the part the journal in progress does not hold, because a limit refused them: their
     * state's own, or [maxChanges]. Guarded by [lock].
     */
    private var dropped = 0

    /**
     * Whether [maxChanges] refused a change of the journal in progress, guarded by [lock].
     */
    private var sharedLimitApplied = false

    /**
     * For each state, what the journal in progress lists of it and does not, guarded by [lock]. It is emptied when a
     * journal ends, so that each journal counts from nothing. While no journal is in progress it holds only the changes
     * reporters said they never passed on, which the next journal to start counts.
     */
    private val tallies = LinkedHashMap<String, StateTally>()

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
        val value = PropertyUtils.truncate(event.value.toString(), MAX_VALUE_LENGTH)
        val system = SchemaType.State.valueType(event.value) != null
        synchronized(lock) {
            if (event.droppedChanges > 0) {
                // what a reporter never passed on has no place in the order of changes, so it is counted and takes no number
                val tally = tallyOf(event.state)
                tally.reported = sumUpToTheMost(tally.reported, event.droppedChanges)
            }
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

            // the value was replaced first, so a change that is refused here is still what everything stamped from now on carries
            val journal = journal
            if (journal != null && refusedByALimit(event, journal)) {
                return
            }
            val record = StateRecord(
                sequence = sequence,
                time_ms = event.timeMs,
                change = StateChange(value_ = stateValue, attributes = event.attributes.limited()),
            )
            if (journal != null) {
                journal(listOf(record))
            } else {
                hold(record)
            }
            listener?.onChange(record, snapshot, previous)
        }
    }

    /**
     * Whether a limit refuses the change [event] made while [journal] is in progress: its state's own limit, which is
     * checked first, or the limit on the journal as a whole. A change that is refused is counted for its state and for
     * the journal, and one that is not is counted towards both limits. The first change of a part that a limit refuses
     * also has that limit recorded in [journal], once for each state's own and once for the journal's. Called under [lock].
     */
    private fun refusedByALimit(event: StateUpdate, journal: (List<StateRecord>) -> Unit): Boolean {
        val tally = tallyOf(event.state)
        val byItsOwnLimit = tally.listed >= event.maxChangesInPart
        if (!byItsOwnLimit && recorded < maxChanges) {
            tally.listed++
            recorded++
            return false
        }
        // counted before anything is told of it, so that the counts are right whatever a callback does
        tally.refused++
        dropped++
        if (byItsOwnLimit) {
            val limit = event.maxChangesInPart.coerceAtLeast(0)
            val firstInThePart = tally.limit == null
            tally.limit = limit
            if (firstInThePart) {
                journal(listOf(limitReached(event, limit, event.state)))
            }
            onStateLimitApplied(event.state)
        } else {
            val firstInThePart = !sharedLimitApplied
            sharedLimitApplied = true
            if (firstInThePart) {
                journal(listOf(limitReached(event, maxChanges, state = null)))
            }
            onChangeDropped()
        }
        return true
    }

    /**
     * The record that [limit] refused the change [event] made, which is the last change there is: the limit of [state], or
     * the limit on the journal as a whole where there is no state. It carries that change's number and time, as nothing
     * else says when the limit was reached, and takes no number of its own.
     */
    private fun limitReached(event: StateUpdate, limit: Int, state: String?): StateRecord = StateRecord(
        sequence = sequence,
        time_ms = event.timeMs,
        limit_reached = StateLimitReached(state = state, limit = limit),
    )

    private fun tallyOf(state: String): StateTally = tallies.getOrPut(state, ::StateTally)

    /**
     * The sum of two counts, or the most a count can hold where they come to more, so that a reporter that says it
     * dropped a great many changes cannot turn a count into a number that reads as none.
     */
    private fun sumUpToTheMost(count: Int, more: Int): Int =
        (count.toLong() + more).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

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
            // the changes held from before the part count towards the limit on the journal, and towards no state's own:
            // every state's count of what is listed starts at nothing here, as the last journal's end emptied the tallies
            recorded = held.size
            dropped = 0
            sharedLimitApplied = false
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
                        // only a state that had a change refused: the changes listed lead to the value of every other
                        values = if (dropped > 0) {
                            snapshot.values.values.filter { (tallies[it.state]?.refused ?: 0) > 0 }
                        } else {
                            emptyList()
                        },
                        dropped_counts = tallies.mapNotNull { (state, tally) ->
                            val count = sumUpToTheMost(tally.refused, tally.reported)
                            if (count > 0) StateDroppedCount(state = state, count = count, limit = tally.limit) else null
                        },
                        shared_limit = if (sharedLimitApplied) maxChanges else null,
                    ),
                ),
            ),
        )
        this.journal = null
        opening = snapshot
        openingTimeMs = timeMs
        recorded = 0
        dropped = 0
        sharedLimitApplied = false
        tallies.clear()
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
            // a reserved key is dropped before the count is applied, so that it uses up none of what a change can carry
            if (!key.isReserved()) {
                if (limited.size == MAX_ATTRIBUTES) {
                    break
                }
                limited[PropertyUtils.truncate(key, MAX_KEY_LENGTH)] = PropertyUtils.truncate(value, MAX_VALUE_LENGTH)
            }
        }
        return limited
    }

    /**
     * Whether a change's own attribute may not use this key: it is under [RESERVED_KEY_PREFIX], which is the SDK's, or
     * under v1's stem, or is one of the two stems alone, so that none of a change's attributes can replace or sit beside
     * one the SDK sets. There is no exception: the count of transitions an instrumentation dropped, which v1 writes
     * under its stem, is reported as [StateUpdate.droppedChanges] and counted for the state.
     */
    private fun String.isReserved(): Boolean =
        startsWith(RESERVED_KEY_PREFIX) || this == RESERVED_KEY_PREFIX.removeSuffix(".") ||
            startsWith(V1_KEY_PREFIX) || this == V1_KEY_PREFIX.removeSuffix(".")

    /**
     * What a journal in progress lists, and does not list, of one state.
     */
    private class StateTally {
        /**
         * How many changes of the state made during the part the journal lists, which is what the state's own limit bounds.
         */
        var listed = 0

        /**
         * How many changes of the state made during the part a limit refused: the state's own, or the journal's.
         */
        var refused = 0

        /**
         * How many changes of the state its reporter said it never passed on.
         */
        var reported = 0

        /**
         * The state's own limit, if that refused a change of the state during the part.
         */
        var limit: Int? = null
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
         * The attributes an event is given when a change is delivered as one, beyond the change's own: its new value, the
         * type of that value, and its number.
         */
        private const val ATTRIBUTES_OF_AN_EVENT = 3

        /**
         * The start of every key the SDK sets on an event, which a change's own attributes may not use.
         */
        private const val RESERVED_KEY_PREFIX = "emb.app.state."

        /**
         * The stem of v1's keys, which a change's own attributes may not use either, as a reader that knows them could take one
         * for the SDK's.
         */
        private const val V1_KEY_PREFIX = "emb.state."

        // the limits a state's value and a change's attributes would have met as a span event the SDK records itself, and
        // each is cut the way such an attribute is: to three characters short of the limit and "...", splitting no pair
        private val MAX_VALUE_LENGTH = SpanLimits.Internal.maxAttributeValueLength
        private val MAX_KEY_LENGTH = SpanLimits.Internal.maxAttributeKeyLength
        private val MAX_ATTRIBUTES = OtelLimitsConfigImpl.getMaxEventAttributeCount() - ATTRIBUTES_OF_AN_EVENT
    }
}
