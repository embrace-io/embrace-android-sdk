package io.embrace.android.embracesdk.logging

import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

internal class EmbLoggerImplTest {

    private lateinit var logger: FakeEmbLogger

    @Before
    fun setUp() {
        logger = FakeEmbLogger()
    }

    @Test
    fun `a log with the same severity as the threshold triggers logging actions`() {
        // when log is called with the same severity
        logger.logInfo("test")

        // then logger actions are triggered
        val msg = logger.infoMessages.single()
        assertEquals("test", msg.msg)
        assertNull(msg.throwable)
    }

    @Test
    fun `a log with a higher severity than the threshold triggers logging actions`() {
        // when log is called with a higher severity
        val throwable = Exception()
        logger.logWarning("test", throwable)

        // then logger actions are triggered
        val msg = logger.warningMessages.single()
        assertEquals("test", msg.msg)
        assertEquals(throwable, msg.throwable)
    }
}
