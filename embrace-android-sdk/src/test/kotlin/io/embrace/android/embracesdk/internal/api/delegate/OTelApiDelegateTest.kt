package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.fakes.FakeAttributesMutator
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fakes.FakeLogRecordExporter
import io.embrace.android.embracesdk.fakes.FakeLogRecordProcessor
import io.embrace.android.embracesdk.fakes.FakeOtelKotlinClock
import io.embrace.android.embracesdk.fakes.FakeSpanExporter
import io.embrace.android.embracesdk.fakes.FakeSpanProcessor
import io.embrace.android.embracesdk.fakes.FakeSpanService
import io.embrace.android.embracesdk.fakes.OtelSdkMode
import io.embrace.android.embracesdk.fakes.TestUuidSource
import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.otel.config.OtelSdkConfig
import io.embrace.android.embracesdk.internal.otel.export.ExternalExportDispatcher
import io.embrace.android.embracesdk.internal.otel.logs.LogSinkImpl
import io.embrace.android.embracesdk.internal.otel.sdk.OtelSdkWrapper
import io.embrace.android.embracesdk.internal.otel.spans.SpanRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
internal class OTelApiDelegateTest(
    private val otelSdkMode: OtelSdkMode,
) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun modes(): List<Array<Any>> = OtelSdkMode.parameters()
    }

    private lateinit var otelSdkWrapper: OtelSdkWrapper
    private lateinit var delegate: OTelApiDelegate
    private lateinit var cfg: OtelSdkConfig

    @Before
    fun setUp() {
        cfg = OtelSdkConfig(
            spanRepository = SpanRepository(FakeErrorHandler()),
            logSink = LogSinkImpl(),
            sdkName = "sdk",
            sdkVersion = "1.0",
            appVersion = "1.0.0",
            packageName = "com.test.app",
            systemInfo = SystemInfo(),
            uuidSource = TestUuidSource(),
            externalExportDispatcher = ExternalExportDispatcher(FakeErrorHandler()),
        )
        otelSdkWrapper = OtelSdkWrapper(
            otelClock = FakeOtelKotlinClock(),
            configuration = cfg,
            spanServiceProvider = { FakeSpanService() },
            errorHandler = FakeErrorHandler(),
            useKotlinSdk = otelSdkMode.useKotlinSdk,
        )
        delegate = OTelApiDelegate(lazyOf(otelSdkWrapper))
    }

    @Test
    fun `add exporters after start`() {
        delegate.addSpanExporter(FakeSpanExporter())
        delegate.addLogRecordExporter(FakeLogRecordExporter())
        assertFalse(cfg.externalConfig.hasConfiguredOtlpExport())
    }

    @Test
    fun `add processors after start`() {
        delegate.addSpanProcessor(FakeSpanProcessor())
        delegate.addLogRecordProcessor(FakeLogRecordProcessor())
        assertFalse(cfg.externalConfig.hasConfiguredOtlpExport())
    }

    @Test
    fun `get opentelemetry kotlin returns sdk instance`() {
        assertSame(
            otelSdkWrapper.openTelemetryKotlin,
            delegate.getOpenTelemetryKotlin(),
        )
    }

    @Test
    fun `set resource attribute after sdk starts`() {
        delegate.setResourceAttribute("test", "foo")
        val attrs = FakeAttributesMutator().apply(cfg.resourceAction).attributes
        assertNull(attrs["test"])
    }
}
