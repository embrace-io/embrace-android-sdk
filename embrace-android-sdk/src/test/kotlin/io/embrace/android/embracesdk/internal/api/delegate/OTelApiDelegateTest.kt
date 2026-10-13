package io.embrace.android.embracesdk.internal.api.delegate

import androidx.test.core.app.ApplicationProvider
import io.embrace.android.embracesdk.fakes.FakeAttributesMutator
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.FakeLogRecordExporter
import io.embrace.android.embracesdk.fakes.FakeLogRecordProcessor
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryModule
import io.embrace.android.embracesdk.fakes.FakeSpanExporter
import io.embrace.android.embracesdk.fakes.FakeSpanProcessor
import io.embrace.android.embracesdk.fakes.OtelSdkMode
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.otel.config.OtelSdkConfig
import io.opentelemetry.kotlin.NoopOpenTelemetry
import io.opentelemetry.kotlin.OpenTelemetry
import io.opentelemetry.kotlin.semconv.ServiceAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

@RunWith(ParameterizedRobolectricTestRunner::class)
internal class OTelApiDelegateTest(
    private val otelSdkMode: OtelSdkMode,
) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun modes(): List<Array<Any>> = OtelSdkMode.parameters()
    }

    private lateinit var bootstrapper: ModuleInitBootstrapper
    private lateinit var delegate: OTelApiDelegate
    private lateinit var cfg: OtelSdkConfig
    private lateinit var sdkCallChecker: SdkCallChecker
    private lateinit var openTelemetryKotlin: OpenTelemetry

    @Before
    fun setUp() {
        bootstrapper = ModuleInitBootstrapper(
            FakeInitModule(otelSdkMode = OtelSdkMode.COMPAT),
            FakeOpenTelemetryModule(otelSdkMode = otelSdkMode),
        )
        bootstrapper.init(ApplicationProvider.getApplicationContext())
        cfg = bootstrapper.openTelemetryModule.otelSdkConfig

        sdkCallChecker = SdkCallChecker(FakeInternalLogger())
        sdkCallChecker.started.set(true)
        openTelemetryKotlin = LateBindingOpenTelemetry {
            if (sdkCallChecker.started.get()) {
                bootstrapper.openTelemetryModule.otelSdkWrapper.openTelemetryKotlin
            } else {
                NoopOpenTelemetry
            }
        }
        delegate = OTelApiDelegate(bootstrapper, sdkCallChecker, openTelemetryKotlin)
    }

    @Test
    fun `add span exporter before start`() {
        sdkCallChecker.started.set(false)
        delegate.addSpanExporter(FakeSpanExporter())
        assertTrue(bootstrapper.openTelemetryModule.otelSdkConfig.hasConfiguredOtlpExport())
    }

    @Test
    fun `add log exporter before start`() {
        sdkCallChecker.started.set(false)
        delegate.addLogRecordExporter(FakeLogRecordExporter())
        assertTrue(bootstrapper.openTelemetryModule.otelSdkConfig.hasConfiguredOtlpExport())
    }

    @Test
    fun `add exporters after start`() {
        delegate.addSpanExporter(FakeSpanExporter())
        delegate.addLogRecordExporter(FakeLogRecordExporter())
        assertFalse(bootstrapper.openTelemetryModule.otelSdkConfig.hasConfiguredOtlpExport())
    }

    @Test
    fun `add span processor before start`() {
        sdkCallChecker.started.set(false)
        delegate.addSpanProcessor(FakeSpanProcessor())
        assertTrue(bootstrapper.openTelemetryModule.otelSdkConfig.hasConfiguredOtlpExport())
    }

    @Test
    fun `add log processor before start`() {
        sdkCallChecker.started.set(false)
        delegate.addLogRecordProcessor(FakeLogRecordProcessor())
        assertTrue(bootstrapper.openTelemetryModule.otelSdkConfig.hasConfiguredOtlpExport())
    }

    @Test
    fun `add processors after start`() {
        delegate.addSpanProcessor(FakeSpanProcessor())
        delegate.addLogRecordProcessor(FakeLogRecordProcessor())
        assertFalse(bootstrapper.openTelemetryModule.otelSdkConfig.hasConfiguredOtlpExport())
    }

    @Test
    fun `get opentelemetry kotlin binds late`() {
        sdkCallChecker.started.set(false)
        val otel = delegate.getOpenTelemetryKotlin()
        assertSame(openTelemetryKotlin, otel)
        val tracer = otel.tracerProvider.getTracer("test")
        assertFalse(tracer.startSpan("before").isRecording())

        sdkCallChecker.started.set(true)
        assertTrue(tracer.startSpan("after").isRecording())
    }

    @Test
    fun `context captured before start binds late`() {
        sdkCallChecker.started.set(false)
        val otel = delegate.getOpenTelemetryKotlin()
        val context = otel.context

        sdkCallChecker.started.set(true)
        val span = otel.tracerProvider.getTracer("test").startSpan("span")
        val scope = context.root().storeSpan(span).attach()
        try {
            assertEquals(span.spanContext.traceId, context.implicit().extractSpan().spanContext.traceId)
        } finally {
            scope.detach()
        }
    }

    @Test
    fun `set resource attribute before sdk starts`() {
        sdkCallChecker.started.set(false)
        delegate.setResourceAttribute("test", "foo")
        val attrs = FakeAttributesMutator().apply(cfg.resourceAction).attributes
        assertEquals("foo", attrs["test"])
    }

    @Test
    fun `override resource attribute before sdk starts`() {
        sdkCallChecker.started.set(false)
        delegate.setResourceAttribute(ServiceAttributes.SERVICE_NAME, "foo")
        val attrs = FakeAttributesMutator().apply(cfg.resourceAction).attributes
        assertEquals("foo", attrs[ServiceAttributes.SERVICE_NAME])
    }

    @Test
    fun `set resource attribute after sdk starts`() {
        delegate.setResourceAttribute("test", "foo")
        val attrs = FakeAttributesMutator().apply(cfg.resourceAction).attributes
        assertNull(attrs["test"])
    }
}
