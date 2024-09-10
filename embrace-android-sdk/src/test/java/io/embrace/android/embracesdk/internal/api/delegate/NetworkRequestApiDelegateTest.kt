package io.embrace.android.embracesdk.internal.api.delegate

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakeNetworkLoggingService
import io.embrace.android.embracesdk.fakes.FakeSessionOrchestrator
import io.embrace.android.embracesdk.fakes.FakeTelemetryService
import io.embrace.android.embracesdk.fakes.createNetworkSpanForwardingBehavior
import io.embrace.android.embracesdk.fakes.fakeModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.config.remote.NetworkSpanForwardingRemoteConfig
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.embrace.android.embracesdk.network.http.HttpMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class NetworkRequestApiDelegateTest {

    private lateinit var delegate: NetworkRequestApiDelegate
    private lateinit var configService: FakeConfigService
    private lateinit var networkLoggingService: FakeNetworkLoggingService
    private lateinit var orchestrator: FakeSessionOrchestrator

    @Before
    fun setUp() {
        val moduleInitBootstrapper = fakeModuleInitBootstrapper()
        moduleInitBootstrapper.init(ApplicationProvider.getApplicationContext(), AppFramework.NATIVE, 0)
        configService = moduleInitBootstrapper.configModule.configService as FakeConfigService
        networkLoggingService = moduleInitBootstrapper.logModule.networkLoggingService as FakeNetworkLoggingService
        orchestrator = moduleInitBootstrapper.sessionOrchestrationModule.sessionOrchestrator as FakeSessionOrchestrator

        val sdkCallChecker = SdkCallChecker(FakeEmbLogger(), FakeTelemetryService())
        sdkCallChecker.started.set(true)
        delegate = NetworkRequestApiDelegate(moduleInitBootstrapper, sdkCallChecker)
    }

    @Test
    fun `record network request`() {
        val request = EmbraceNetworkRequest.fromCompletedRequest(
            "https://www.google.com",
            HttpMethod.GET,
            100,
            200,
            4,
            29,
            200
        )
        delegate.recordNetworkRequest(request)
        assertEquals(1, orchestrator.stateChangeCount)
        assertEquals(request, networkLoggingService.requests.single())
    }

    @Test
    fun `test trace id header`() {
        assertEquals("x-emb-trace-id", delegate.traceIdHeader)
    }

    @Test
    fun testGenerateW3cTraceparentEnabled() {
        configService.networkSpanForwardingBehavior = createNetworkSpanForwardingBehavior {
            NetworkSpanForwardingRemoteConfig(100f)
        }
        assertNotNull(delegate.generateW3cTraceparent())
    }

    @Test
    fun testGenerateW3cTraceparent() {
        assertNull(delegate.generateW3cTraceparent())
    }
}
