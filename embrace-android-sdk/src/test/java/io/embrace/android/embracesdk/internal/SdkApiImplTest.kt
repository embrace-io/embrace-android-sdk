package io.embrace.android.embracesdk.internal

import io.embrace.android.embracesdk.fakes.FakeClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class SdkApiImplTest {

    private lateinit var fakeClock: FakeClock
    private lateinit var sdkApi: SdkApiImpl

    @Before
    fun setup() {
        fakeClock = FakeClock(currentTime = beforeObjectInitTime)
        sdkApi = SdkApiImpl(fakeClock)
    }

    @Test
    fun `check usage of SDK time`() {
        assertEquals(beforeObjectInitTime, sdkApi.getSdkCurrentTime())
        assertTrue(sdkApi.getSdkCurrentTime() < System.currentTimeMillis())
        fakeClock.tick(10L)
        assertEquals(fakeClock.now(), sdkApi.getSdkCurrentTime())
    }

    @Test
    fun `check default implementation`() {
        assertTrue(beforeObjectInitTime < default.getSdkCurrentTime())
        assertTrue(default.getSdkCurrentTime() <= System.currentTimeMillis())
    }

    companion object {
        val beforeObjectInitTime = System.currentTimeMillis() - 1
    }
}
