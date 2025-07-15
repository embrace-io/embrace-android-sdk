package io.embrace.android.embracesdk.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.arch.destination.LogWriter
import io.embrace.android.embracesdk.internal.arch.destination.LogWriterImpl
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.arch.schema.TelemetryAttributes
import io.embrace.android.embracesdk.internal.clock.NormalizedIntervalClock
import io.embrace.android.embracesdk.internal.otel.config.OtelSdkConfig
import io.embrace.android.embracesdk.internal.otel.impl.EmbClock
import io.embrace.android.embracesdk.internal.otel.logs.LogSink
import io.embrace.android.embracesdk.internal.otel.logs.LogSinkImpl
import io.embrace.android.embracesdk.internal.otel.sdk.OtelSdkWrapper
import io.embrace.android.embracesdk.internal.otel.spans.SpanSinkImpl
import io.embrace.android.embracesdk.internal.session.id.SessionData
import io.embrace.android.embracesdk.internal.session.id.SessionIdTracker
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessStateListener
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessStateService
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@Suppress("OPT_IN_USAGE")
@RunWith(AndroidJUnit4::class)
class LoggingApiBenchmarks {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private val logMessages = (1..TOTAL_LOG_COUNT).map { "a really cool log message but it's not structured so it's useless lol $it" }
    private val logAttributes = (1..ATTRIBUTES_PER_LOG).associate { Pair("test-key-$it", "test-long-ish-values-$it") }

    private lateinit var logSink: LogSink
    private lateinit var logWriter: LogWriter

    @Before
    fun setup() {
        logSink = LogSinkImpl()
        val clock = NormalizedIntervalClock()
        val otelSdkWrapper = OtelSdkWrapper(
            otelClock = EmbClock(clock),
            configuration = OtelSdkConfig(
                spanSink = SpanSinkImpl(),
                logSink = logSink,
                sdkName = "benchmark-test-sdk",
                sdkVersion = "1.0",
                systemInfo = SystemInfo(),
                sessionIdProvider = { "fake-session-id" },
                processIdentifierProvider = { "fake-pid" }
            )
        )
        logWriter = LogWriterImpl(
            logger = otelSdkWrapper.logger,
            sessionIdTracker = NoopSessionIdTracker(),
            processStateService = NoopProcessStateService(),
            clock = clock,
        )
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
        attributes: Boolean = false
    ) {
        logWriter.addLog(
            schemaType = SchemaType.Log(
                TelemetryAttributes(
                    customAttributes = if (attributes) {
                        logAttributes
                    } else {
                        null
                    }
                )
            ),
            severity = Severity.INFO,
            message = message,
            addCurrentSessionInfo = false
        )
    }

    private fun BenchmarkRule.Scope.verifyAndCleanup() {
        runWithTimingDisabled {
            assertEquals(TOTAL_LOG_COUNT, logSink.logsForNextBatch().size)
            logSink.flushBatch()
        }
    }

    private class NoopSessionIdTracker : SessionIdTracker {
        override fun getActiveSession(): SessionData? = null

        override fun setActiveSession(sessionId: String?, isSession: Boolean) {
        }

        override fun addListener(listener: (String?) -> Unit) {
        }
    }

    private class NoopProcessStateService : ProcessStateService {
        override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        }

        override val isInBackground: Boolean = false

        override fun addListener(listener: ProcessStateListener) {
        }

        override fun onForeground() {
        }

        override fun onBackground() {
        }

        override fun getAppState(): String = "fake-state"

        override fun isInitialized(): Boolean = true

        override fun close() {
        }
    }

    companion object {
        private const val TOTAL_LOG_COUNT = 10
        private const val ATTRIBUTES_PER_LOG = 20
    }
}
