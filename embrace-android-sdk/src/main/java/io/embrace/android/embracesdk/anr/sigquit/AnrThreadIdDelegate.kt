package io.embrace.android.embracesdk.anr.sigquit

import io.embrace.android.embracesdk.logging.EmbLogger
import java.io.File

internal class AnrThreadIdDelegate(
    private val logger: EmbLogger
) {

    /**
     * Search the app's threads to find the Google ANR watcher thread.
     *
     * @return the thread ID for the Google ANR watcher thread, or 0 if one cannot be found
     */
    fun findGoogleAnrThread(): Int {
        logger.logInfo("Searching for Google thread ID for ANR detection")

        val anrThreadId = getThreadIdsInCurrentProcess().firstOrNull {
            readThreadCommand(it).startsWith("Signal Catcher")
        }
        return anrThreadId?.toIntOrNull() ?: 0
    }

    /**
     * Get command name associated with thread id
     *
     * @return a command name, or empty if none found
     */
    private fun readThreadCommand(threadId: String): String {
        val file = File("/proc/$threadId/comm")
        return try {
            file.readText()
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Get threads associated with the current process
     *
     * @return a list of numerical thread ids
     */
    private fun getThreadIdsInCurrentProcess(): List<String> {
        val dir = File("/proc/self/task")
        return try {
            dir.listFiles()?.map { it.name } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
