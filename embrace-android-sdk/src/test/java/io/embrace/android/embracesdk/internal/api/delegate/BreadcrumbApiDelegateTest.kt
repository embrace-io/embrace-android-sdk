package io.embrace.android.embracesdk.internal.api.delegate

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.FakeBreadcrumbService
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakeSessionOrchestrator
import io.embrace.android.embracesdk.fakes.FakeTelemetryService
import io.embrace.android.embracesdk.fakes.fakeModuleInitBootstrapper
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class BreadcrumbApiDelegateTest {

    private lateinit var delegate: BreadcrumbApiDelegate
    private lateinit var breadcrumbService: FakeBreadcrumbService
    private lateinit var orchestrator: FakeSessionOrchestrator

    @Before
    fun setUp() {
        val bootstrapper = fakeModuleInitBootstrapper()
        bootstrapper.init(ApplicationProvider.getApplicationContext(), Embrace.AppFramework.NATIVE, 0)
        breadcrumbService = bootstrapper.dataCaptureServiceModule.breadcrumbService as FakeBreadcrumbService
        orchestrator = bootstrapper.sessionModule.sessionOrchestrator as FakeSessionOrchestrator

        val sdkCallChecker = SdkCallChecker(FakeEmbLogger(), FakeTelemetryService())
        sdkCallChecker.started.set(true)
        delegate = BreadcrumbApiDelegate(bootstrapper, sdkCallChecker)
    }

    @Test
    fun addBreadcrumb() {
        delegate.addBreadcrumb("test")
        assertEquals("test", breadcrumbService.customCalls.single())
    }
}
