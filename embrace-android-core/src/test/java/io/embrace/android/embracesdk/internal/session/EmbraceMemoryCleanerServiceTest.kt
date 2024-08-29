package io.embrace.android.embracesdk.internal.session

import io.embrace.android.embracesdk.fakes.FakeMemoryCleanerListener
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.internal.utils.at
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class EmbraceMemoryCleanerServiceTest {

    private lateinit var service: EmbraceMemoryCleanerService

    @Before
    fun setUp() {
        service = EmbraceMemoryCleanerService(EmbLoggerImpl())
    }

    @Test
    fun `test cleanServicesCollections clears service listeners`() {
        val listener = FakeMemoryCleanerListener()
        val listener2 = FakeMemoryCleanerListener()

        service.addListener(listener)
        service.addListener(listener2)

        service.cleanServicesCollections()

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

        service.cleanServicesCollections()

        assertEquals(1, listener1.callCount)
        assertEquals(1, listener3.callCount)
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
