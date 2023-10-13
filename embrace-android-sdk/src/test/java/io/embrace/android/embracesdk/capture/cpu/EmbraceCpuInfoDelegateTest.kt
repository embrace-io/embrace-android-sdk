package io.embrace.android.embracesdk.capture.cpu

import io.embrace.android.embracesdk.internal.SharedObjectLoader
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test

internal class EmbraceCpuInfoDelegateTest {

    private val mockSharedObjectLoader: SharedObjectLoader = mockk(relaxed = true)
    private lateinit var logger: InternalEmbraceLogger
    private lateinit var cpuInfoDelegate: EmbraceCpuInfoDelegate

    @Before
    fun before() {
        logger = InternalEmbraceLogger()
        cpuInfoDelegate = EmbraceCpuInfoDelegate(mockSharedObjectLoader, logger)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `check native library not loaded returns null`() {
        every { mockSharedObjectLoader.loadEmbraceNative() } returns false

        Assert.assertEquals(null, cpuInfoDelegate.getCpuName())
        Assert.assertEquals(null, cpuInfoDelegate.getElg())
    }
}
