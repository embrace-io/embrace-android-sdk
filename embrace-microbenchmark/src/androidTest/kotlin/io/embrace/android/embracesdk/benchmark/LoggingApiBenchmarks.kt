package io.embrace.android.embracesdk.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.internal.arch.datasource.LogSeverity
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.arch.schema.TelemetryAttributes
import io.embrace.android.embracesdk.internal.otel.logs.LogSink
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoggingApiBenchmarks: RobolectricTest() {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private val logMessages = (1..TOTAL_LOG_COUNT).map { "a really cool log message but it's not structured so it's useless lol $it" }
    private val logAttributes = (1..ATTRIBUTES_PER_LOG).associate { Pair("test-key-$it", "test-long-ish-values-$it") }

    private lateinit var telemetryDestination: TelemetryDestination
    private lateinit var logSink: LogSink

    @Before
    fun setup() {
        val harness = TelemetryDestinationHarness()
        telemetryDestination = harness.destination
        logSink = harness.logSink
    }

    @Test
    fun createLogs() {
        benchmarkRule.measureRepeated {
            logMessages.forEach {
                emitLog(message = it)
            }
            verifyAndCleanup()
        }
    }

    @Test
    fun createLogsWithAttributes() {
        benchmarkRule.measureRepeated {
            logMessages.forEach {
                emitLog(message = it, attributes = true)
            }
            verifyAndCleanup()
        }
    }

    private fun emitLog(
        message: String,
        attributes: Boolean = false,
    ) {
        telemetryDestination.addLog(
            schemaType = SchemaType.Log(
                TelemetryAttributes(
                    customAttributes = if (attributes) {
                        logAttributes
                    } else {
                        null
                    }
                )
            ),
            severity = LogSeverity.INFO,
            message = message,
            addCurrentSessionInfo = false
        )
    }

    private fun BenchmarkRule.Scope.verifyAndCleanup() {
        runWithMeasurementDisabled {
            assertEquals(TOTAL_LOG_COUNT, logSink.logsForNextBatch().size)
            logSink.flushBatch()
        }
    }


    companion object {
        private const val TOTAL_LOG_COUNT = 10
        private const val ATTRIBUTES_PER_LOG = 20
    }
}
