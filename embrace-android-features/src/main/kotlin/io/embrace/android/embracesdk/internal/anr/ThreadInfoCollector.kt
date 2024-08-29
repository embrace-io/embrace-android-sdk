package io.embrace.android.embracesdk.internal.anr

import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.payload.ThreadInfo
import java.util.regex.Pattern

internal class ThreadInfoCollector(
    private val targetThread: Thread
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
        val threads = getAllowedThreads(configService)
        val sanitizedThreads = mutableListOf<ThreadInfo>()

        threads.forEach { threadInfo ->
            // Compares every thread with the last known thread state via hashcode. If hashcode changed
            // it should be added to the anrInfo list and also the currentAnrInfoState must be updated.
            val threadId = threadInfo.threadId
            val cache: ThreadInfo? = currentStacktraceStates[threadId]

            // only serialize if the previous stacktrace doesn't match.
            if (cache == null || threadInfo != cache) {
                sanitizedThreads.add(threadInfo)
                currentStacktraceStates[threadId] = threadInfo
            }
        }
        return sanitizedThreads
    }

    /**
     * Filter the thread list based on allow/block list get by config.
     *
     * @return filtered threads
     */

    fun getAllowedThreads(configService: ConfigService): Set<ThreadInfo> {
        val allowed: MutableSet<ThreadInfo> = HashSet()
        val anrBehavior = configService.anrBehavior
        val blockList = anrBehavior.blockPatternList
        val allowList = anrBehavior.allowPatternList
        val anrStacktracesMaxLength = anrBehavior.getStacktraceFrameLimit()
        val priority = anrBehavior.getMinThreadPriority()

        if (anrBehavior.shouldCaptureMainThreadOnly()) {
            val threadInfo = ThreadInfo.ofThread(
                targetThread,
                targetThread.stackTrace,
                anrStacktracesMaxLength
            )
            allowed.add(threadInfo)
        } else {
            Thread.getAllStackTraces().forEach { (thread, stacktrace) ->
                val allowedByPriority = isAllowedByPriority(priority, thread.priority)
                val allowedByLists = isAllowedByLists(allowList, blockList, thread.name)

                if (allowedByPriority && allowedByLists) {
                    allowed.add(
                        ThreadInfo.ofThread(
                            thread,
                            stacktrace,
                            anrStacktracesMaxLength
                        )
                    )
                }
            }
        }
        return allowed
    }

    private fun isAllowedByLists(
        allowList: List<Pattern>?,
        blockList: List<Pattern>?,
        name: String
    ): Boolean {
        return matchesList(allowList, name) || !matchesList(blockList, name)
    }

    private fun matchesList(allowed: List<Pattern>?, name: String): Boolean {
        if (allowed == null || allowed.isEmpty()) {
            return false
        }
        return allowed.any { pattern ->
            pattern.matcher(name).find()
        }
    }

    fun isAllowedByPriority(priority: Int, observedPriority: Int): Boolean = observedPriority >= priority
}
