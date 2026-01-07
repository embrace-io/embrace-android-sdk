package io.embrace.android.embracesdk.internal.api

import io.embrace.android.embracesdk.EmbraceImpl
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeEnvelopeResourceSource
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.behavior.FakeNetworkSpanForwardingBehavior
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.api.delegate.EmbraceInternalInterfaceImpl
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class EmbraceInternalInterfaceImplTest {

    private lateinit var internalImpl: EmbraceInternalInterfaceImpl
    private lateinit var embraceImpl: EmbraceImpl
    private lateinit var fakeClock: FakeClock
    private lateinit var initModule: FakeInitModule
    private lateinit var fakeConfigService: FakeConfigService
    private lateinit var resourceSource: FakeEnvelopeResourceSource

    @Before
    fun setUp() {
        embraceImpl = mockk(relaxed = true)
        fakeClock = FakeClock(currentTime = beforeObjectInitTime)
        initModule = FakeInitModule(clock = fakeClock, logger = FakeInternalLogger(false))
        fakeConfigService = FakeConfigService()
        resourceSource = FakeEnvelopeResourceSource()
        internalImpl = EmbraceInternalInterfaceImpl(fakeConfigService, resourceSource)
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
