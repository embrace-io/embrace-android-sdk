package io.embrace.android.embracesdk.internal.api.delegate

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeAttributeContainer
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakeOtelJavaLogRecordExporter
import io.embrace.android.embracesdk.fakes.FakeOtelJavaSpanExporter
import io.embrace.android.embracesdk.fakes.FakeTelemetryService
import io.embrace.android.embracesdk.fakes.fakeModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.otel.config.OtelSdkConfig
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaOpenTelemetry
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
        bootstrapper = fakeModuleInitBootstrapper()
        bootstrapper.init(ApplicationProvider.getApplicationContext(), AppFramework.NATIVE, 0)
        cfg = bootstrapper.openTelemetryModule.otelSdkConfig

        sdkCallChecker = SdkCallChecker(FakeEmbLogger(), FakeTelemetryService())
        sdkCallChecker.started.set(true)
        delegate = OTelApiDelegate(bootstrapper, sdkCallChecker)
    }

    @Test
    fun `add span exporter before start`() {
        sdkCallChecker.started.set(false)
        delegate.addSpanExporter(FakeOtelJavaSpanExporter())
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
        delegate.addSpanExporter(FakeOtelJavaSpanExporter())
        delegate.addLogRecordExporter(FakeOtelJavaLogRecordExporter())
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
        val attrs = FakeAttributeContainer().apply(cfg.resourceAction).attributes()
        assertEquals("foo", attrs["test"])
    }

    @Test
    fun `override resource attribute before sdk starts`() {
        sdkCallChecker.started.set(false)
        delegate.setResourceAttribute(ServiceAttributes.SERVICE_NAME, "foo")
        val attrs = FakeAttributeContainer().apply(cfg.resourceAction).attributes()
        assertEquals("foo", attrs[ServiceAttributes.SERVICE_NAME.key])
    }

    @Test
    fun `set resource attribute after sdk starts`() {
        delegate.setResourceAttribute("test", "foo")
        val attrs = FakeAttributeContainer().apply(cfg.resourceAction).attributes()
        assertNull(attrs["test"])
    }
}
