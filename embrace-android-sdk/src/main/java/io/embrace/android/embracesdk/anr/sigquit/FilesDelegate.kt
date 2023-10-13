package io.embrace.android.embracesdk.anr.sigquit

import java.io.File

internal class FilesDelegate {
    fun getThreadsFileForCurrentProcess(): File {
        return File("/proc/self/task")
    }

    fun getCommandFileForThread(threadId: String): File {
        return File("/proc/$threadId/comm")
    }
}
