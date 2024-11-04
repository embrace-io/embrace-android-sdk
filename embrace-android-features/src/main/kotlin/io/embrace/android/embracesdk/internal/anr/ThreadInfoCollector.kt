package io.embrace.android.embracesdk.internal.anr

import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.payload.ThreadInfo

internal class ThreadInfoCollector(
    private val targetThread: Thread,
) {

    private val currentStacktraceStates: MutableMap<Long, ThreadInfo> = HashMap()

    /**
     * Clears the stacktrace cache for all threads.
     */
    fun clearStacktraceCache(): Unit = currentStacktraceStates.clear()

    /**
     * Captures the thread traces required for the given sample.
     */
    fun captureSample(configService: ConfigService): List<ThreadInfo> {
        val threadInfo = getMainThread(configService)
        val sanitizedThreads = mutableListOf<ThreadInfo>()

        // Compares main thread with the last known thread state via hashcode. If hashcode changed
        // it should be added to the anrInfo list and also the currentAnrInfoState must be updated.
        val threadId = threadInfo.threadId
        val cache: ThreadInfo? = currentStacktraceStates[threadId]

        // only serialize if the previous stacktrace doesn't match.
        if (cache == null || threadInfo != cache) {
            sanitizedThreads.add(threadInfo)
            currentStacktraceStates[threadId] = threadInfo
        }
        return sanitizedThreads
    }

    /**
     * Filter the thread list based on allow/block list get by config.
     *
     * @return filtered threads
     */
    fun getMainThread(configService: ConfigService): ThreadInfo = ThreadInfo.ofThread(
        targetThread,
        targetThread.stackTrace,
        configService.anrBehavior.getStacktraceFrameLimit()
    )
}
