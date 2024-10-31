package io.embrace.android.embracesdk.internal.anr

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.behavior.FakeAnrBehavior
import io.embrace.android.embracesdk.internal.config.ConfigService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.Thread.currentThread

internal class ThreadInfoCollectorTest {

    private lateinit var configService: ConfigService
    private lateinit var threadInfoCollector: ThreadInfoCollector

    @Before
    fun setUp() {
        configService = FakeConfigService(
            anrBehavior = FakeAnrBehavior(frameLimit = 5)
        )
        threadInfoCollector = ThreadInfoCollector(currentThread())
    }

    @Test
    fun `verify truncation of ANR stacktrace respects the config`() {
        val thread = threadInfoCollector.getMainThread(configService)
        val frames = checkNotNull(thread.lines)
        assertEquals(5, frames.size)
        assertTrue(thread.frameCount > frames.size)
    }
}
