package io.embrace.android.embracesdk.anr

import io.embrace.android.embracesdk.anr.sigquit.FindGoogleThread
import io.embrace.android.embracesdk.anr.sigquit.GetThreadCommand
import io.embrace.android.embracesdk.anr.sigquit.GetThreadsInCurrentProcess
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

internal class FindGoogleThreadTest {

    private val testThread = "12423"
    private val anotherTestThread = "13215"

    private val mockLogger = mockk<InternalEmbraceLogger>(relaxed = true)
    private val mockGetThreadsInCurrentProcess = mockk<GetThreadsInCurrentProcess>()
    private val mockGetThreadCommand = mockk<GetThreadCommand>()

    private val findGoogleThread =
        FindGoogleThread(mockLogger, mockGetThreadsInCurrentProcess, mockGetThreadCommand)

    @Test
    fun `return 0 when there are no threads in current process`() {
        every { mockGetThreadsInCurrentProcess() } returns emptyList()

        val googleThread = findGoogleThread()

        assertEquals(0, googleThread)
    }

    @Test
    fun `return 0 when there are no commands for the threads`() {
        every { mockGetThreadsInCurrentProcess() } returns listOf(testThread, anotherTestThread)
        every { mockGetThreadCommand(any()) } returns ""

        val googleThread = findGoogleThread()

        assertEquals(0, googleThread)
    }

    @Test
    fun `return second thread when it has the correct command`() {
        every { mockGetThreadsInCurrentProcess() } returns listOf(testThread, anotherTestThread)
        every { mockGetThreadCommand(anotherTestThread) } returns "Signal Catcher"
        every { mockGetThreadCommand(testThread) } returns "Not Signal Catcher"

        val googleThread = findGoogleThread()

        assertEquals(anotherTestThread.toInt(), googleThread)
    }
}
