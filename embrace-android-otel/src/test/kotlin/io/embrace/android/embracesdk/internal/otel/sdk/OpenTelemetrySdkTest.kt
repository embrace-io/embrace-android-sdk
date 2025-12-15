package io.embrace.android.embracesdk.internal.otel.sdk

import io.embrace.android.embracesdk.assertions.assertExpectedAttributes
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeLogRecordExporter
import io.embrace.android.embracesdk.fakes.FakeOtelKotlinClock
import io.embrace.android.embracesdk.fakes.FakeSpanExporter
import io.embrace.android.embracesdk.fakes.FakeSpanService
import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.otel.config.OtelSdkConfig
import io.embrace.android.embracesdk.internal.otel.logs.LogSink
import io.embrace.android.embracesdk.internal.otel.logs.LogSinkImpl
import io.embrace.android.embracesdk.internal.otel.spans.SpanSink
import io.embrace.android.embracesdk.internal.otel.spans.SpanSinkImpl
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalApi::class)
internal class OpenTelemetrySdkTest {

    private lateinit var spanSink: SpanSink
    private lateinit var logSink: LogSink
    private lateinit var systemInfo: SystemInfo
    private lateinit var configuration: OtelSdkConfig
    private lateinit var spanExporter: FakeSpanExporter
    private lateinit var logExporter: FakeLogRecordExporter
    private lateinit var sdk: OtelSdkWrapper

    @Before
    fun setup() {
        spanSink = SpanSinkImpl()
        logSink = LogSinkImpl()
        systemInfo = SystemInfo()
        sdk = createSdkWrapper()
    }

    @Test
    fun `check resource added by sdk tracer`() {
        sdk.sdkTracer.createSpan("test").end()
        spanExporter.exportedSpans.single().resource.assertExpectedAttributes(
            expectedServiceName = configuration.packageName,
            expectedServiceVersion = configuration.appVersion,
            systemInfo = systemInfo,
            expectedDistroName = configuration.sdkName,
            expectedDistroVersion = configuration.sdkVersion
        )
    }

    @Test
    fun `check resource added by default logger`() {
        sdk.openTelemetryKotlin.loggerProvider.getLogger("my_logger").log()
        checkNotNull(logExporter.exportedLogs).single().resource.assertExpectedAttributes(
            expectedServiceName = configuration.packageName,
            expectedServiceVersion = configuration.appVersion,
            systemInfo = systemInfo,
            expectedDistroName = configuration.sdkName,
            expectedDistroVersion = configuration.sdkVersion
        )
    }

    @Test
    fun `sdk name and version used as instrumentation scope for tracer instance used by embrace`() {
        sdk.sdkTracer
            .createSpan("test")
            .end()
        with(spanExporter.exportedSpans.single().instrumentationScopeInfo) {
            assertEquals(configuration.sdkName, name)
            assertEquals(configuration.sdkVersion, version)
            assertNull(schemaUrl)
        }
    }

    @Test
    fun `instrumentation scope set properly on external tracer`() {
        val tracer = sdk.openTelemetryKotlin.tracerProvider.getTracer(
            name = "testScope",
            version = "v1",
            schemaUrl = "url"
        )
        tracer.createSpan("test").end()
        with(spanExporter.exportedSpans.single().instrumentationScopeInfo) {
            assertEquals("testScope", name)
            assertEquals("v1", version)
            assertEquals("url", schemaUrl)
        }
    }

    @Test
    fun `verify that the default StorageContext is used if Java SDK is used`() {
        sdk = createSdkWrapper()
        assertEquals("default", System.getProperty("io.opentelemetry.context.contextStorageProvider"))
    }

    private fun createOtelSdkConfig(): OtelSdkConfig {
        val configuration = OtelSdkConfig(
            spanSink = spanSink,
            logSink = logSink,
            sdkName = "sdk",
            sdkVersion = "1.0",
            appVersion = "2.5.1",
            packageName = "com.test.app",
            systemInfo = systemInfo,
        )
        spanExporter = FakeSpanExporter()
        logExporter = FakeLogRecordExporter()
        configuration.addSpanExporter(spanExporter)
        configuration.addLogExporter(logExporter)

        return configuration
    }

    private fun createSdkWrapper(): OtelSdkWrapper {
        configuration = createOtelSdkConfig()
        return OtelSdkWrapper(
            otelClock = FakeOtelKotlinClock(FakeClock()),
            configuration = configuration,
            spanService = FakeSpanService(),
            useKotlinSdk = false,
        )
    }
}
