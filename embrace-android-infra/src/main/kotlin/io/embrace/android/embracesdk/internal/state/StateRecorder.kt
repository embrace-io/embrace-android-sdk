package io.embrace.android.embracesdk.internal.state

import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.telemetry.AppliedLimitType
import io.embrace.android.embracesdk.internal.telemetry.InternalTelemetryService
import io.embrace.android.embracesdk.internal.utils.PropertyUtils
import io.embrace.android.embracesdk.internal.utils.event.EventBus

/**
 * The one place that holds the truth about state: every state's value, and every change of any state in one order.
 *
 * Each change takes the next number of one sequence, which starts at 1 and belongs to this process. Taking the number and replacing
 * the values happen together under [lock], which is held only for work in memory, so that reporting a change never waits for a
 * write and costs the same however many came before. What reads the values now, a log being stamped or a span starting or
 * ending as it runs, reads the whole of one [StateSnapshot] with no lock. Only a span given a time earlier than the last change
 * takes the lock, to be placed among the recent snapshots.
 *
 * A change made while a session part is open is passed on to the [journal], which the part's change log is later made from, and to
 * [replay], unless a limit refuses it. A refused change, and one made with no session part, still takes its number and still
 * replaces the state's value, and is counted for its state. Nothing but a short window of recent snapshots is kept in memory.
 */
class StateRecorder(
    private val clock: Clock,
    private val telemetryService: InternalTelemetryService? = null,
    private val globalChangeLimit: Int = GLOBAL_CHANGE_LIMIT,
    windowSize: Int = WINDOW_SIZE,
) {

    /**
     * Where the open session part's records go, or null when nothing keeps them, as in an app that sends nothing to Embrace.
     */
    @Volatile
    var journal: StateJournal? = null

    /**
     * What replays changes to a customer's exporter, or null when no exporter receives them.
     */
    @Volatile
    var replay: StateReplay? = null

    private val lock = Any()

    @Volatile
    private var snapshot: StateSnapshot = StateSnapshot.EMPTY

    /** The most recent snapshots, oldest overwritten first. Guarded by [lock]. */
    private val window = arrayOfNulls<StateSnapshot>(windowSize.coerceAtLeast(1))

    /** How many snapshots have ever been put in [window]. Guarded by [lock]. */
    private var recorded = 0L

    /** One entry for each declared state, in the order of the snapshot's states. Guarded by [lock]. */
    private var counters = emptyArray<Counters>()

    /** The session part in progress, if any. Guarded by [lock]. */
    private var part: OpenPart? = null

    /**
     * Every state's value now, with the number of the last change.
     */
    fun current(): StateSnapshot = snapshot

    /**
     * The snapshot that held at [timeMs] by the SDK's clock, for a span that is given its times. A time equal to the one a change was
     * recorded at counts as after the change.
     *
     * Returns [StateSnapshot.EMPTY] for a time before the first change, and null for a time older than the window of recent
     * snapshots, where what held is no longer known.
     */
    fun snapshotAt(timeMs: Long): StateSnapshot? {
        val latest = snapshot
        if (timeMs >= latest.recordedAtMs) {
            return latest
        }
        synchronized(lock) {
            val held = minOf(recorded, window.size.toLong()).toInt()
            for (age in 1..held) {
                val candidate = window[((recorded - age) % window.size).toInt()] ?: break
                if (timeMs >= candidate.recordedAtMs) {
                    return candidate
                }
            }
            return if (recorded > window.size) null else StateSnapshot.EMPTY
        }
    }

    /**
     * Registers the handlers through which data sources reach this recorder. This must happen before any data source is created,
     * as a declaration emitted earlier is not kept for it.
     */
    fun attach(eventBus: EventBus) {
        eventBus.addHandler(StateEvents.DECLARED) { declare(it.definition, it.initialValue, it.system, it.timeMs) }
        eventBus.addHandler(StateEvents.UPDATED) { update(it.state, it.value, it.system, it.timeMs, it.attributes) }
        eventBus.addHandler(StateEvents.DROPPED) { reportDropped(it.state, it.count) }
    }

    /**
     * Makes [definition] a state, with [initialValue] as its first value. A state's first value is a change like any other, so it
     * takes a number. Returns false if the name cannot name a state or is already declared.
     */
    fun declare(definition: StateDefinition, initialValue: String, system: Boolean, timeMs: Long): Boolean {
        if (!StateDefinition.isValidName(definition.name)) {
            return false
        }
        val value = cap(initialValue)
        synchronized(lock) {
            val before = snapshot
            if (before.indexOf(definition.name) >= 0) {
                return false
            }
            val after = before.plus(definition, value, system, before.seq + 1, clock.now())
            counters += Counters()
            publish(after)
            record(
                index = after.size - 1,
                change = StateChange(
                    seq = after.seq,
                    definition = definition,
                    timeMs = timeMs,
                    value = value,
                    system = system,
                    previousValue = null,
                    previousSystem = false,
                    attributes = emptyMap(),
                    after = after,
                ),
            )
        }
        return true
    }

    /**
     * Reports that the state called [state] has [value] as of [timeMs]. An update for a state no data source declared is
     * discarded, and one that leaves the value as it was is not a change and takes no number. Returns whether it was a change.
     *
     * [attributes] describe the value being left. Only the keys the state's data source declared are kept.
     */
    fun update(
        state: String,
        value: String,
        system: Boolean,
        timeMs: Long,
        attributes: Map<String, String> = emptyMap(),
    ): Boolean {
        val capped = cap(value)
        synchronized(lock) {
            val before = snapshot
            val index = before.indexOf(state)
            if (index < 0) {
                return false
            }
            val previousValue = before.value(index)
            val previousSystem = before.isSystem(index)
            if (previousValue == capped && previousSystem == system) {
                return false
            }
            val definition = before.definition(index)
            val after = before.with(index, capped, system, before.seq + 1, clock.now())
            publish(after)
            record(
                index = index,
                change = StateChange(
                    seq = after.seq,
                    definition = definition,
                    timeMs = timeMs,
                    value = capped,
                    system = system,
                    previousValue = previousValue,
                    previousSystem = previousSystem,
                    attributes = ownAttributes(definition, attributes),
                    after = after,
                ),
            )
        }
        return true
    }

    /**
     * Counts [count] changes of [state] that its instrumentation never passed on. They take no number, since when they happened is
     * not known, and are counted for the state on the change log of the open session part, or of the next one if none is open.
     */
    fun reportDropped(state: String, count: Int) {
        if (count <= 0) {
            return
        }
        synchronized(lock) {
            val index = snapshot.indexOf(state)
            if (index >= 0) {
                counters[index].droppedByInstrumentation += count
            }
        }
    }

    /**
     * A session part has started. Its change log opens with every state's value as it is now.
     */
    fun openPart(sessionPartId: String, startTimeMs: Long) {
        synchronized(lock) {
            if (part != null) {
                closePartLocked()
            }
            val opening = snapshot
            val states = ArrayList<OpeningState>(opening.size)
            for (i in 0 until opening.size) {
                val counts = counters[i]
                states.add(OpeningState(opening.name(i), opening.value(i), opening.isSystem(i), counts.notInSession))
                counts.notInSession = 0
            }
            part = OpenPart(sessionPartId)
            journal?.append(StateJournalRecord.PartOpened(sessionPartId, opening.seq, states))
            replay?.onPartOpened(opening, startTimeMs)
        }
    }

    /**
     * The session part in progress has ended. Changes made from now until the next part opens are counted and not kept.
     */
    fun closePart() {
        synchronized(lock) {
            closePartLocked()
        }
    }

    private fun closePartLocked() {
        val closing = part ?: return
        val closingValues = snapshot
        val states = ArrayList<ClosingState>()
        for (i in 0 until closingValues.size) {
            val counts = counters[i]
            val dropped = counts.refused + counts.droppedByInstrumentation
            if (dropped > 0) {
                val refused = counts.refused > 0
                states.add(
                    ClosingState(
                        name = closingValues.name(i),
                        droppedCount = dropped,
                        finalValue = if (refused) closingValues.value(i) else null,
                        finalSystem = refused && closingValues.isSystem(i),
                        finalSeq = if (refused) counts.lastSeq else 0L,
                    ),
                )
            }
            counts.endPart()
        }
        part = null
        journal?.append(StateJournalRecord.PartClosed(closing.sessionPartId, closing.refused, states))
    }

    /** Makes [after] the values everything reads, and keeps it in the window. Called with [lock] held. */
    private fun publish(after: StateSnapshot) {
        window[(recorded % window.size).toInt()] = after
        recorded++
        snapshot = after
    }

    /**
     * Lists [change] in the open session part's journal, or counts it: for its state when no part is open, and as refused when the
     * state's limit or the limit across every state has been reached. Called with [lock] held.
     */
    private fun record(index: Int, change: StateChange) {
        val counts = counters[index]
        val open = part
        if (open == null) {
            counts.notInSession++
            return
        }
        counts.lastSeq = change.seq
        val definition = change.definition
        when {
            counts.listed >= definition.changeLimit -> {
                if (!counts.limitStated) {
                    counts.limitStated = true
                    journal?.append(StateJournalRecord.LimitReached(open.sessionPartId, definition.name, definition.changeLimit))
                }
                refuse(open, counts, definition)
            }

            open.listed >= globalChangeLimit -> {
                if (!open.globalLimitStated) {
                    open.globalLimitStated = true
                    journal?.append(StateJournalRecord.LimitReached(open.sessionPartId, null, globalChangeLimit))
                }
                refuse(open, counts, definition)
            }

            else -> {
                counts.listed++
                open.listed++
                journal?.append(StateJournalRecord.Change(open.sessionPartId, change))
                replay?.onChange(change)
            }
        }
    }

    private fun refuse(open: OpenPart, counts: Counters, definition: StateDefinition) {
        counts.refused++
        open.refused++
        telemetryService?.trackAppliedLimit(definition.limitTelemetryName, AppliedLimitType.DROP)
    }

    private fun ownAttributes(definition: StateDefinition, attributes: Map<String, String>): Map<String, String> {
        if (attributes.isEmpty() || definition.attributeKeys.isEmpty()) {
            return emptyMap()
        }
        val kept = LinkedHashMap<String, String>()
        for (key in definition.attributeKeys) {
            val value = attributes[key] ?: continue
            kept[key] = cap(value)
        }
        return kept
    }

    private fun cap(value: String): String = PropertyUtils.truncate(value, MAX_VALUE_LENGTH)

    private class OpenPart(val sessionPartId: String) {

        /** Changes this part's journal lists, for every state together. */
        var listed = 0

        /** Changes made in this part that a limit refused, for every state together. */
        var refused = 0

        var globalLimitStated = false
    }

    private class Counters {

        /** Changes of the state that the open part's journal lists. */
        var listed = 0

        /** Changes of the state that a limit refused in the open part. */
        var refused = 0

        /** Changes its instrumentation reported it never passed on, not yet stated on a change log. */
        var droppedByInstrumentation = 0

        /** Changes of the state made since the last part closed. */
        var notInSession = 0

        /** Whether the open part's journal already states that the state's own limit was reached. */
        var limitStated = false

        /** The number of the state's last change in the open part, listed or not. */
        var lastSeq = 0L

        fun endPart() {
            listed = 0
            refused = 0
            droppedByInstrumentation = 0
            limitStated = false
            lastSeq = 0L
        }
    }

    companion object {

        /** How many changes a session part's change log lists for every state together, as a backstop beside each state's limit. */
        const val GLOBAL_CHANGE_LIMIT: Int = 10_000

        /** How many recent snapshots are kept to place a span that is given its times. */
        const val WINDOW_SIZE: Int = 64

        /**
         * The longest a state's value, or the value of a change's own attribute, may be. It is the SDK's limit on an internal
         * attribute's value. A longer one is cut to this length less three and `...`, without splitting a surrogate pair.
         */
        const val MAX_VALUE_LENGTH: Int = 2000
    }
}
