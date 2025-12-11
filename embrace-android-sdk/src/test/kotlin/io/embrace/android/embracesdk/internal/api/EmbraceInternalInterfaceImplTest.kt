package io.embrace.android.embracesdk.internal.api

import io.embrace.android.embracesdk.EmbraceImpl
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakeNetworkCaptureDataSource
import io.embrace.android.embracesdk.fakes.behavior.FakeNetworkSpanForwardingBehavior
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.api.delegate.EmbraceInternalInterfaceImpl
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.SocketException

internal class EmbraceInternalInterfaceImplTest {

    private lateinit var internalImpl: EmbraceInternalInterfaceImpl
    private lateinit var embraceImpl: EmbraceImpl
    private lateinit var fakeClock: FakeClock
    private lateinit var initModule: FakeInitModule
    private lateinit var fakeConfigService: FakeConfigService

    @Before
    fun setUp() {
        embraceImpl = mockk(relaxed = true)
        fakeClock = FakeClock(currentTime = beforeObjectInitTime)
        initModule = FakeInitModule(clock = fakeClock, logger = FakeEmbLogger(false))
        fakeConfigService = FakeConfigService()
        internalImpl = EmbraceInternalInterfaceImpl(
            embraceImpl,
            initModule,
            ::FakeNetworkCaptureDataSource,
            fakeConfigService,
        )
    }

    @Test
    fun `check isNetworkSpanForwardingEnabled`() {
        assertFalse(internalImpl.isNetworkSpanForwardingEnabled())
        fakeConfigService.networkSpanForwardingBehavior = FakeNetworkSpanForwardingBehavior(true)
        assertTrue(internalImpl.isNetworkSpanForwardingEnabled())
    }

    @Test
    fun `check logInternalError with exception`() {
        val expectedException = SocketException()
        internalImpl.logInternalError(expectedException)
        val logger = initModule.logger as FakeEmbLogger
        checkNotNull(logger.internalErrorMessages.single().throwable)
    }

    companion object {
        private val beforeObjectInitTime = System.currentTimeMillis() - 1
    }
}
