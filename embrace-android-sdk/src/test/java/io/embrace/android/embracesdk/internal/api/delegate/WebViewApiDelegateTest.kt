package io.embrace.android.embracesdk.internal.api.delegate

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.FakeBreadcrumbService
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakeSessionOrchestrator
import io.embrace.android.embracesdk.fakes.FakeTelemetryService
import io.embrace.android.embracesdk.fakes.FakeWebViewService
import io.embrace.android.embracesdk.fakes.fakeModuleInitBootstrapper
import io.embrace.android.embracesdk.fakes.injection.FakeDataCaptureServiceModule
import io.embrace.android.embracesdk.internal.payload.AppFramework
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class WebViewApiDelegateTest {

    private lateinit var delegate: WebViewApiDelegate
    private lateinit var breadcrumbService: FakeBreadcrumbService
    private lateinit var webviewService: FakeWebViewService
    private lateinit var orchestrator: FakeSessionOrchestrator

    @Before
    fun setUp() {
        val bootstrapper = fakeModuleInitBootstrapper(
            dataCaptureServiceModuleSupplier = { _, _, _, _, _, _, _ ->
                FakeDataCaptureServiceModule(webviewService = FakeWebViewService())
            }
        )
        bootstrapper.init(ApplicationProvider.getApplicationContext(), AppFramework.NATIVE, 0)
        orchestrator = bootstrapper.sessionModule.sessionOrchestrator as FakeSessionOrchestrator
        breadcrumbService = bootstrapper.dataCaptureServiceModule.breadcrumbService as FakeBreadcrumbService
        webviewService = bootstrapper.dataCaptureServiceModule.webviewService as FakeWebViewService

        val sdkCallChecker = SdkCallChecker(FakeEmbLogger(), FakeTelemetryService())
        sdkCallChecker.started.set(true)
        delegate = WebViewApiDelegate(bootstrapper, sdkCallChecker)
    }

    @Test
    fun logWebView() {
        delegate.logWebView("test")
        assertEquals("test", breadcrumbService.webviewCalls.single())
    }

    @Test
    fun trackWebViewPerformance() {
        delegate.trackWebViewPerformance("test", "message")
        assertEquals("test", webviewService.tags.single())
    }
}
