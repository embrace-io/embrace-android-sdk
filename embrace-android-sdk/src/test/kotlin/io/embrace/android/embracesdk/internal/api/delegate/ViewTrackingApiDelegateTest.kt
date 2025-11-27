package io.embrace.android.embracesdk.internal.api.delegate

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeConfigModule
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakeTelemetryService
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class ViewTrackingApiDelegateTest {

    private lateinit var delegate: ViewTrackingApiDelegate

    @Before
    fun setUp() {
        val moduleInitBootstrapper = ModuleInitBootstrapper(
            FakeInitModule(),
            configModuleSupplier = { _, _, _, _ ->
                FakeConfigModule()
            },
        )
        moduleInitBootstrapper.init(ApplicationProvider.getApplicationContext())

        val sdkCallChecker = SdkCallChecker(FakeEmbLogger(), FakeTelemetryService())
        sdkCallChecker.started.set(true)
        delegate = ViewTrackingApiDelegate(moduleInitBootstrapper, sdkCallChecker)
    }

    @Test
    fun logRnView() {
        delegate.logRnView("test")
    }
}
