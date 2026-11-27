package io.embrace.android.embracesdk.benchmark

import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.clock.NormalizedIntervalClock
import io.embrace.android.embracesdk.internal.logging.ErrorHandlerImpl
import io.embrace.android.embracesdk.internal.logging.androidLogcat
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSdkSpan
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartDirectory
import io.embrace.android.embracesdk.internal.session.persistence.SessionPartWriteTarget
import io.embrace.android.embracesdk.internal.session.persistence.SpanSnapshotsWriter
import io.embrace.android.embracesdk.internal.session.persistence.StateJournalWriter
import io.embrace.android.embracesdk.internal.state.OpeningState
import io.embrace.android.embracesdk.internal.state.StateChange
import io.embrace.android.embracesdk.internal.state.StateDefinition
import io.embrace.android.embracesdk.internal.state.StateJournal
import io.embrace.android.embracesdk.internal.state.StateJournalRecord
import io.embrace.android.embracesdk.internal.state.StateRecorder
import java.io.File

/**
 * The write that persists a state change once [changesBefore] have been made in the session part, which is work
 * multi-file persistence does on its worker.
 *
 * When [journaled], that is appending one record to the journal with a [StateJournalWriter], and is done for every change.
 * Otherwise it is taking a snapshot of the span the state is recorded into, which holds every change made so far, and writing
 * that snapshot again with a [SpanSnapshotsWriter]. That write is delayed by two seconds and one write covers every change made
 * meanwhile, so a burst of changes costs one of them, and changes more than two seconds apart cost one each.
 *
 * The journal's records are the ones a real [StateRecorder] makes, taken from it as they are made and written later, once for
 * each write that is measured. A journal accepts [MAX_JOURNAL_WRITES] writes after the changes it starts with, and [reset] starts
 * again.
 */
internal class StateChangePersistenceFixture(
    private val journaled: Boolean,
    private val changesBefore: Int,
    private val storageDir: File,
    private val clock: Clock = NormalizedIntervalClock(),
) : AutoCloseable {

    private val errorHandler = ErrorHandlerImpl(clock::now, ::androidLogcat)
    private var partNumber = 0
    private var directory = nextDirectory()
    private val target = SessionPartWriteTarget(lazy { storageDir }, clock) { directory }

    private val journal = StateJournalWriter(target, errorHandler)
    private val changes: List<StateChange>
    private var records: List<StateJournalRecord> = emptyList()
    private var nextRecord = 0

    private val snapshots = SpanSnapshotsWriter(target, errorHandler)
    private val stateCapture: StateCaptureFixture?
    private val stateSpan: EmbraceSdkSpan?

    init {
        if (journaled) {
            stateCapture = null
            stateSpan = null
            changes = recordChanges(changesBefore + MAX_JOURNAL_WRITES)
        } else {
            stateCapture = StateCaptureFixture(1, journaled = false, storageDir = File(storageDir, "capture"), clock = clock)
            repeat(changesBefore) { stateCapture.transition(0) }
            stateSpan = stateCapture.stateSpan(0)
            changes = emptyList()
        }
        reset()
    }

    /**
     * Persists one more change, and returns whether it was written.
     */
    fun persistOneChange(): Boolean = if (journaled) {
        journal.write(listOf(records[nextRecord++]))
    } else {
        val snapshot = checkNotNull(checkNotNull(stateSpan).snapshot())
        snapshots.append(listOf(snapshot)) { listOf(snapshot) }
    }

    /**
     * Starts again in an empty directory, with [changesBefore] changes already persisted there. A journal only grows, so
     * this is what keeps a long run from filling its file.
     */
    fun reset() {
        journal.close()
        snapshots.close()
        File(storageDir, directory.dirName).deleteRecursively()
        directory = nextDirectory()
        File(storageDir, directory.dirName).mkdirs()
        if (journaled) {
            val partId = directory.sessionPartId
            records = changes.map { StateJournalRecord.Change(partId, it) }
            val opened = StateJournalRecord.PartOpened(partId, 0L, listOf(OpeningState(STATE_NAME, INITIAL_VALUE, false, 0)))
            val seeded = listOf(opened) + records.subList(0, changesBefore)
            nextRecord = changesBefore
            check(journal.write(seeded)) { "the journal was not seeded" }
        }
    }

    /**
     * The bytes on disk of the file the changes are persisted in.
     */
    fun persistedBytes(): Long =
        File(File(storageDir, directory.dirName), if (journaled) "state_journal.pb" else "span_snapshots.pb").length()

    /**
     * How many changes the span holds. A span stops taking them at its own limit, so this can be fewer than were made.
     */
    fun changesHeldBySpan(): Int = stateSpan?.snapshot()?.events?.size ?: 0

    override fun close() {
        journal.close()
        snapshots.close()
        stateCapture?.close()
    }

    /**
     * Makes [count] changes of one state with a real recorder that has no limit on them, and returns them in order.
     */
    private fun recordChanges(count: Int): List<StateChange> {
        val made = ArrayList<StateChange>(count)
        val recorder = StateRecorder(
            clock = clock,
            globalChangeLimit = Int.MAX_VALUE,
            keepsWindow = false,
        )
        recorder.attach(
            StateJournal { record ->
                if (record is StateJournalRecord.Change) {
                    made.add(record.change)
                }
            },
        )
        check(recorder.declare(StateDefinition(STATE_NAME, changeLimit = Int.MAX_VALUE), INITIAL_VALUE, false, clock.now()))
        recorder.openPart(CHANGES_PART_ID, clock.now())
        repeat(count) { index ->
            check(recorder.update(STATE_NAME, STATE_VALUES[index % STATE_VALUES.size], false, clock.now()))
        }
        return made
    }

    private fun nextDirectory() = SessionPartDirectory(
        timestamp = clock.now(),
        uuid = "9d1e7c22-0f5b-4a63-8c2e-6b7d4f1a0e35",
        userSessionId = "4f3c9a7d0b8e5f2a4c116c9b1f2ec1d3",
        sessionPartId = "%032x".format(++partNumber),
    )

    companion object {
        /**
         * How many writes a journal takes after the changes it starts with before it has to be [reset].
         */
        const val MAX_JOURNAL_WRITES: Int = 5_000

        private const val STATE_NAME = "network"
        private const val INITIAL_VALUE = "state-value-initial"
        private const val CHANGES_PART_ID = "00000000000000000000000000000000"
        private val STATE_VALUES = listOf("state-value-a", "state-value-b")
    }
}
