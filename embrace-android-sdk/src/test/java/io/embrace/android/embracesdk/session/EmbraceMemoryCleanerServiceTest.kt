package io.embrace.android.embracesdk.session

import io.embrace.android.embracesdk.fakes.FakeMemoryCleanerListener
import io.embrace.android.embracesdk.logging.EmbraceInternalErrorService
import io.embrace.android.embracesdk.utils.at
import io.mockk.clearAllMocks
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

internal class EmbraceMemoryCleanerServiceTest {

    companion object {
        private lateinit var exceptionService: EmbraceInternalErrorService

        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            exceptionService = mockk(relaxed = true)
        }

        @JvmStatic
        @AfterClass
        fun tearDown() {
            unmockkAll()
        }
    }

    private lateinit var service: EmbraceMemoryCleanerService

    @Before
    fun setUp() {
        clearAllMocks(
            answers = false,
            objectMocks = false,
            constructorMocks = false,
            staticMocks = false
        )

        service = EmbraceMemoryCleanerService()
    }

    @Test
    fun `test cleanServicesCollections clears service listeners`() {
        val listener = FakeMemoryCleanerListener()
        val listener2 = FakeMemoryCleanerListener()

        service.addListener(listener)
        service.addListener(listener2)

        service.cleanServicesCollections(exceptionService)

        assertEquals(1, listener.callCount)
        assertEquals(1, listener2.callCount)
    }

    @Test
    fun `test cleanServicesCollections clear listeners and catch exception`() {
        val listener1 = FakeMemoryCleanerListener()
        val listener2 = object : MemoryCleanerListener {
            override fun cleanCollections() = throw NullPointerException()
        }
        val listener3 = FakeMemoryCleanerListener()

        service.addListener(listener1)
        service.addListener(listener2)
        service.addListener(listener3)

        service.cleanServicesCollections(exceptionService)

        assertEquals(1, listener1.callCount)
        assertEquals(1, listener3.callCount)
    }

    @Test
    fun `test cleanServicesCollections clears Embrace public API`() {
        service.cleanServicesCollections(exceptionService)
        verify(exactly = 1) { exceptionService.resetExceptionErrorObject() }
    }

    @Test
    fun addListener() {
        val listener = FakeMemoryCleanerListener()
        val listener2 = FakeMemoryCleanerListener()

        service.addListener(listener)
        service.addListener(listener)
        service.addListener(listener2)

        assertEquals(2, service.listeners.size)
        assertEquals(listener, service.listeners.at(0))
        assertEquals(listener2, service.listeners.at(1))
    }
}
