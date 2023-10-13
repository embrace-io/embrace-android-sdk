package io.embrace.android.embracesdk.anr.sigquit

import io.embrace.android.embracesdk.logging.InternalEmbraceLogger

internal class FindGoogleThread(
    private val logger: InternalEmbraceLogger,
    private val getThreadsInCurrentProcess: GetThreadsInCurrentProcess,
    private val getThreadCommand: GetThreadCommand
) {

    /**
     * Search the app's threads to find the Google ANR watcher thread.
     *
     * @return the thread ID for the Google ANR watcher thread, or 0 if one cannot be found
     */

    operator fun invoke(): Int {
        logger.logInfo("Searching for Google thread ID for ANR detection")
        val threads = getThreadsInCurrentProcess()
        for (threadId in threads) {
            val command = getThreadCommand(threadId)
            if (command.startsWith("Signal Catcher")) {
                return threadId.toIntOrNull() ?: 0
            }
        }
        return 0
    }
}
