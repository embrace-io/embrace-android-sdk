package io.embrace.android.embracesdk.capture.memory

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import io.embrace.android.embracesdk.fakes.FakeMemoryService
import io.embrace.android.embracesdk.fakes.system.mockContext
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class ComponentCallbackServiceTest {

    private lateinit var service: ComponentCallbackService
    private lateinit var application: Application
    private lateinit var memoryService: FakeMemoryService
    private lateinit var ctx: Context

    @Before
    fun setup() {
        ctx = mockContext()
        application = mockk(relaxed = true) {
            every { applicationContext } returns ctx
        }
        memoryService = FakeMemoryService()
        every { application.registerActivityLifecycleCallbacks(any()) } returns Unit
    }

    @Before
    fun before() {
        clearAllMocks(
            answers = false,
            objectMocks = false,
            constructorMocks = false,
            staticMocks = false
        )

        service = ComponentCallbackService(
            application,
            memoryService,
            EmbLoggerImpl(),
        )
    }

    @Test
    fun `test we are adding lifecycle observer on constructor`() {
        verify(exactly = 1) { ctx.registerComponentCallbacks(service) }
    }

    @Test
    fun `verify on trim on level not running low does not do anything`() {
        service.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE)
        assertEquals(0, memoryService.callCount)
    }

    @Test
    fun `verify on trim on level running low triggers memory warning`() {
        service.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
        assertEquals(1, memoryService.callCount)
    }

    @Test
    fun `verify close cleans everything`() {
        service.close()
        verify(exactly = 1) { ctx.unregisterComponentCallbacks(service) }
    }
}
