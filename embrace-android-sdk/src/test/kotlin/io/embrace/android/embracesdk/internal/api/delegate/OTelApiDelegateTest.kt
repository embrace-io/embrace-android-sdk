package io.embrace.android.embracesdk.internal.api.delegate

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.FakeLogRecordExporter
import io.embrace.android.embracesdk.fakes.FakeMutableAttributeContainer
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryModule
import io.embrace.android.embracesdk.fakes.FakeSpanExporter
import io.embrace.android.embracesdk.fakes.FakeTelemetryService
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.otel.config.OtelSdkConfig
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.createNoopOpenTelemetry
import io.embrace.opentelemetry.kotlin.semconv.ServiceAttributes
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
        bootstrapper = ModuleInitBootstrapper(
            FakeInitModule(),
            FakeOpenTelemetryModule(),
        )
        bootstrapper.init(ApplicationProvider.getApplicationContext())
        cfg = bootstrapper.openTelemetryModule.otelSdkConfig

        sdkCallChecker = SdkCallChecker(FakeInternalLogger(), FakeTelemetryService())
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
        delegate.addLogRecordExporter(FakeLogRecordExporter())
        assertTrue(bootstrapper.openTelemetryModule.otelSdkConfig.hasConfiguredOtelExporters())
    }

    @Test
    fun `add exporters after start`() {
        delegate.addSpanExporter(FakeSpanExporter())
        delegate.addLogRecordExporter(FakeLogRecordExporter())
        assertFalse(bootstrapper.openTelemetryModule.otelSdkConfig.hasConfiguredOtelExporters())
    }

    @Test
    fun `get opentelemetry kotlin before start`() {
        sdkCallChecker.started.set(false)
        assertEquals(createNoopOpenTelemetry(), delegate.getOpenTelemetryKotlin())
    }

    @Test
    fun `get opentelemetry kotlin after start`() {
        assertNotEquals(createNoopOpenTelemetry(), delegate.getOpenTelemetryKotlin())
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
        delegate.setResourceAttribute(ServiceAttributes.SERVICE_NAME, "foo")
        val attrs = FakeMutableAttributeContainer().apply(cfg.resourceAction).attributes
        assertEquals("foo", attrs[ServiceAttributes.SERVICE_NAME])
    }

    @Test
    fun `set resource attribute after sdk starts`() {
        delegate.setResourceAttribute("test", "foo")
        val attrs = FakeMutableAttributeContainer().apply(cfg.resourceAction).attributes
        assertNull(attrs["test"])
    }
}
