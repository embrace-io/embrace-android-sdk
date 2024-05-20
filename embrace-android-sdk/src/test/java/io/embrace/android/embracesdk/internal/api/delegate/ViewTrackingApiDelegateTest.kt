package io.embrace.android.embracesdk.internal.api.delegate

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.FakeBreadcrumbService
import io.embrace.android.embracesdk.FakeSessionPropertiesService
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakeSessionOrchestrator
import io.embrace.android.embracesdk.fakes.FakeTelemetryService
import io.embrace.android.embracesdk.fakes.fakeModuleInitBootstrapper
import io.embrace.android.embracesdk.payload.TapBreadcrumb
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class ViewTrackingApiDelegateTest {

    private lateinit var delegate: ViewTrackingApiDelegate
    private lateinit var orchestrator: FakeSessionOrchestrator
    private lateinit var sessionPropertiesService: FakeSessionPropertiesService
    private lateinit var breadcrumbService: FakeBreadcrumbService
    private lateinit var logger: FakeEmbLogger

    @Before
    fun setUp() {
        val moduleInitBootstrapper = fakeModuleInitBootstrapper()
        moduleInitBootstrapper.init(ApplicationProvider.getApplicationContext(), Embrace.AppFramework.NATIVE, 0)
        orchestrator = moduleInitBootstrapper.sessionModule.sessionOrchestrator as FakeSessionOrchestrator
        sessionPropertiesService = moduleInitBootstrapper.sessionModule.sessionPropertiesService as FakeSessionPropertiesService
        breadcrumbService = moduleInitBootstrapper.dataCaptureServiceModule.breadcrumbService as FakeBreadcrumbService
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
    fun startView() {
        delegate.startView("test")
        assertEquals("test", breadcrumbService.startViewCalls.single())
    }

    @Test
    fun endView() {
        delegate.endView("test")
        assertEquals("test", breadcrumbService.endViewCalls.single())
    }

    @Test
    fun logTap() {
        delegate.logTap(
            Pair(1f, 2f),
            "test",
            TapBreadcrumb.TapBreadcrumbType.TAP,
        )
        assertEquals("test", breadcrumbService.tapCalls.single())
    }

    @Test
    fun logRnAction() {
        delegate.logRnAction("test", 5, 10, emptyMap(), 0, "test")
        assertEquals("test", breadcrumbService.rnActionCalls.single())
    }

    @Test
    fun logRnView() {
        delegate.logRnView("test")
        assertEquals(1, logger.warningMessages.size)
    }
}
