package io.embrace.android.embracesdk.internal.capture.cpu

import io.embrace.android.embracesdk.fakes.FakeSharedObjectLoader
import io.embrace.android.embracesdk.internal.SharedObjectLoader
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class EmbraceCpuInfoDelegateTest {

    private val sharedObjectLoader: SharedObjectLoader = FakeSharedObjectLoader()
    private lateinit var logger: EmbLogger
    private lateinit var cpuInfoDelegate: EmbraceCpuInfoDelegate

    @Before
    fun before() {
        logger = EmbLoggerImpl()
        cpuInfoDelegate = EmbraceCpuInfoDelegate(sharedObjectLoader, logger)
    }

    @Test
    fun `check native library not loaded returns null`() {
        assertEquals(null, cpuInfoDelegate.getCpuName())
        assertEquals(null, cpuInfoDelegate.getEgl())
    }
}
