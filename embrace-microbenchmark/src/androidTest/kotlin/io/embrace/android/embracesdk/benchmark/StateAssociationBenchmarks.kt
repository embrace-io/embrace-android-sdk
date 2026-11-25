package io.embrace.android.embracesdk.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import io.embrace.android.embracesdk.internal.config.resolved.StateExportMode
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * What associating telemetry with [stateCount] states costs on the thread that makes the telemetry or reports a change:
 * the link on a span, the stamp on a log, and the event for a change in each of the ways an app's own exporters can be
 * given it.
 *
 * [association] is what an app's exporters are given, or [StateAssociationMode.NONE] where telemetry is not associated
 * with state, which is the floor. Nothing is persisted, so these leave out what the journal costs, which
 * [StateCaptureBenchmarks] measures.
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
        fixture = StateAssociationFixture(
            stateCount = stateCount,
            mode = association.exportMode,
            appLogExporter = association.appLogExporter,
        )
    }

    @After
    fun tearDown() {
        fixture.close()
    }

    /**
     * Starting a span and ending it, when no state changed while it ran: the span reads the sequence twice and writes one
     * number. Where the values are exported, what an app's exporter is handed is wrapped as the span ends, and the values
     * are put on it when the exporter reads them, on its own thread.
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
     * Building what one log record is stamped with for an app's exporter, one attribute for each state, when no state has changed since the
     * last log. It is the same whatever an app's exporters are given, so it is measured once.
     */
    @Test
    fun stampLog() {
        assumeTrue(association == StateAssociationMode.OFF && stateCount > 0)
        benchmarkRule.measureRepeated {
            fixture.stampLog()
        }
    }

    /**
     * The same as one string that holds every value, to compare [stampLog] with.
     */
    @Test
    fun stampLogPacked() {
        assumeTrue(association == StateAssociationMode.NONE && stateCount > 0)
        benchmarkRule.measureRepeated {
            fixture.stampLogPacked()
        }
    }

    /**
     * [TRANSITION_BATCH] changes in the value of one state, on the thread that reports them. Where a change is exported
     * as an event, all that thread does is queue it: the worker that emits events is held, and what it then emits is not
     * measured.
     */
    @Test
    fun reportChanges() {
        assumeTrue(stateCount > 0)
        benchmarkRule.measureRepeated {
            runWithMeasurementDisabled {
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
        assumeTrue(stateCount > 0 && association.exportMode in EVENT_MODES)
        benchmarkRule.measureRepeated {
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
        private val EVENT_MODES = listOf(StateExportMode.ASSOCIATION, StateExportMode.VERBOSE)

        @JvmStatic
        @Parameterized.Parameters(name = "{0}-{1,number,#}-states")
        fun parameters(): List<Array<Any>> = StateAssociationMode.entries.flatMap { association ->
            STATE_COUNTS.map { count -> arrayOf(association, count) }
        }
    }
}

/**
 * How telemetry is associated with state in a benchmark: not at all, or by a link on a span and a stamp on a log, with
 * what an app's own exporters are given beyond that.
 */
enum class StateAssociationMode(internal val exportMode: StateExportMode?, internal val appLogExporter: Boolean = true) {
    NONE(null),
    NO_APP_LOG_EXPORTER(StateExportMode.ASSOCIATION, appLogExporter = false),
    OFF(StateExportMode.OFF),
    ASSOCIATION(StateExportMode.ASSOCIATION),
    VERBOSE(StateExportMode.VERBOSE),
}
