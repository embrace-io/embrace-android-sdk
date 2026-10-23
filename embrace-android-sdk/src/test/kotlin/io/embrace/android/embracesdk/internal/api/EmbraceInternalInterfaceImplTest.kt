package io.embrace.android.embracesdk.internal.api

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeEnvelopeResourceSource
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fakes.FakeInternalTelemetryService
import io.embrace.android.embracesdk.fakes.OtelSdkMode
import io.embrace.android.embracesdk.fakes.behavior.FakeNetworkSpanForwardingBehavior
import io.embrace.android.embracesdk.fakes.injection.TestInitServices
import io.embrace.android.embracesdk.fakes.unimplemented
import io.embrace.android.embracesdk.internal.api.delegate.HostedSdkInternalInterfaceImpl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class EmbraceInternalInterfaceImplTest {

    private lateinit var internalImpl: HostedSdkInternalInterfaceImpl
    private lateinit var fakeClock: FakeClock
    private lateinit var initServices: TestInitServices
    private lateinit var fakeConfigService: FakeConfigService
    private lateinit var resourceSource: FakeEnvelopeResourceSource

    @Before
    fun setUp() {
        fakeClock = FakeClock(currentTime = beforeObjectInitTime)
        initServices = TestInitServices(clock = fakeClock, errorHandler = FakeErrorHandler(false), otelSdkMode = OtelSdkMode.COMPAT)
        fakeConfigService = FakeConfigService()
        resourceSource = FakeEnvelopeResourceSource()
        internalImpl = HostedSdkInternalInterfaceImpl(
            logsApi = unimplemented(),
            configService = fakeConfigService,
            resourceSource = resourceSource,
            instrumentationRegistry = unimplemented(),
            serializer = unimplemented(),
            rnBundleIdTracker = lazy { unimplemented() },
            hostedSdkVersionInfo = unimplemented(),
            errorHandler = FakeErrorHandler(),
            telemetryService = FakeInternalTelemetryService(),
        )
    }

    @Test
    fun `check isNetworkSpanForwardingEnabled`() {
        assertFalse(internalImpl.isNetworkSpanForwardingEnabled())
        fakeConfigService.networkSpanForwardingBehavior = FakeNetworkSpanForwardingBehavior(true)
        assertTrue(internalImpl.isNetworkSpanForwardingEnabled())
    }

    @Test
    fun `check resource source addition`() {
        internalImpl.addEnvelopeResource("foo", "bar")
        assertEquals("bar", resourceSource.customValues["foo"])
    }

    companion object {
        private val beforeObjectInitTime = System.currentTimeMillis() - 1
    }
}
