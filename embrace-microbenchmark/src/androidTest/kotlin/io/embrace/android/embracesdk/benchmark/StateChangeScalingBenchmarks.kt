package io.embrace.android.embracesdk.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/**
 * Whether a state change costs more once many have been made: one more change after [changesBefore] in the session part,
 * recorded into the state's own span against recorded into the journal of state changes.
 *
 * A span stops taking changes at 2,000, so it is not measured past that.
 */
@RunWith(Parameterized::class)
class StateChangeScalingBenchmarks(
    private val recording: StateRecording,
    private val changesBefore: Int,
) {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private lateinit var storageDir: File
    private val journaled = recording == StateRecording.JOURNAL

    @Before
    fun setup() {
        storageDir = benchmarkStorageDir()
    }

    @After
    fun tearDown() {
        storageDir.deleteRecursively()
    }

    /**
     * [CHANGE_BATCH] changes in the value of one state after [changesBefore] were made in the session part, on the thread
     * that reports them.
     */
    @Test
    fun recordChanges() {
        StateCaptureFixture(stateCount = 1, journaled = journaled, storageDir = storageDir).use { fixture ->
            benchmarkRule.measureRepeated {
                runWithMeasurementDisabled {
                    fixture.changeSessionPart()
                    repeat(changesBefore) { fixture.transition(0) }
                    fixture.holdWrites()
                }
                repeat(CHANGE_BATCH) {
                    fixture.transition(0)
                }
                runWithMeasurementDisabled {
                    fixture.releaseWrites()
                }
            }
        }
    }

    /**
     * One write to persist a change after [changesBefore] were made in the session part, which multi-file persistence
     * does on its worker. For the journal that is one record appended, made for every change. For a span it is a snapshot
     * of the whole span written again, which is made once for all the changes of two seconds, not once for each.
     */
    @Test
    fun persistChange() {
        StateChangePersistenceFixture(journaled, changesBefore, storageDir).use { fixture ->
            var writes = 0
            benchmarkRule.measureRepeated {
                if (writes == WRITES_BEFORE_RESET) {
                    runWithMeasurementDisabled {
                        fixture.reset()
                        writes = 0
                    }
                }
                check(fixture.persistOneChange()) { "the change was not persisted" }
                writes++
            }
        }
    }

    companion object {
        private const val CHANGE_BATCH = 100
        private const val WRITES_BEFORE_RESET = 5_000

        // a span holds at most 2,000 changes, and CHANGE_BATCH more must fit after these
        private val SPAN_CHANGES_BEFORE = listOf(50, 500, 1_500)
        private val JOURNAL_CHANGES_BEFORE = SPAN_CHANGES_BEFORE + 5_000

        @JvmStatic
        @Parameterized.Parameters(name = "{0}-after-{1,number,#}-changes")
        fun parameters(): List<Array<Any>> =
            SPAN_CHANGES_BEFORE.map { arrayOf<Any>(StateRecording.PER_STATE_SPAN, it) } +
                JOURNAL_CHANGES_BEFORE.map { arrayOf<Any>(StateRecording.JOURNAL, it) }
    }
}
