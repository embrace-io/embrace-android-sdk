package io.embrace.android.embracesdk.internal.anr

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.behavior.FakeAnrBehavior
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.payload.ThreadInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.Thread.MAX_PRIORITY
import java.lang.Thread.MIN_PRIORITY
import java.lang.Thread.NORM_PRIORITY
import java.lang.Thread.State.RUNNABLE
import java.lang.Thread.currentThread
import java.util.regex.Pattern

internal class ThreadInfoCollectorTest {

    private val highPrioThread = ThreadInfo(1, RUNNABLE, "thread-1", MAX_PRIORITY, emptyList(), 0)
    private val medPrioThread = ThreadInfo(2, RUNNABLE, "thread-2", NORM_PRIORITY, emptyList(), 0)
    private val lowPrioThread = ThreadInfo(3, RUNNABLE, "thread-3", MIN_PRIORITY, emptyList(), 0)

    private lateinit var configService: ConfigService
    private lateinit var threadInfoCollector: ThreadInfoCollector

    @Before
    fun setUp() {
        configService = FakeConfigService(
            anrBehavior = FakeAnrBehavior(
                allowPatternList = listOf(currentThread().name).map(Pattern::compile),
                blockPatternList = listOf("Finalizer").map(Pattern::compile),
                frameLimit = 5
            )
        )
        threadInfoCollector = ThreadInfoCollector(currentThread())
    }

    @Test
    fun testGetThreadsAllowList() {
        val threadName = currentThread().name
        val thread = threadInfoCollector.getAllowedThreads(configService).single()
        assertEquals(threadName, thread.name)
    }

    @Test
    fun testGetThreadsPriority() {
        assertEquals(1, threadInfoCollector.getAllowedThreads(configService).size)
    }

    @Test
    fun testGetAllowedThreads() {
        assertTrue(
            threadInfoCollector.getAllowedThreads(configService)
                .none { it.name == "Finalizer" }
        )
    }

    @Test
    fun testIsAllowedByPriority() {
        // 0 priority is always allowed
        assertTrue(
            threadInfoCollector.isAllowedByPriority(
                0,
                highPrioThread.priority
            )
        )
        assertTrue(
            threadInfoCollector.isAllowedByPriority(
                0,
                medPrioThread.priority
            )
        )
        assertTrue(
            threadInfoCollector.isAllowedByPriority(
                0,
                lowPrioThread.priority
            )
        )

        // check low priority boundaries
        assertTrue(
            threadInfoCollector.isAllowedByPriority(
                1,
                lowPrioThread.priority
            )
        )
        assertFalse(
            threadInfoCollector.isAllowedByPriority(
                2,
                lowPrioThread.priority
            )
        )

        // check medium priority boundaries
        assertTrue(
            threadInfoCollector.isAllowedByPriority(
                4,
                medPrioThread.priority
            )
        )
        assertTrue(
            threadInfoCollector.isAllowedByPriority(
                5,
                medPrioThread.priority
            )
        )
        assertFalse(
            threadInfoCollector.isAllowedByPriority(
                6,
                medPrioThread.priority
            )
        )

        // check high priority boundaries
        assertTrue(
            threadInfoCollector.isAllowedByPriority(
                9,
                highPrioThread.priority
            )
        )
        assertTrue(
            threadInfoCollector.isAllowedByPriority(
                10,
                highPrioThread.priority
            )
        )
    }

    @Test
    fun `verify truncation of ANR stacktrace respects the config`() {
        val thread = threadInfoCollector.getAllowedThreads(configService).single()
        val frames = checkNotNull(thread.lines)
        assertEquals(5, frames.size)
        assertTrue(thread.frameCount > frames.size)
    }
}
