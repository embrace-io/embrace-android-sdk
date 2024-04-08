package io.embrace.android.embracesdk.opentelemetry

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeLogRecordExporter
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryClock
import io.embrace.android.embracesdk.fakes.FakeSpanExporter
import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.logs.LogSink
import io.embrace.android.embracesdk.internal.logs.LogSinkImpl
import io.embrace.android.embracesdk.internal.spans.SpanSink
import io.embrace.android.embracesdk.internal.spans.SpanSinkImpl
import org.junit.Before
import org.junit.Test

internal class OpenTelemetrySdkTest {

    private lateinit var spanSink: SpanSink
    private lateinit var logSink: LogSink
    private lateinit var systemInfo: SystemInfo
    private lateinit var configuration: OpenTelemetryConfiguration
    private lateinit var spanExporter: FakeSpanExporter
    private lateinit var logExporter: FakeLogRecordExporter
    private lateinit var sdk: OpenTelemetrySdk

    @Before
    fun setup() {
        spanSink = SpanSinkImpl()
        logSink = LogSinkImpl()
        systemInfo = SystemInfo()
        configuration = OpenTelemetryConfiguration(
            spanSink = spanSink,
            logSink = logSink,
            systemInfo = systemInfo,
            processIdentifier = "fakeProcessIdentifier"
        )
        spanExporter = FakeSpanExporter()
        logExporter = FakeLogRecordExporter()
        configuration.addSpanExporter(spanExporter)
        configuration.addLogExporter(logExporter)

        sdk = OpenTelemetrySdk(
            openTelemetryClock = FakeOpenTelemetryClock(FakeClock()),
            configuration = configuration
        )
    }

    @Test
    fun `check resource added by default tracer`() {
        sdk.getOpenTelemetryTracer().spanBuilder("test").startSpan().end()
        spanExporter.exportedSpans.single().resource.assertExpectedAttributes(
            expectedServiceName = configuration.embraceServiceName,
            expectedServiceVersion = configuration.embraceVersionName,
            systemInfo = systemInfo
        )
    }

    @Test
    fun `check resource added by default logger`() {
        sdk.getOpenTelemetryLogger().logRecordBuilder().emit()
        checkNotNull(logExporter.exportedLogs).single().resource.assertExpectedAttributes(
            expectedServiceName = configuration.embraceServiceName,
            expectedServiceVersion = configuration.embraceVersionName,
            systemInfo = systemInfo
        )
    }
}
