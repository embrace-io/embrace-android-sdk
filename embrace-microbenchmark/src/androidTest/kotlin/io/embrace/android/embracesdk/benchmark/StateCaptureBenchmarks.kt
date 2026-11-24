package io.embrace.android.embracesdk.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/**
 * What capturing [stateCount] states costs on the thread that reports them, when each is recorded into a span of its own
 * against when they are all recorded into the journal of state changes.
 *
 * Every benchmark runs with a session part's telemetry persisted as multiple files, by a worker of its own that is not
 * measured. With no states, every benchmark that needs none measures the floor: what the session part alone costs.
 */
@RunWith(Parameterized::class)
class StateCaptureBenchmarks(
    private val recording: StateRecording,
    private val stateCount: Int,
) {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private lateinit var storageDir: File
    private lateinit var fixture: StateCaptureFixture

    @Before
    fun setup() {
        storageDir = benchmarkStorageDir()
        fixture = StateCaptureFixture(
            stateCount = stateCount,
            journaled = recording == StateRecording.JOURNAL,
            storageDir = storageDir,
        )
    }

    @After
    fun tearDown() {
        fixture.close()
        storageDir.deleteRecursively()
    }

    /**
     * A session part change when no state changed value during the part, which is what most part changes are.
     */
    @Test
    fun changeSessionPart() {
        benchmarkRule.measureRepeated {
            runWithMeasurementDisabled {
                fixture.holdWrites()
            }
            fixture.changeSessionPart()
            runWithMeasurementDisabled {
                fixture.releaseWrites()
            }
        }
    }

    /**
     * A session part change after every state changed value [TRANSITIONS_PER_STATE] times during the part.
     */
    @Test
    fun changeSessionPartAfterTransitions() {
        benchmarkRule.measureRepeated {
            runWithMeasurementDisabled {
                fixture.transitionEveryState(TRANSITIONS_PER_STATE)
                fixture.holdWrites()
            }
            fixture.changeSessionPart()
            runWithMeasurementDisabled {
                fixture.releaseWrites()
            }
        }
    }

    /**
     * [TRANSITION_BATCH] changes in the value of one state, at the start of a session part.
     */
    @Test
    fun transitions() {
        assumeTrue(stateCount > 0)
        benchmarkRule.measureRepeated {
            runWithMeasurementDisabled {
                fixture.holdWrites()
            }
            repeat(TRANSITION_BATCH) {
                fixture.transition(0)
            }
            runWithMeasurementDisabled {
                fixture.releaseWrites()
                fixture.changeSessionPart()
            }
        }
    }

    /**
     * Building what one log record is stamped with to say what value every state had, when no state has changed value
     * since the last log.
     */
    @Test
    fun stampLog() {
        assumeTrue(stateCount > 0)
        fixture.transitionEveryState(1)
        fixture.holdWrites()
        benchmarkRule.measureRepeated {
            fixture.stampLog()
        }
        fixture.releaseWrites()
    }

    /**
     * One change in the value of one state while the persistence worker is idle and free to run, which is how a change
     * usually finds it. Handing the journal a record then wakes the worker, a cost [transitions] leaves out by holding the
     * worker. The worker's own work overlaps the measured stretch here, so the allocation count is not the reporting
     * thread's alone.
     */
    @Test
    fun transitionWithIdleWorker() {
        assumeTrue(stateCount > 0)
        var changes = 0
        benchmarkRule.measureRepeated {
            runWithMeasurementDisabled {
                if (++changes % TRANSITION_BATCH == 0) {
                    fixture.changeSessionPart()
                }
                fixture.awaitWrites()
            }
            fixture.transition(0)
        }
    }

    /**
     * [TRANSITION_BATCH] times: one change in the value of one state, then building what a log record is stamped with.
     * Against [transitions], this is what the first log after a change costs.
     */
    @Test
    fun stampLogAfterChange() {
        assumeTrue(stateCount > 0)
        benchmarkRule.measureRepeated {
            runWithMeasurementDisabled {
                fixture.holdWrites()
            }
            repeat(TRANSITION_BATCH) {
                fixture.transition(0)
                fixture.stampLog()
            }
            runWithMeasurementDisabled {
                fixture.releaseWrites()
                fixture.changeSessionPart()
            }
        }
    }

    /**
     * Reading back from disk a session part in which every state changed value [TRANSITIONS_PER_STATE] times, as the
     * envelope to deliver for it. This is where the span of journaled states is made, so it is work a span for each state
     * does when the part changes and the journal leaves until the part is delivered.
     */
    @Test
    fun readSessionPart() {
        StateCaptureFixture(
            stateCount = stateCount,
            journaled = recording == StateRecording.JOURNAL,
            storageDir = File(storageDir, "kept"),
            keepParts = true,
        ).use { kept ->
            kept.changeSessionPart()
            kept.transitionEveryState(TRANSITIONS_PER_STATE)
            val directory = kept.partDirectory(kept.changeSessionPart())
            kept.holdWrites()
            benchmarkRule.measureRepeated {
                kept.readPart(directory)
            }
            kept.releaseWrites()
        }
    }

    companion object {
        private const val TRANSITIONS_PER_STATE = 5
        private const val TRANSITION_BATCH = 50
        private val STATE_COUNTS = listOf(1, 3, 10, StateCaptureFixture.MAX_STATE_COUNT)

        @JvmStatic
        @Parameterized.Parameters(name = "{0}-{1,number,#}-states")
        fun parameters(): List<Array<Any>> =
            // with no states the two are the same, so the floor is measured once
            listOf(arrayOf<Any>(StateRecording.PER_STATE_SPAN, 0)) + StateRecording.entries.flatMap { recording ->
                STATE_COUNTS.map { count -> arrayOf(recording, count) }
            }
    }
}

/**
 * How state changes are recorded: into a span for each state, or into the journal of state changes.
 */
enum class StateRecording { PER_STATE_SPAN, JOURNAL }

/**
 * A directory of the app's own for one benchmark to persist session parts in.
 */
internal fun benchmarkStorageDir(): File =
    File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "state-capture-${System.nanoTime()}")
