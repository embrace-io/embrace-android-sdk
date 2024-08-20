package io.embrace.android.embracesdk.internal.capture.cpu

import io.embrace.android.embracesdk.internal.SharedObjectLoader
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class EmbraceCpuInfoDelegateTest {

    private val mockSharedObjectLoader: SharedObjectLoader = mockk(relaxed = true)
    private lateinit var logger: EmbLogger
    private lateinit var cpuInfoDelegate: EmbraceCpuInfoDelegate

    @Before
    fun before() {
        logger = EmbLoggerImpl()
        cpuInfoDelegate = EmbraceCpuInfoDelegate(mockSharedObjectLoader, logger)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `check native library not loaded returns null`() {
        every { mockSharedObjectLoader.loadEmbraceNative() } returns false

        assertEquals(null, cpuInfoDelegate.getCpuName())
        assertEquals(null, cpuInfoDelegate.getEgl())
    }
}
