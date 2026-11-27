package io.embrace.android.embracesdk.internal.state

import io.embrace.android.embracesdk.internal.arch.SessionPartChangeListener
import io.embrace.android.embracesdk.internal.arch.SessionPartEndListener
import io.embrace.android.embracesdk.internal.arch.schema.StateKeys
import io.embrace.android.embracesdk.internal.arch.state.GLOBAL_STATE_CHANGE_LIMIT
import io.embrace.android.embracesdk.internal.arch.state.MAX_STATE_CHANGE_ATTRIBUTES
import io.embrace.android.embracesdk.internal.arch.state.StateDefinition
import io.embrace.android.embracesdk.internal.arch.state.StateRecorder
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.session.persistence.StateChangeRecord
import io.embrace.android.embracesdk.internal.session.persistence.StateCountRecord
import io.embrace.android.embracesdk.internal.session.persistence.StateFinalRecord
import io.embrace.android.embracesdk.internal.session.persistence.StateJournalRecord
import io.embrace.android.embracesdk.internal.session.persistence.StateLimitReached
import io.embrace.android.embracesdk.internal.session.persistence.StatePartClosed
import io.embrace.android.embracesdk.internal.session.persistence.StatePartOpened
import io.embrace.android.embracesdk.internal.session.persistence.StateValueRecord
import io.embrace.android.embracesdk.internal.utils.PropertyUtils

/**
 * Where the journal records of a session part go: appended to the part's journal on disk, without waiting on the
 * write.
 */
fun interface StateJournalSink {
    fun append(sessionPartId: String, record: StateJournalRecord)
}

/**
 * What a customer's exporters are sent of the state as it changes: a snapshot when a session part starts, and each
 * change made in a session part that a limit did not refuse.
 */
interface StateReplay {
    fun onPartStarted(snapshot: StateSnapshot, timeMs: Long)

    fun onChange(change: StateChangeRecord, previous: StateSnapshot, after: StateSnapshot)
}

/**
 * The one recorder of every state. It gives each change of any state one number in one sequence, replaces every
 * state's value together with the sequence, keeps a short window of recent sets of values, and decides once whether a
 * change is listed in the session part's change log, refused by a limit, or made with no session part.
 *
 * Every decision is taken under one lock, which is held only to update memory: a listed change is handed to the
 * [journal] and the [replay], which queue it, so reporting a change does no I/O and costs the same however many came
 * before. The past is not kept in memory beyond the window: the change log is rendered from the journal on disk.
 */
internal class StateRecorderImpl(
    private val clock: Clock,
    private val sessionPartIdProvider: () -> String?,
    private val journal: StateJournalSink,
    private val replay: StateReplay? = null,
    private val globalChangeLimit: Int = GLOBAL_STATE_CHANGE_LIMIT,
    private val windowSize: Int = DEFAULT_WINDOW_SIZE,
) : StateRecorder, SessionPartEndListener, SessionPartChangeListener {

    private class Slot(val definition: StateDefinition) {
        var listedInPart = 0
        var droppedInPart = 0L
        var notInSession = 0L
        var limitReported = false
        var droppedWithNoPart = 0L
        var refusedInPart = false
        var lastSeq = 0L
    }

    private val lock = Any()
    private val slots = LinkedHashMap<String, Slot>()

    @Volatile
    private var snapshot: StateSnapshot = StateSnapshot.EMPTY

    private val window = arrayOfNulls<StateSnapshot>(windowSize)
    private var windowNext = 0
    private var windowEvicted = false

    private var seq = 0L
    private var partId: String? = null
    private var partListed = 0
    private var partDropped = 0L
    private var globalLimitReported = false

    override fun current(): StateSnapshot = snapshot

    override fun snapshotAt(timeMs: Long): StateSnapshot? {
        val latest = snapshot
        if (latest.recordedAtMs <= timeMs) {
            return latest
        }
        synchronized(lock) {
            for (offset in 1..windowSize) {
                val entry = window[(windowNext - offset + windowSize) % windowSize] ?: break
                if (entry.recordedAtMs <= timeMs) {
                    return entry
                }
            }
            return if (windowEvicted) null else StateSnapshot.EMPTY
        }
    }

    override fun isDefined(name: String): Boolean = synchronized(lock) { slots.containsKey(name) }

    override fun define(definition: StateDefinition, initialValue: String, isSystem: Boolean, timeMs: Long) {
        synchronized(lock) {
            if (slots.containsKey(definition.name)) {
                return
            }
            slots[definition.name] = Slot(definition)
            applyChange(definition.name, initialValue, isSystem, timeMs) { emptyMap() }
        }
    }

    override fun record(name: String, value: String, isSystem: Boolean, timeMs: Long, attributes: () -> Map<String, String>) {
        synchronized(lock) {
            if (!slots.containsKey(name)) {
                return
            }
            applyChange(name, value, isSystem, timeMs, attributes)
        }
    }

    override fun reportDropped(name: String, count: Int) {
        if (count <= 0) {
            return
        }
        synchronized(lock) {
            val slot = slots[name] ?: return
            // a change an instrumentation dropped takes no number, since nothing says where it fell in the order. A count
            // reported with no part in progress goes in the next part's log
            if (partId != null) {
                slot.droppedInPart += count
            } else {
                slot.droppedWithNoPart += count
            }
        }
    }

    /**
     * Writes the closing record of the part that is ending, while the part's journal is still the one being written.
     */
    override fun onPreSessionEnd() {
        synchronized(lock) {
            val endingPart = partId ?: return
            val dropped = slots.values.filter { it.droppedInPart > 0 }
            journal.append(
                endingPart,
                StateJournalRecord(
                    closed = StatePartClosed(
                        dropped_changes = partDropped,
                        dropped_counts = dropped.map { StateCountRecord(it.definition.name, it.droppedInPart) },
                        finals = slots.values.filter { it.refusedInPart }.map { slot ->
                            val index = snapshot.indexOf(slot.definition.name)
                            StateFinalRecord(
                                name = slot.definition.name,
                                state_value = snapshot.value(index),
                                system = snapshot.isSystem(index),
                                seq = slot.lastSeq,
                            )
                        },
                    ),
                ),
            )
            partId = null
            partListed = 0
            partDropped = 0
            globalLimitReported = false
            slots.values.forEach {
                it.listedInPart = 0
                it.droppedInPart = 0
                it.limitReported = false
                it.refusedInPart = false
            }
        }
    }

    /**
     * Opens the change log of the part that has just started, if one did, with every state's value and the counts of
     * the changes made since the part before ended.
     */
    override fun onPostSessionChange() {
        synchronized(lock) {
            val newPart = sessionPartIdProvider() ?: return
            if (newPart == partId) {
                return
            }
            partId = newPart
            val current = snapshot
            journal.append(
                newPart,
                StateJournalRecord(
                    opened = StatePartOpened(
                        initial_seq = seq,
                        values = (0 until current.size).map {
                            StateValueRecord(current.name(it), current.value(it), current.isSystem(it))
                        },
                        not_in_session_counts = slots.values.filter { it.notInSession > 0 }.map {
                            StateCountRecord(it.definition.name, it.notInSession)
                        },
                    ),
                ),
            )
            slots.values.forEach {
                it.notInSession = 0
                it.droppedInPart = it.droppedWithNoPart
                it.droppedWithNoPart = 0
            }
            replay?.onPartStarted(current, clock.now())
        }
    }

    /**
     * Must be called holding [lock], for a state that is defined.
     */
    private fun applyChange(
        name: String,
        rawValue: String,
        isSystem: Boolean,
        timeMs: Long,
        attributes: () -> Map<String, String>,
    ) {
        val slot = slots[name] ?: return
        val value = PropertyUtils.truncate(rawValue, MAX_STATE_VALUE_LENGTH)
        val previous = snapshot
        val index = previous.indexOf(name)
        if (index >= 0 && previous.value(index) == value && previous.isSystem(index) == isSystem) {
            return
        }
        // collected for every change, listed or not, since collecting resets what the source has accrued
        val ownAttributes = attributes()
        seq++
        val after = previous.with(seq, clock.now(), name, value, isSystem)
        snapshot = after
        if (window[windowNext] != null) {
            windowEvicted = true
        }
        window[windowNext] = after
        windowNext = (windowNext + 1) % windowSize
        slot.lastSeq = seq

        val part = partId
        when {
            part == null -> slot.notInSession++
            slot.listedInPart >= slot.definition.changeLimit -> {
                refuse(slot)
                if (!slot.limitReported) {
                    slot.limitReported = true
                    journal.append(part, StateJournalRecord(limit = StateLimitReached(name, slot.definition.changeLimit)))
                }
            }
            partListed >= globalChangeLimit -> {
                refuse(slot)
                if (!globalLimitReported) {
                    globalLimitReported = true
                    journal.append(part, StateJournalRecord(limit = StateLimitReached("", globalChangeLimit)))
                }
            }
            else -> {
                slot.listedInPart++
                partListed++
                val change = StateChangeRecord(
                    seq = seq,
                    name = name,
                    state_value = value,
                    system = isSystem,
                    time_ms = timeMs,
                    attributes = ownAttributes.declaredBy(slot.definition),
                )
                journal.append(part, StateJournalRecord(change = change))
                replay?.onChange(change, previous, after)
            }
        }
    }

    private fun refuse(slot: Slot) {
        slot.droppedInPart++
        slot.refusedInPart = true
        partDropped++
    }

    /**
     * Only the keys the state's data source declared, at most seven, none the SDK uses, and each value capped.
     */
    private fun Map<String, String>.declaredBy(definition: StateDefinition): Map<String, String> {
        if (isEmpty() || definition.attributeKeys.isEmpty()) {
            return emptyMap()
        }
        val kept = LinkedHashMap<String, String>()
        for ((key, value) in this) {
            if (kept.size >= MAX_STATE_CHANGE_ATTRIBUTES) {
                break
            }
            if (key in definition.attributeKeys && !StateKeys.isReserved(key)) {
                kept[key] = PropertyUtils.truncate(value, MAX_STATE_VALUE_LENGTH)
            }
        }
        return kept
    }

    internal companion object {
        const val DEFAULT_WINDOW_SIZE: Int = 64
        const val MAX_STATE_VALUE_LENGTH: Int = 2000
    }
}
