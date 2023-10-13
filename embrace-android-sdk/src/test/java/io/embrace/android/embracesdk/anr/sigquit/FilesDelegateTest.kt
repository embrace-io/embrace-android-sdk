package io.embrace.android.embracesdk.anr.sigquit

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

internal class FilesDelegateTest {

    @Test
    fun testGetThreadsFileForCurrentProcess() {
        assertEquals(File("/proc/self/task"), FilesDelegate().getThreadsFileForCurrentProcess())
    }

    @Test
    fun testGetCommandFileForThread() {
        assertEquals(File("/proc/512/comm"), FilesDelegate().getCommandFileForThread("512"))
    }
}
