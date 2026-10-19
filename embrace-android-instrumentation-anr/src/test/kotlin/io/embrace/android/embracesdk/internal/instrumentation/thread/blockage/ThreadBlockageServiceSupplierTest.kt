package io.embrace.android.embracesdk.internal.instrumentation.thread.blockage

import android.os.Looper
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.internal.config.resolved.AutoDataCaptureConfig
import io.embrace.android.embracesdk.internal.config.resolved.EmbraceConfig
import io.embrace.android.embracesdk.internal.config.resolved.ThreadBlockageConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

internal class ThreadBlockageServiceSupplierTest {

    @Before
    fun setUp() {
        mockkStatic(Looper::class)
        every { Looper.getMainLooper() } returns mockk { every { thread } returns Thread.currentThread() }
    }

    @After
    fun tearDown() {
        unmockkStatic(Looper::class)
    }

    @Test
    fun testDefaultImplementations() {
        val service = createThreadBlockageService(
            FakeInstrumentationArgs(
                configService = FakeConfigService(),
            ),
        )
        assertNotNull(service)
    }

    @Test
    fun testBehaviorDisabled() {
        val service = createThreadBlockageService(
            FakeInstrumentationArgs(
                configService = FakeConfigService(
                    config = EmbraceConfig(autoDataCapture = { AutoDataCaptureConfig(threadBlockageCaptureEnabled = { false }) }),
                ),
            ),
        )
        assertNull(service)
    }

    @Test
    fun testRemoteBehaviorDisabled() {
        val service = createThreadBlockageService(
            FakeInstrumentationArgs(
                configService = FakeConfigService(
                    config = EmbraceConfig(threadBlockage = { ThreadBlockageConfig(captureEnabled = { false }) }),
                ),
            ),
        )
        assertNull(service)
    }
}
