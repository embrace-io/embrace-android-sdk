package io.embrace.android.embracesdk.internal.api.delegate

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakeLogRecordExporter
import io.embrace.android.embracesdk.fakes.FakeMutableAttributeContainer
import io.embrace.android.embracesdk.fakes.FakeOtelJavaLogRecordExporter
import io.embrace.android.embracesdk.fakes.FakeSpanExporter
import io.embrace.android.embracesdk.fakes.FakeSpanService
import io.embrace.android.embracesdk.fakes.FakeTelemetryService
import io.embrace.android.embracesdk.fakes.fakeModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.otel.config.OtelSdkConfig
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.OpenTelemetryInstance
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaOpenTelemetry
import io.embrace.opentelemetry.kotlin.noop
import io.opentelemetry.semconv.ServiceAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalApi::class)
@RunWith(AndroidJUnit4::class)
internal class OTelApiDelegateTest {

    private lateinit var bootstrapper: ModuleInitBootstrapper
    private lateinit var delegate: OTelApiDelegate
    private lateinit var cfg: OtelSdkConfig
    private lateinit var sdkCallChecker: SdkCallChecker

    @Before
    fun setUp() {
        bootstrapper = fakeModuleInitBootstrapper(useKotlinSdk = false)
        bootstrapper.init(ApplicationProvider.getApplicationContext(), AppFramework.NATIVE, 0)
        cfg = bootstrapper.openTelemetryModule.otelSdkConfig

        sdkCallChecker = SdkCallChecker(FakeEmbLogger(), FakeTelemetryService())
        sdkCallChecker.started.set(true)
        delegate = OTelApiDelegate(bootstrapper, sdkCallChecker)
    }

    @Test
    fun `add span exporter before start`() {
        sdkCallChecker.started.set(false)
        delegate.addSpanExporter(FakeSpanExporter())
        assertTrue(bootstrapper.openTelemetryModule.otelSdkConfig.hasConfiguredOtelExporters())
    }

    @Test
    fun `add log exporter before start`() {
        sdkCallChecker.started.set(false)
        delegate.addLogRecordExporter(FakeOtelJavaLogRecordExporter())
        assertTrue(bootstrapper.openTelemetryModule.otelSdkConfig.hasConfiguredOtelExporters())
    }

    @Test
    fun `add exporters after start`() {
        delegate.addSpanExporter(FakeSpanExporter())
        delegate.addLogRecordExporter(FakeLogRecordExporter())
        assertFalse(bootstrapper.openTelemetryModule.otelSdkConfig.hasConfiguredOtelExporters())
    }

    @Test
    fun `get opentelemetry before start`() {
        sdkCallChecker.started.set(false)
        assertEquals(OtelJavaOpenTelemetry.noop(), delegate.getOpenTelemetry())
    }

    @Test
    fun `get opentelemetry after start`() {
        assertNotEquals(OtelJavaOpenTelemetry.noop(), delegate.getOpenTelemetry())
    }

    @Test
    fun `get opentelemetry kotlin before start`() {
        sdkCallChecker.started.set(false)
        assertEquals(OpenTelemetryInstance.noop(), delegate.getOpenTelemetryKotlin())
    }

    @Test
    fun `get opentelemetry kotlin after start`() {
        assertNotEquals(OpenTelemetryInstance.noop(), delegate.getOpenTelemetryKotlin())
    }

    @Test
    fun `get tracer before start`() {
        sdkCallChecker.started.set(false)
        assertFalse(delegate.getOpenTelemetry().getTracer("foo").spanBuilder("test").startSpan().spanContext.isValid)
    }

    @Test
    fun `get tracer after start`() {
        assertTrue(delegate.getOpenTelemetry().getTracer("foo").spanBuilder("test").startSpan().spanContext.isValid)
    }

    @Test
    fun `set resource attribute before sdk starts`() {
        sdkCallChecker.started.set(false)
        delegate.setResourceAttribute("test", "foo")
        val attrs = FakeMutableAttributeContainer().apply(cfg.resourceAction).attributes
        assertEquals("foo", attrs["test"])
    }

    @Test
    fun `override resource attribute before sdk starts`() {
        sdkCallChecker.started.set(false)
        delegate.setResourceAttribute(ServiceAttributes.SERVICE_NAME.key, "foo")
        val attrs = FakeMutableAttributeContainer().apply(cfg.resourceAction).attributes
        assertEquals("foo", attrs[ServiceAttributes.SERVICE_NAME.key])
    }

    @Test
    fun `set resource attribute after sdk starts`() {
        delegate.setResourceAttribute("test", "foo")
        val attrs = FakeMutableAttributeContainer().apply(cfg.resourceAction).attributes
        assertNull(attrs["test"])
    }

    @Test
    fun `getOpenTelemetry and getOpenTelemetryKotlin should be wired through EmbraceSpanService`() {
        val spanService = bootstrapper.openTelemetryModule.spanService as FakeSpanService

        val javaTracer = delegate.getOpenTelemetry().getTracer("test-java")
        val kotlinTracer = delegate.getOpenTelemetryKotlin().tracerProvider.getTracer("test-kotlin")
        
        // Create spans with both APIs
        val javaSpan = javaTracer.spanBuilder("java-span").startSpan()
        val kotlinSpan = kotlinTracer.createSpan("kotlin-span")
        
        // Both should go through Embrace's SpanService, so Embrace tracks when these exposed instances are used in 3rd party
        // instrumentation.
        assertEquals(2, spanService.createdSpans.size)
        
        javaSpan.end()
        kotlinSpan.end()
    }
}
