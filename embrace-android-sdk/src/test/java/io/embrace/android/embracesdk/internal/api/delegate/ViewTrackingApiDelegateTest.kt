package io.embrace.android.embracesdk.internal.api.delegate

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakeSessionOrchestrator
import io.embrace.android.embracesdk.fakes.FakeSessionPropertiesService
import io.embrace.android.embracesdk.fakes.FakeTelemetryService
import io.embrace.android.embracesdk.fakes.fakeModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.spans.SpanSink
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class ViewTrackingApiDelegateTest {

    private lateinit var delegate: ViewTrackingApiDelegate
    private lateinit var orchestrator: FakeSessionOrchestrator
    private lateinit var sessionPropertiesService: FakeSessionPropertiesService
    private lateinit var spanSink: SpanSink
    private lateinit var logger: FakeEmbLogger

    @Before
    fun setUp() {
        val moduleInitBootstrapper = fakeModuleInitBootstrapper()
        moduleInitBootstrapper.init(ApplicationProvider.getApplicationContext(), AppFramework.NATIVE, 0)
        orchestrator = moduleInitBootstrapper.sessionModule.sessionOrchestrator as FakeSessionOrchestrator
        sessionPropertiesService = moduleInitBootstrapper.essentialServiceModule.sessionPropertiesService as FakeSessionPropertiesService
        spanSink = moduleInitBootstrapper.openTelemetryModule.spanSink
        logger = moduleInitBootstrapper.logger as FakeEmbLogger

        val sdkCallChecker = SdkCallChecker(FakeEmbLogger(), FakeTelemetryService())
        sdkCallChecker.started.set(true)
        delegate = ViewTrackingApiDelegate(moduleInitBootstrapper, sdkCallChecker)
    }

    @Test
    fun registerComposeActivityListener() {
        delegate.registerComposeActivityListener(ApplicationProvider.getApplicationContext())
        assertEquals(1, logger.errorMessages.size)
    }

    @Test
    fun logRnView() {
        delegate.logRnView("test")
        assertEquals(1, logger.warningMessages.size)
    }
}
