package io.embrace.android.embracesdk.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * What associating telemetry with [stateCount] states costs on the thread that makes the telemetry or reports a change: the
 * link on a span, the stamp on a log, and the event for a change, in each of the ways an app's own exporters can be given them.
 *
 * [association] is how the app state data model is switched and what the app registered, and [StateAssociationMode.NONE] is the
 * model off, which is the floor. Nothing is persisted, so these leave out what the journal costs, which [StateCaptureBenchmarks]
 * measures.
 */
@RunWith(Parameterized::class)
class StateAssociationBenchmarks(
    private val association: StateAssociationMode,
    private val stateCount: Int,
) {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private lateinit var fixture: StateAssociationFixture

    @Before
    fun setup() {
        fixture = StateAssociationFixture(stateCount = stateCount, mode = association)
    }

    @After
    fun tearDown() {
        fixture.close()
    }

    /**
     * Starting a span and ending it, when no state changed while it ran: with the second step on the span reads the number
     * twice and writes one. Where the values are exported, what an app's exporter is handed is wrapped as the span ends, and
     * the values are put on it when the exporter reads them, on its own thread.
     */
    @Test
    fun startAndEndSpan() {
        var spans = 0
        benchmarkRule.measureRepeated {
            fixture.startAndEndSpan()
            runWithMeasurementDisabled {
                if (++spans % SPAN_BATCH == 0) {
                    fixture.clearCompletedSpans()
                }
            }
        }
    }

    /**
     * Emitting one log through the real pipeline, which stamps Embrace's payload with the packed attribute and, where the
     * app has a log exporter, the record with one attribute for each state. Against [StateAssociationMode.NONE] it is what
     * each form costs a log; [StateAssociationMode.NO_APP_LOG_EXPORTER] never builds the plain one.
     */
    @Test
    fun recordLog() {
        var logs = 0
        benchmarkRule.measureRepeated {
            fixture.recordLog()
            runWithMeasurementDisabled {
                if (++logs % SPAN_BATCH == 0) {
                    fixture.clearStoredLogs()
                }
            }
        }
    }

    /**
     * Building what one log record is stamped with for an app's exporter, one attribute for each state, when no state has
     * changed since the last log. It is the same whatever an app's exporters are given, so it is measured once.
     */
    @Test
    fun stampLog() {
        assumeTrue(association == StateAssociationMode.FIRST_STEP && stateCount > 0)
        benchmarkRule.measureRepeated {
            fixture.stampLog()
        }
    }

    /**
     * The same as one string that holds every value, to compare [stampLog] with.
     */
    @Test
    fun stampLogPacked() {
        assumeTrue(association == StateAssociationMode.FIRST_STEP && stateCount > 0)
        benchmarkRule.measureRepeated {
            fixture.stampLogPacked()
        }
    }

    /**
     * [TRANSITION_BATCH] changes in the value of one state, on the thread that reports them. Where a change is replayed as
     * an event, all that thread does is queue it: the worker that emits events is held, and what it then emits is not
     * measured. Each batch is made in a session part of its own, as the recorder limits the changes of one.
     */
    @Test
    fun reportChanges() {
        assumeTrue(association != StateAssociationMode.NONE && stateCount > 0)
        benchmarkRule.measureRepeated {
            runWithMeasurementDisabled {
                fixture.startPart()
                fixture.holdEvents()
            }
            repeat(TRANSITION_BATCH) {
                fixture.transition(0)
            }
            runWithMeasurementDisabled {
                fixture.releaseEvents()
            }
        }
    }

    /**
     * [TRANSITION_BATCH] changes in the value of one state, and the event for each built, emitted and exported before the
     * measured stretch ends. Against [reportChanges], this is what the events themselves cost, on the worker that emits
     * them. The two threads overlap, so the allocation count is not the reporting thread's alone.
     */
    @Test
    fun reportChangesAndEmitEvents() {
        assumeTrue(association.replaysChanges && stateCount > 0)
        benchmarkRule.measureRepeated {
            runWithMeasurementDisabled {
                fixture.startPart()
                fixture.awaitEvents()
            }
            repeat(TRANSITION_BATCH) {
                fixture.transition(0)
            }
            fixture.awaitEvents()
        }
    }

    companion object {
        private const val SPAN_BATCH = 200
        private const val TRANSITION_BATCH = 50
        private val STATE_COUNTS = listOf(0, 1, 3, 10, StateCaptureFixture.MAX_STATE_COUNT)

        @JvmStatic
        @Parameterized.Parameters(name = "{0}-{1,number,#}-states")
        fun parameters(): List<Array<Any>> = StateAssociationMode.entries.flatMap { association ->
            // with the model off there are no states
            STATE_COUNTS.filter { association != StateAssociationMode.NONE || it == 0 }.map { count -> arrayOf(association, count) }
        }
    }
}
