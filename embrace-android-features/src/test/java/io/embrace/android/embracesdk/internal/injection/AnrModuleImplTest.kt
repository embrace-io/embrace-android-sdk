package io.embrace.android.embracesdk.internal.injection

import android.os.Looper
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.behavior.FakeAutoDataCaptureBehavior
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

internal class AnrModuleImplTest {

    @Before
    fun setUp() {
        mockkStatic(Looper::class)
        every { Looper.getMainLooper() } returns mockk(relaxed = true)
    }

    @Test
    fun testDefaultImplementations() {
        val module = AnrModuleImpl(
            FakeInitModule(),
            FakeConfigService(),
            FakeWorkerThreadModule()
        )
        assertNotNull(module.anrService)
        assertNotNull(module.anrOtelMapper)
    }

    @Test
    fun testBehaviorDisabled() {
        val module = AnrModuleImpl(
            FakeInitModule(),
            FakeConfigService(
                autoDataCaptureBehavior = FakeAutoDataCaptureBehavior(anrServiceEnabled = false)
            ),
            FakeWorkerThreadModule()
        )
        assertNull(module.anrService)
        assertNull(module.anrOtelMapper)
    }
}
