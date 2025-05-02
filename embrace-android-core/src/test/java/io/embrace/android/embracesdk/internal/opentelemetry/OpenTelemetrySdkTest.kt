package io.embrace.android.embracesdk.internal.opentelemetry

import io.embrace.android.embracesdk.assertions.assertExpectedAttributes
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeLogRecordExporter
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryClock
import io.embrace.android.embracesdk.fakes.FakeSpanExporter
import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.logs.LogSink
import io.embrace.android.embracesdk.internal.logs.LogSinkImpl
import io.embrace.android.embracesdk.internal.spans.SpanSink
import io.embrace.android.embracesdk.internal.spans.SpanSinkImpl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
            systemInfo = systemInfo
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
    fun `check resource added by sdk tracer`() {
        sdk.sdkTracer.spanBuilder("test").startSpan().end()
        spanExporter.exportedSpans.single().resource.assertExpectedAttributes(
            expectedServiceName = configuration.embraceSdkName,
            expectedServiceVersion = configuration.embraceSdkVersion,
            systemInfo = systemInfo
        )
    }

    @Test
    fun `check resource added by default logger`() {
        sdk.getOpenTelemetryLogger().logRecordBuilder().emit()
        checkNotNull(logExporter.exportedLogs).single().resource.assertExpectedAttributes(
            expectedServiceName = configuration.embraceSdkName,
            expectedServiceVersion = configuration.embraceSdkVersion,
            systemInfo = systemInfo
        )
    }

    @Test
    fun `sdk name and version used as instrumentation scope for tracer instance used by embrace`() {
        sdk.sdkTracer
            .spanBuilder("test")
            .startSpan()
            .end()
        with(spanExporter.exportedSpans.single().instrumentationScopeInfo) {
            assertEquals(configuration.embraceSdkName, name)
            assertEquals(configuration.embraceSdkVersion, version)
            assertNull(schemaUrl)
        }
    }

    @Test
    fun `instrumentation scope set properly on external tracer`() {
        sdk.sdkTracerProvider
            .tracerBuilder("testScope")
            .setInstrumentationVersion("v1")
            .setSchemaUrl("url")
            .build()
            .spanBuilder("test")
            .startSpan()
            .end()
        with(spanExporter.exportedSpans.single().instrumentationScopeInfo) {
            assertEquals("testScope", name)
            assertEquals("v1", version)
            assertEquals("url", schemaUrl)
        }
    }

    @Test
    fun `verify that the default StorageContext is used after OpenTelemetrySdk is initialized`() {
        assertEquals("default", System.getProperty("io.opentelemetry.context.contextStorageProvider"))
    }
}
