package io.embrace.android.embracesdk.internal.capture.state

import io.embrace.android.embracesdk.internal.arch.datasource.StateDeclaration
import io.embrace.android.embracesdk.internal.arch.datasource.StateUpdate
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.instrumented.OtelLimitsConfigImpl
import io.embrace.android.embracesdk.internal.limits.SpanLimits
import io.embrace.android.embracesdk.internal.session.persistence.StateChange
import io.embrace.android.embracesdk.internal.session.persistence.StateCheckpoint
import io.embrace.android.embracesdk.internal.session.persistence.StateDroppedCount
import io.embrace.android.embracesdk.internal.session.persistence.StateLimitReached
import io.embrace.android.embracesdk.internal.session.persistence.StateNotInSessionCount
import io.embrace.android.embracesdk.internal.session.persistence.StatePartEnd
import io.embrace.android.embracesdk.internal.session.persistence.StatePartStart
import io.embrace.android.embracesdk.internal.session.persistence.StateRecord
import io.embrace.android.embracesdk.internal.session.persistence.StateValue
import io.embrace.android.embracesdk.internal.utils.PropertyUtils
import io.embrace.android.embracesdk.internal.utils.event.EventBus
import io.embrace.android.embracesdk.internal.utils.event.EventHandler

/**
 * Decides the order of state changes, and holds the value of every state.
 *
 * A state exists here only once its data source has declared it, with a [StateDeclaration] that gives its name, its
 * limit and the keys its changes' attributes may use. The first declaration of a name stands for the life of the
 * process, and a later one of the same name changes nothing. An update for a state that has not been declared is
 * discarded: it takes no number, leaves [snapshot] as it is, and is handed to nothing. It is only counted, through
 * [onUndeclaredState].
 *
 * Every [StateUpdate] of a declared state passes through one lock, whichever thread reports it. Under that lock an update that changes its
 * state's value takes the next sequence number and replaces [snapshot] with one that holds the new value. Where a
 * session part is in progress it is then handed to the part's journal. So the order of the journal, the sequence, and
 * the values anything else reads from [snapshot] cannot disagree.
 *
 * Nothing waits for the journal to be persisted. Handing a record to the journal is expected to queue it, and as records
 * are handed over in sequence order, what has been persisted at any moment is the start of the sequence with nothing
 * missing from it.
 *
 * A journal belongs to a session part. Two limits bound what it lists. Each state has its own, which its declaration
 * gives as [StateDeclaration.maxChangesInPart]: the most changes of that state made while the part is in progress. The journal as a
 * whole holds at most [maxChanges] changes, a limit on what a part's payload should
 * carry. A change either limit refuses still takes its number and still replaces its state's value, so everything read
 * from [snapshot] stays right. It is not handed to the journal or to [listener]. It is counted, for its state and for the
 * journal, and the journal ends with those counts, with each limit that refused a change, a state's own and the
 * journal's, and with the value of each state that had a change refused. So the numbers of the changes a journal lists
 * can skip anywhere.
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
 * A change made while no part is in progress is not kept. It takes its number and replaces its state's value like any
 * other, so [snapshot] and [snapshotAt] stay right, and it is counted for its state. It is in no journal, and [listener]
 * is not told of it. The journal of the next part to start opens with every state's value as it then stands, and its
 * part start carries, for each state, how many such changes there were since the journal before it ended. No limit
 * applies to them, as nothing of them is kept but a count.
 *
 * [listener] is told of every change that a journal lists, as it is made, and of every part that starts. It is called
 * under the same lock, so the order it is told things in is the order of the sequence.
 *
 * @param onChangeDropped called for each change the limit on a journal as a whole refuses.
 * @param onStateLimitApplied called with a state's name for each change of it that the state's own limit refuses.
 * @param onUndeclaredState called for each update that is discarded because its state has not been declared.
 * @param keepRecent whether this notes when each change takes effect and remembers the snapshots of the most recent
 * changes, so that [snapshotAt] can say which values held at a time in the recent past. That is for telemetry
 * associated with state, and nothing else reads it.
 */
class StateRecorder(
    private val clock: Clock,
    private val maxChanges: Int = MAX_JOURNAL_CHANGES,
    private val onChangeDropped: () -> Unit = {},
    private val onStateLimitApplied: (state: String) -> Unit = {},
    private val onUndeclaredState: () -> Unit = {},
    private val listener: StateChangeListener? = null,
    private val keepRecent: Boolean = false,
) : EventHandler<StateUpdate> {

    private val lock = Any()

    /**
     * What each declared state is, by its name, guarded by [lock]. A state is added by [declare] and never taken out.
     */
    private val declared = HashMap<String, DeclaredState>()

    private val declarationHandler = EventHandler<StateDeclaration>(::declare)

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
     * For each state, how many of its changes took a number while no journal was open, since the last journal ended or
     * since the process started, guarded by [lock]. The next journal to begin takes them for its part start, and they
     * start again from nothing.
     */
    private val notInSession = LinkedHashMap<String, Int>()

    /**
     * How many changes the journal in progress holds, guarded by [lock].
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
    private var globalLimitApplied = false

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

    /**
     * Registers this recorder with [eventBus] for the declarations and the updates of every state. A data source emits its
     * declaration before its first update, on one thread, and the bus delivers each as it is emitted, so the declaration is
     * always here first. One emitted before this is called is lost, and every update of that state is then discarded.
     */
    fun listenTo(eventBus: EventBus) {
        eventBus.addHandler(StateDeclaration.KEY, declarationHandler)
        eventBus.addHandler(StateUpdate.KEY, this)
    }

    /**
     * Makes the state [declaration] names one this recorder records, with the limit and the attribute keys it gives. The
     * first declaration of a name stands: a state has one limit for the life of the process, so a later declaration of the
     * same name changes nothing. No data source makes one today, since each state has one data source and it declares once.
     *
     * Of the keys it gives, those the SDK reserves are left out, and the first [MAX_ATTRIBUTES] of the rest are kept, which
     * is as many attributes of its own as a change can carry.
     */
    fun declare(declaration: StateDeclaration) {
        synchronized(lock) {
            if (declaration.state !in declared) {
                declared[declaration.state] = DeclaredState(
                    maxChangesInPart = declaration.maxChangesInPart.coerceAtLeast(0),
                    attributeKeys = declaration.attributeKeys.filterNot { it.isReserved() }.take(MAX_ATTRIBUTES).toSet(),
                )
            }
        }
    }

    override fun onEvent(event: StateUpdate) {
        val value = PropertyUtils.truncate(event.value.toString(), MAX_VALUE_LENGTH)
        val system = SchemaType.State.valueType(event.value) != null
        val known = synchronized(lock) {
            val state = declared[event.state]
            if (state != null) {
                record(event, state, value, system)
            }
            state != null
        }
        if (!known) {
            // nothing of it was kept: no number, no value, no record. It is only counted, so that it can be seen
            onUndeclaredState()
        }
    }

    /**
     * Records the update [event] of the declared state [state], whose value as text is [value]. Called under [lock].
     */
    private fun record(event: StateUpdate, state: DeclaredState, value: String, system: Boolean) {
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

        val journal = journal
        if (journal == null) {
            // no session part is in progress: the change has its number and its value is in place, and all that is kept
            // of it is a count for its state, which the next journal's part start carries
            notInSession[event.state] = sumUpToTheMost(notInSession[event.state] ?: 0, 1)
            return
        }
        // the value was replaced first, so a change that is refused here is still what everything stamped from now on carries
        if (refusedByALimit(event, state, journal)) {
            return
        }
        val record = StateRecord(
            sequence = sequence,
            time_ms = event.timeMs,
            change = StateChange(value_ = stateValue, attributes = event.attributes.limitedTo(state.attributeKeys)),
        )
        journal(listOf(record))
        listener?.onChange(record, snapshot, previous)
    }

    /**
     * Whether a limit refuses the change [event] made while [journal] is in progress: the limit [state] was declared with,
     * which is checked first, or the limit on the journal as a whole. A change that is refused is counted for its state and for
     * the journal, and one that is not is counted towards both limits. The first change of a part that a limit refuses
     * also has that limit recorded in [journal], once for each state's own and once for the journal's. Called under [lock].
     */
    private fun refusedByALimit(
        event: StateUpdate,
        state: DeclaredState,
        journal: (List<StateRecord>) -> Unit,
    ): Boolean {
        val tally = tallyOf(event.state)
        // the number of the state's last change in the part, which its final value holds from if a change of it is refused
        tally.lastChange = sequence
        val byItsOwnLimit = tally.listed >= state.maxChangesInPart
        if (!byItsOwnLimit && recorded < maxChanges) {
            tally.listed++
            recorded++
            return false
        }
        // counted before anything is told of it, so that the counts are right whatever a callback does
        tally.refused++
        dropped++
        if (byItsOwnLimit) {
            val firstInThePart = tally.limit == null
            tally.limit = state.maxChangesInPart
            if (firstInThePart) {
                journal(listOf(limitReached(event, state.maxChangesInPart, event.state)))
            }
            onStateLimitApplied(event.state)
        } else {
            val firstInThePart = !globalLimitApplied
            globalLimitApplied = true
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
     * order, and must not block. It opens with every state's value as it stands, as of the last change made, and with
     * the count for each state of the changes made with no journal open since the last one ended.
     *
     * Those counts start again from nothing here, whatever becomes of the records [journal] is handed: this does not
     * learn whether they were written, so a count is given to one journal and never carried to a later one.
     */
    fun beginJournal(timeMs: Long, journal: (List<StateRecord>) -> Unit) {
        synchronized(lock) {
            // a part should always end before the next one starts, but a journal left open must still be closed
            endJournalUnderLock(timeMs)

            val opening = current
            val records = listOf(
                StateRecord(
                    sequence = opening.sequence,
                    time_ms = timeMs,
                    checkpoint = StateCheckpoint(values = opening.values.values.toList()),
                ),
                StateRecord(
                    sequence = opening.sequence,
                    time_ms = timeMs,
                    part_start = StatePartStart(
                        not_in_session_counts = notInSession.map { (state, count) -> StateNotInSessionCount(state, count) },
                    ),
                ),
            )
            notInSession.clear()
            // both limits count only the changes made while this journal is open: every state's count of what is listed
            // starts at nothing here, as the last journal's end emptied the tallies
            recorded = 0
            dropped = 0
            globalLimitApplied = false
            this.journal = journal
            journal(records)
            listener?.onPartStart(timeMs, current)
        }
    }

    /**
     * Ends the journal of the session part that ended at [timeMs]. Changes made from here on are in no journal, and are
     * counted for the next one to begin.
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
                        dropped_counts = tallies.mapNotNull { (state, tally) -> tally.droppedCount(state) },
                        global_limit = if (globalLimitApplied) maxChanges else null,
                    ),
                ),
            ),
        )
        this.journal = null
        recorded = 0
        dropped = 0
        globalLimitApplied = false
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
     * What the journal's end says of [state] if the journal does not list every change of it: how many it does not list,
     * the state's own limit if that refused one, and the number of the state's last change if a limit refused one, which
     * is where the value the part ended with holds from. Null if every change of the state is listed.
     */
    private fun StateTally.droppedCount(state: String): StateDroppedCount? {
        val count = sumUpToTheMost(refused, reported)
        if (count == 0) {
            return null
        }
        return StateDroppedCount(
            state = state,
            count = count,
            limit = limit,
            final_sequence = if (refused > 0) lastChange else null,
        )
    }

    /**
     * The attributes of a change that it keeps: those under one of [keys], which are the keys its state was declared with,
     * no more than [MAX_ATTRIBUTES] of them and none of them reserved. Every other attribute is dropped.
     */
    private fun Map<String, String>.limitedTo(keys: Set<String>): Map<String, String> {
        if (isEmpty() || keys.isEmpty()) {
            return emptyMap()
        }
        val limited = LinkedHashMap<String, String>(minOf(size, keys.size))
        for ((key, value) in this) {
            if (key in keys) {
                limited[PropertyUtils.truncate(key, MAX_KEY_LENGTH)] = PropertyUtils.truncate(value, MAX_VALUE_LENGTH)
            }
        }
        return limited
    }

    /**
     * Whether a change's own attribute may not use this key, so that a declaration that names it does not get it: it is
     * under [RESERVED_KEY_PREFIX], which is the SDK's, or under v1's stem, or is one of the two stems alone, so that none
     * of a change's attributes can replace or sit beside one the SDK sets. There is no exception: the count of transitions
     * an instrumentation dropped, which v1 writes under its stem, is reported as [StateUpdate.droppedChanges] and counted
     * for the state.
     */
    private fun String.isReserved(): Boolean =
        startsWith(RESERVED_KEY_PREFIX) || this == RESERVED_KEY_PREFIX.removeSuffix(".") ||
            startsWith(V1_KEY_PREFIX) || this == V1_KEY_PREFIX.removeSuffix(".")

    /**
     * What a state was declared to be: the most changes of it that one session part lists, and the keys its changes'
     * attributes may use.
     */
    private class DeclaredState(val maxChangesInPart: Int, val attributeKeys: Set<String>)

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

        /**
         * The number of the last change of the state made during the part, listed or refused.
         */
        var lastChange = 0L
    }

    companion object {
        /**
         * The most changes one journal holds. The limit is on what a session part's payload should carry, not on what can
         * be recorded.
         */
        const val MAX_JOURNAL_CHANGES: Int = 10_000

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
