package io.embrace.android.embracesdk.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.clock.NormalizedIntervalClock
import io.embrace.android.embracesdk.internal.otel.config.OtelSdkConfig
import io.embrace.android.embracesdk.internal.otel.impl.EmbClock
import io.embrace.android.embracesdk.internal.otel.logs.LogSinkImpl
import io.embrace.android.embracesdk.internal.otel.sdk.DataValidator
import io.embrace.android.embracesdk.internal.otel.sdk.OtelSdkWrapper
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanFactoryImpl
import io.embrace.android.embracesdk.internal.otel.spans.SpanRepository
import io.embrace.android.embracesdk.internal.otel.spans.SpanServiceImpl
import io.embrace.android.embracesdk.internal.otel.spans.SpanSink
import io.embrace.android.embracesdk.internal.otel.spans.SpanSinkImpl
import io.embrace.android.embracesdk.internal.otel.spans.UninitializedSdkSpanService
import io.embrace.android.embracesdk.spans.EmbraceSpan
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@Suppress("OPT_IN_USAGE")
@RunWith(AndroidJUnit4::class)
class TracingApiBenchmarks {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private val attributesPairs = (1..ATTRIBUTES_PER_SPAN).map { Pair("test-key-$it", "test-long-ish-values-$it") }
    private val eventNames = (1..EVENTS_PER_SPAN).map { "test-span-event-name-$it" }
    private val extraAttributes = mapOf(attributesPairs.first().first to attributesPairs.first().second)

    private lateinit var spanSink: SpanSink
    private lateinit var spansService: SpanServiceImpl

    @Before
    fun setup() {
        val clock = EmbClock(NormalizedIntervalClock())
        val dataValidator = DataValidator()
        val spanRepository = SpanRepository()
        spanSink = SpanSinkImpl()
        val otelSdkWrapper = OtelSdkWrapper(
            otelClock = clock,
            configuration = OtelSdkConfig(
                spanSink = spanSink,
                logSink = LogSinkImpl(),
                sdkName = "benchmark-test-sdk",
                sdkVersion = "1.0",
                systemInfo = SystemInfo(),
                sessionIdProvider = { "fake-session-id" },
                processIdentifierProvider = { "fake-pid" }
            ),
            spanService = UninitializedSdkSpanService()
        )
        spansService = SpanServiceImpl(
            tracer = otelSdkWrapper.sdkTracer,
            spanRepository = spanRepository,
            embraceSpanFactory =
                EmbraceSpanFactoryImpl(
                    openTelemetryClock = clock,
                    spanRepository = spanRepository,
                    dataValidator = dataValidator
                ),
            dataValidator = dataValidator,
            canStartNewSpan = { _, _ -> true },
            initCallback = { },
            openTelemetry = otelSdkWrapper.kotlinApi,
        )
    }

    @Test
    fun createSpans() {
        benchmarkRule.measureRepeated {
            repeat(TOTAL_SPAN_COUNT) {
                createSpan("test")
            }
        }
    }

    @Test
    fun createSpansWithEverything() {
        benchmarkRule.measureRepeated {
            repeat(TOTAL_SPAN_COUNT) {
                createSpan(
                    name = "test",
                    attributes = true,
                    events = true,
                    links = true,
                )
            }
        }
    }

    @Test
    fun startSpans() {
        benchmarkRule.measureRepeated {
            val spans = mutableListOf<EmbraceSpan>()
            runWithTimingDisabled {
                repeat(TOTAL_SPAN_COUNT) {
                    spans.add(createSpan("test"))
                }
            }

            spans.forEach {
                it.start()
            }

            stopAndCleanup(spans)
        }
    }

    @Test
    fun startSpansWithEverything() {
        benchmarkRule.measureRepeated {
            val spans = mutableListOf<EmbraceSpan>()
            runWithTimingDisabled {
                repeat(TOTAL_SPAN_COUNT) {
                    spans.add(
                        createSpan(
                            name = "test",
                            attributes = true,
                            events = true,
                            links = true,
                        )
                    )
                }
            }

            spans.forEach {
                it.start()
            }

            stopAndCleanup(spans)
        }
    }

    @Test
    fun stopSpans() {
        benchmarkRule.measureRepeated {
            val spans = mutableListOf<EmbraceSpan>()
            runWithTimingDisabled {
                repeat(TOTAL_SPAN_COUNT) {
                    spans.add(createSpan(name = "test", start = true))
                }
            }

            spans.forEach {
                it.stop()
            }

            verifyAndCleanup()
        }
    }

    @Test
    fun stopSpansWithEverything() {
        benchmarkRule.measureRepeated {
            val spans = mutableListOf<EmbraceSpan>()
            runWithTimingDisabled {
                repeat(TOTAL_SPAN_COUNT) {
                    spans.add(
                        createSpan(
                            name = "test",
                            attributes = true,
                            events = true,
                            links = true,
                            start = true,
                        )
                    )
                }
            }

            spans.forEach {
                it.stop()
            }

            verifyAndCleanup()
        }
    }

    @Test
    fun startAndStopSpans() {
        benchmarkRule.measureRepeated {
            val spans = mutableListOf<EmbraceSpan>()
            repeat(TOTAL_SPAN_COUNT) {
                spans.add(createSpan(name = "test", start = true))
            }
            spans.forEach {
                it.stop()
            }
            verifyAndCleanup()
        }
    }

    @Test
    fun startAndStopTrace() {
        benchmarkRule.measureRepeated {
            createAndStartTrace().stopTrace()
            verifyAndCleanup()
        }
    }

    @Test
    fun startAndStopTraceWithAttributes() {
        benchmarkRule.measureRepeated {
            createAndStartTrace(attributes = true).stopTrace()
            verifyAndCleanup()
        }
    }

    @Test
    fun startAndStopTraceWithEvents() {
        benchmarkRule.measureRepeated {
            createAndStartTrace(events = true).stopTrace()
            verifyAndCleanup()
        }
    }

    @Test
    fun startAndStopTraceWithLinks() {
        benchmarkRule.measureRepeated {
            createAndStartTrace(links = true).stopTrace()
            verifyAndCleanup()
        }
    }

    @Test
    fun startAndStopTraceWithEverything() {
        benchmarkRule.measureRepeated {
            createAndStartTrace(
                attributes = true,
                events = true,
                links = true
            ).stopTrace()
            verifyAndCleanup()
        }
    }

    /**
     * Return a list of spans that belong to the same trace
     */
    private fun createAndStartTrace(
        attributes: Boolean = false,
        events: Boolean = false,
        links: Boolean = false,
    ): List<EmbraceSpan> {
        val traceSpans = mutableListOf<EmbraceSpan>()
        val parent = createSpan(
            name = "parent",
            attributes = attributes,
            events = events,
            start = true
        ).apply {
            traceSpans.add(this)
        }
        repeat(TOTAL_SPAN_COUNT - 1) {
            traceSpans.add(
                createSpan(
                    name = "child",
                    parent = parent,
                    attributes = attributes,
                    events = events,
                    links = links,
                    start = true
                )
            )
        }
        return traceSpans
    }

    private fun List<EmbraceSpan>.stopTrace() {
        forEach {
            if (it.parent != null) {
                it.stop()
            }
        }
        single { it.parent == null }.stop()
    }

    private fun createSpan(
        name: String,
        parent: EmbraceSpan? = null,
        attributes: Boolean = false,
        events: Boolean = false,
        links: Boolean = false,
        start: Boolean = false,
    ): EmbraceSpan {
        return checkNotNull(spansService.createSpan(name = name, parent = parent) as EmbraceSpan).apply {
            if (start) {
                start()
            }
            if (attributes) {
                attributesPairs.forEach {
                    addAttribute(it.first, it.second)
                }
            }

            if (events) {
                eventNames.forEach {
                    addEvent(name = it, timestampMs = null, attributes = extraAttributes)
                }
            }

            if (links && parent != null) {
                repeat(LINKS_PER_SPAN) {
                    addLink(linkedSpan = parent, attributes = extraAttributes)
                }
            }
        }
    }

    private fun BenchmarkRule.Scope.stopAndCleanup(spans: List<EmbraceSpan>) {
        runWithTimingDisabled {
            spans.forEach {
                it.stop()
            }
        }
        verifyAndCleanup()
    }

    private fun BenchmarkRule.Scope.verifyAndCleanup() {
        runWithTimingDisabled {
            assertEquals(TOTAL_SPAN_COUNT, spanSink.completedSpans().size)
            spanSink.flushSpans()
        }
    }

    companion object {
        private const val TOTAL_SPAN_COUNT = 10
        private const val ATTRIBUTES_PER_SPAN = 20
        private const val EVENTS_PER_SPAN = 10
        private const val LINKS_PER_SPAN = 5
    }
}
