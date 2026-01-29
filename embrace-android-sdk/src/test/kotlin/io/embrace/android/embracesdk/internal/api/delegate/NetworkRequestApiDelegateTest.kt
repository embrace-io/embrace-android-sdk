package io.embrace.android.embracesdk.internal.api.delegate

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.FakeTelemetryService
import io.embrace.android.embracesdk.fakes.behavior.FakeNetworkSpanForwardingBehavior
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class NetworkRequestApiDelegateTest {

    private lateinit var delegate: NetworkRequestApiDelegate
    private lateinit var configService: FakeConfigService

    @Before
    fun setUp() {
        val moduleInitBootstrapper = ModuleInitBootstrapper(
            FakeInitModule(),
            configServiceSupplier = { _, _, _, _ ->
                FakeConfigService()
            },
        )
        moduleInitBootstrapper.init(ApplicationProvider.getApplicationContext())

        configService = moduleInitBootstrapper.configService as FakeConfigService
        val sdkCallChecker = SdkCallChecker(FakeInternalLogger(), FakeTelemetryService())
        sdkCallChecker.started.set(true)
        delegate = NetworkRequestApiDelegate(moduleInitBootstrapper, sdkCallChecker)
    }

    @Test
    fun testGenerateW3cTraceparentEnabled() {
        configService.networkSpanForwardingBehavior = FakeNetworkSpanForwardingBehavior(true)
        assertNotNull(delegate.generateW3cTraceparent())
    }

    @Test
    fun testGenerateW3cTraceparent() {
        assertNull(delegate.generateW3cTraceparent())
    }
}
