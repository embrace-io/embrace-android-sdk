package io.embrace.android.embracesdk.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import io.embrace.android.embracesdk.benchmark.NaiveOtelStateFixture.ChangeEventContent
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/**
 * What recording [stateCount] states costs on the thread that makes the telemetry, when they are recorded the way
 * OpenTelemetry's Android SDK records them: a stamp on every span and log, and a log for every change.
 *
 * Every benchmark runs with a session part's telemetry persisted as multiple files, by a worker of its own that is not
 * measured. With no states, every benchmark that needs none measures the floor: what the same spans and logs cost alone.
 * [StateCaptureBenchmarks] measures a span for each state and the journal of state changes the same way.
 */
@RunWith(Parameterized::class)
class NaiveOtelStateBenchmarks(
    private val stateCount: Int,
) {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private lateinit var storageDir: File
    private lateinit var fixture: NaiveOtelStateFixture

    @Before
    fun setup() {
        storageDir = benchmarkStorageDir()
        fixture = NaiveOtelStateFixture(
            stateCount = stateCount,
            storageDir = storageDir,
            changeEventContent = ChangeEventContent.EVERY_VALUE,
        )
    }

    @After
    fun tearDown() {
        fixture.close()
        storageDir.deleteRecursively()
    }

    /**
     * A session part change, which no state takes part in when states are recorded this way.
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
     * [SPAN_BATCH] ordinary spans started and stopped, each stamped as it starts with the value of every state.
     */
    @Test
    fun recordSpans() {
        benchmarkRule.measureRepeated {
            runWithMeasurementDisabled {
                fixture.holdWrites()
            }
            repeat(SPAN_BATCH) {
                fixture.recordSpan()
            }
            runWithMeasurementDisabled {
                fixture.releaseWrites()
                fixture.changeSessionPart()
            }
        }
    }

    /**
     * [LOG_BATCH] ordinary logs emitted, each stamped as it is emitted with the value of every state.
     */
    @Test
    fun recordLogs() {
        benchmarkRule.measureRepeated {
            repeat(LOG_BATCH) {
                fixture.recordLog()
            }
            runWithMeasurementDisabled {
                fixture.drainLogs()
            }
        }
    }

    /**
     * [TRANSITION_BATCH] changes in the value of one state, each emitting a log that carries the value of every state, as
     * a change event does when it passes through what stamps every other log.
     */
    @Test
    fun transitions() {
        assumeTrue(stateCount > 0)
        benchmarkRule.measureRepeated {
            repeat(TRANSITION_BATCH) {
                fixture.transition(0)
            }
            runWithMeasurementDisabled {
                fixture.drainLogs()
            }
        }
    }

    /**
     * [TRANSITION_BATCH] changes in the value of one state, each emitting a log that says only which state changed and to
     * what. Against [transitions], the difference is what carrying every value costs.
     */
    @Test
    fun transitionsNewValueOnly() {
        assumeTrue(stateCount > 0)
        NaiveOtelStateFixture(
            stateCount = stateCount,
            storageDir = File(storageDir, "new-value-only"),
            changeEventContent = ChangeEventContent.NEW_VALUE_ONLY,
        ).use { bare ->
            benchmarkRule.measureRepeated {
                repeat(TRANSITION_BATCH) {
                    bare.transition(0)
                }
                runWithMeasurementDisabled {
                    bare.drainLogs()
                }
            }
        }
    }

    /**
     * Building what one log record is stamped with to say what value every state had.
     */
    @Test
    fun stampLog() {
        assumeTrue(stateCount > 0)
        benchmarkRule.measureRepeated {
            fixture.stamp()
        }
    }

    companion object {
        private const val SPAN_BATCH = 50
        private const val LOG_BATCH = 50
        private const val TRANSITION_BATCH = 50
        private val STATE_COUNTS = listOf(0, 1, 3, 10, StateCaptureFixture.MAX_STATE_COUNT)

        @JvmStatic
        @Parameterized.Parameters(name = "{0,number,#}-states")
        fun parameters(): List<Array<Any>> = STATE_COUNTS.map { arrayOf<Any>(it) }
    }
}
