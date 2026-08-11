package io.embrace.android.embracesdk.internal.otel.sdk

import io.embrace.android.embracesdk.assertions.assertExpectedAttributes
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeLogRecordExporter
import io.embrace.android.embracesdk.fakes.FakeOtelKotlinClock
import io.embrace.android.embracesdk.fakes.FakeSpanExporter
import io.embrace.android.embracesdk.fakes.FakeSpanService
import io.embrace.android.embracesdk.fakes.TestUuidSource
import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.otel.config.OtelSdkConfig
import io.embrace.android.embracesdk.internal.otel.createSdkOtelInstance
import io.embrace.android.embracesdk.internal.otel.export.immediateExportDispatcher
import io.embrace.android.embracesdk.internal.otel.logs.LogSink
import io.embrace.android.embracesdk.internal.otel.logs.LogSinkImpl
import io.embrace.android.embracesdk.internal.otel.spans.SpanRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal class OpenTelemetrySdkTest {

    private lateinit var spanRepository: SpanRepository
    private lateinit var logSink: LogSink
    private lateinit var systemInfo: SystemInfo
    private lateinit var configuration: OtelSdkConfig
    private lateinit var spanExporter: FakeSpanExporter
    private lateinit var logExporter: FakeLogRecordExporter
    private lateinit var sdk: OtelSdkWrapper

    @Before
    fun setup() {
        spanRepository = SpanRepository()
        logSink = LogSinkImpl()
        systemInfo = SystemInfo()
        sdk = createSdkWrapper()
    }

    @Test
    fun `check resource added by sdk tracer`() {
        sdk.sdkTracer.startSpan("test").end()
        spanExporter.exportedSpans.single().resource.assertExpectedAttributes(
            expectedServiceName = configuration.packageName,
            expectedServiceVersion = configuration.appVersion,
            systemInfo = systemInfo,
            expectedDistroName = configuration.sdkName,
            expectedDistroVersion = configuration.sdkVersion,
        )
    }

    @Test
    fun `check resource added by sdk logger`() {
        sdk.sdkLogger.emit()
        checkNotNull(logExporter.exportedLogs).single().resource.assertExpectedAttributes(
            expectedServiceName = configuration.packageName,
            expectedServiceVersion = configuration.appVersion,
            systemInfo = systemInfo,
            expectedDistroName = configuration.sdkName,
            expectedDistroVersion = configuration.sdkVersion,
        )
    }

    @Test
    fun `sdk name and version used as instrumentation scope for tracer instance used by embrace`() {
        sdk.sdkTracer
            .startSpan("test")
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
            schemaUrl = "url",
        )
        tracer.startSpan("test").end()
        with(spanExporter.exportedSpans.single().instrumentationScopeInfo) {
            assertEquals("testScope", name)
            assertEquals("v1", version)
            assertEquals("url", schemaUrl)
        }
    }

    @Test
    fun `sdk name and version used as instrumentation scope for logger instance used by embrace`() {
        sdk.sdkLogger.emit()
        with(logExporter.exportedLogs.single().instrumentationScopeInfo) {
            assertEquals(configuration.sdkName, name)
            assertEquals(configuration.sdkVersion, version)
            assertNull(schemaUrl)
        }
    }

    @Test
    fun `instrumentation scope set properly on external logger`() {
        val loggerProvider = sdk.openTelemetryKotlin.loggerProvider
        val logger = loggerProvider.getLogger(
            name = "testScope",
            version = "v1",
            schemaUrl = "url",
        )
        logger.emit()
        with(logExporter.exportedLogs.single().instrumentationScopeInfo) {
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

    @Test
    fun `implicit context is confined to the thread that attached it if Kotlin SDK is used`() {
        val otel = createSdkOtelInstance(useKotlinSdk = true, clock = FakeOtelKotlinClock(FakeClock()))
        val key = otel.context.createKey<String>("test-key")
        val root = otel.context.root()
        val attached = root.set(key, "value")

        val scope = attached.attach()
        try {
            assertEquals("value", otel.context.implicit().get(key))

            val executor = Executors.newSingleThreadExecutor()
            try {
                val otherThreadValue = executor.submit<String?> { otel.context.implicit().get(key) }
                assertNull(otherThreadValue.get(1, TimeUnit.SECONDS))
            } finally {
                executor.shutdown()
            }
        } finally {
            scope.detach()
        }

        assertNull(otel.context.implicit().get(key))
    }

    private fun createOtelSdkConfig(): OtelSdkConfig {
        val configuration = OtelSdkConfig(
            spanRepository = spanRepository,
            logSink = logSink,
            sdkName = "sdk",
            sdkVersion = "1.0",
            appVersion = "2.5.1",
            packageName = "com.test.app",
            systemInfo = systemInfo,
            uuidSource = TestUuidSource(),
            externalExportDispatcher = immediateExportDispatcher(),
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
