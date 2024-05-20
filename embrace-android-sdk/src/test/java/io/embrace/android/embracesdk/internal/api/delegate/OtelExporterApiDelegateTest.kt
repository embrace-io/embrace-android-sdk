package io.embrace.android.embracesdk.internal.api.delegate

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakeLogRecordExporter
import io.embrace.android.embracesdk.fakes.FakeSpanExporter
import io.embrace.android.embracesdk.fakes.FakeTelemetryService
import io.embrace.android.embracesdk.fakes.fakeModuleInitBootstrapper
import io.embrace.android.embracesdk.opentelemetry.OpenTelemetryConfiguration
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class OtelExporterApiDelegateTest {

    private lateinit var delegate: OtelExporterApiDelegate
    private lateinit var cfg: OpenTelemetryConfiguration
    private lateinit var logger: FakeEmbLogger
    private lateinit var sdkCallChecker: SdkCallChecker

    @Before
    fun setUp() {
        val bootstrapper = fakeModuleInitBootstrapper()
        bootstrapper.init(ApplicationProvider.getApplicationContext(), Embrace.AppFramework.NATIVE, 0)
        cfg = bootstrapper.openTelemetryModule.openTelemetryConfiguration
        logger = bootstrapper.logger as FakeEmbLogger

        sdkCallChecker = SdkCallChecker(FakeEmbLogger(), FakeTelemetryService())
        sdkCallChecker.started.set(true)
        delegate = OtelExporterApiDelegate(bootstrapper, sdkCallChecker)
    }

    @Test
    fun `add before start`() {
        sdkCallChecker.started.set(false)
        delegate.addSpanExporter(FakeSpanExporter())
        delegate.addLogRecordExporter(FakeLogRecordExporter())
        assertEquals(0, logger.errorMessages.size)
    }

    @Test
    fun `add after start`() {
        delegate.addSpanExporter(FakeSpanExporter())
        delegate.addLogRecordExporter(FakeLogRecordExporter())
        assertEquals(2, logger.errorMessages.size)
    }
}
