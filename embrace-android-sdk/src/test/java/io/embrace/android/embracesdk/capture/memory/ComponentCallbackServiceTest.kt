package io.embrace.android.embracesdk.capture.memory

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import io.mockk.Called
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

internal class ComponentCallbackServiceTest {

    private lateinit var service: ComponentCallbackService
    private lateinit var mockApplication: Application
    private lateinit var mockMemoryService: MemoryService
    private lateinit var ctx: Context

    @Before
    fun setup() {
        ctx = mockk(relaxed = true)
        mockApplication = mockk(relaxed = true) {
            every { applicationContext } returns ctx
        }
        mockMemoryService = mockk()
        every { mockApplication.registerActivityLifecycleCallbacks(any()) } returns Unit
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
            mockApplication,
            mockMemoryService
        )
    }

    @Test
    fun `test we are adding lifecycle observer on constructor`() {
        verify(exactly = 1) { ctx.registerComponentCallbacks(service) }
    }

    @Test
    fun `verify on trim on level not running low does not do anything`() {
        service.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE)
        verify { mockMemoryService wasNot Called }
    }

    @Test
    fun `verify on trim on level running low triggers memory warning`() {
        service.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
        verify(exactly = 1) { mockMemoryService.onMemoryWarning() }
    }

    @Test
    fun `verify close cleans everything`() {
        service.close()
        verify(exactly = 1) { ctx.unregisterComponentCallbacks(service) }
    }
}
