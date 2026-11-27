package io.embrace.android.embracesdk.internal.state

/**
 * Where the recorder sends what a session part's change log is later made from. Records arrive in the order of the sequence, on
 * whichever thread reported the change and while the recorder holds its lock, so an implementation only queues them: it must not
 * block, do I/O or call back into the recorder.
 */
fun interface StateJournal {
    fun append(record: StateJournalRecord)
}

/**
 * One record of a session part's journal. Each names the session part it belongs to, so that a record queued for one part cannot
 * land in another's file.
 */
sealed class StateJournalRecord(val sessionPartId: String) {

    /**
     * A session part started. [states] is every state's value as of [initialSeq], the number of the last change made before the
     * part, with how many changes of each were made with no session part since the part before ended.
     */
    class PartOpened(
        sessionPartId: String,
        val initialSeq: Long,
        val states: List<OpeningState>,
    ) : StateJournalRecord(sessionPartId)

    /**
     * A change the part's change log lists.
     */
    class Change(
        sessionPartId: String,
        val change: StateChange,
    ) : StateJournalRecord(sessionPartId)

    /**
     * A limit refused a change for the first time in this part: the limit of the state called [state], or the limit across every
     * state when [state] is null. It is recorded when it happens so that a journal with no [PartClosed] still states it.
     */
    class LimitReached(
        sessionPartId: String,
        val state: String?,
        val limit: Int,
    ) : StateJournalRecord(sessionPartId)

    /**
     * The session part ended. [droppedChanges] is how many changes took a number during the part and are not listed, and [states]
     * has an entry for each state the journal does not list every change of.
     */
    class PartClosed(
        sessionPartId: String,
        val droppedChanges: Int,
        val states: List<ClosingState>,
    ) : StateJournalRecord(sessionPartId)
}

/**
 * A state's value as a session part's change log opens.
 */
class OpeningState(
    val name: String,
    val value: String,
    val system: Boolean,
    val notInSessionCount: Int,
)

/**
 * What a session part's end says of a state whose changes its journal does not all list. [droppedCount] counts the changes a limit
 * refused and those its instrumentation reported it never passed on. [finalValue] and [finalSeq] are set only when a limit refused
 * a change, since the list then no longer leads to the state's value.
 */
class ClosingState(
    val name: String,
    val droppedCount: Int,
    val finalValue: String?,
    val finalSystem: Boolean,
    val finalSeq: Long,
)

/**
 * One change of one state, with its place in the sequence.
 */
class StateChange(

    /** The change's number. */
    val seq: Long,

    val definition: StateDefinition,

    /** When the change happened, as whatever reported it gave it. */
    val timeMs: Long,

    val value: String,
    val system: Boolean,

    /** The value the state had before, or null if this is the state's first value. */
    val previousValue: String?,
    val previousSystem: Boolean,

    /** The change's own attributes, which describe the value being left: only the keys the state's data source declared. */
    val attributes: Map<String, String>,

    /** Every state's value once this change was made. */
    val after: StateSnapshot,
)

/**
 * What the recorder tells whatever replays state to a customer's exporter. As with [StateJournal], calls arrive in order under the
 * recorder's lock, so an implementation only queues.
 */
interface StateReplay {

    /** A session part started at [timeMs] with [snapshot] in force. */
    fun onPartOpened(snapshot: StateSnapshot, timeMs: Long)

    /** A change was made in a session part and no limit refused it. */
    fun onChange(change: StateChange)
}
