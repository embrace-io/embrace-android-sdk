package io.embrace.android.embracesdk.internal.api.delegate

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakeLogRecordExporter
import io.embrace.android.embracesdk.fakes.FakeSpanExporter
import io.embrace.android.embracesdk.fakes.FakeTelemetryService
import io.embrace.android.embracesdk.fakes.fakeModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.opentelemetry.OpenTelemetryConfiguration
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.opentelemetry.api.OpenTelemetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class OTelApiDelegateTest {

    private lateinit var bootstrapper: ModuleInitBootstrapper
    private lateinit var delegate: OTelApiDelegate
    private lateinit var cfg: OpenTelemetryConfiguration
    private lateinit var sdkCallChecker: SdkCallChecker

    @Before
    fun setUp() {
        bootstrapper = fakeModuleInitBootstrapper()
        bootstrapper.init(ApplicationProvider.getApplicationContext(), AppFramework.NATIVE, 0)
        cfg = bootstrapper.openTelemetryModule.openTelemetryConfiguration

        sdkCallChecker = SdkCallChecker(FakeEmbLogger(), FakeTelemetryService())
        sdkCallChecker.started.set(true)
        delegate = OTelApiDelegate(bootstrapper, sdkCallChecker)
    }

    @Test
    fun `add span exporter before start`() {
        sdkCallChecker.started.set(false)
        delegate.addSpanExporter(FakeSpanExporter())
        assertTrue(bootstrapper.openTelemetryModule.openTelemetryConfiguration.hasConfiguredOtelExporters())
    }

    @Test
    fun `add log exporter before start`() {
        sdkCallChecker.started.set(false)
        delegate.addLogRecordExporter(FakeLogRecordExporter())
        assertTrue(bootstrapper.openTelemetryModule.openTelemetryConfiguration.hasConfiguredOtelExporters())
    }

    @Test
    fun `add exporters after start`() {
        delegate.addSpanExporter(FakeSpanExporter())
        delegate.addLogRecordExporter(FakeLogRecordExporter())
        assertFalse(bootstrapper.openTelemetryModule.openTelemetryConfiguration.hasConfiguredOtelExporters())
    }

    @Test
    fun `get opentelemetry before start`() {
        sdkCallChecker.started.set(false)
        assertEquals(OpenTelemetry.noop(), delegate.getOpenTelemetry())
    }

    @Test
    fun `get opentelemetry after start`() {
        assertNotEquals(OpenTelemetry.noop(), delegate.getOpenTelemetry())
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
}
