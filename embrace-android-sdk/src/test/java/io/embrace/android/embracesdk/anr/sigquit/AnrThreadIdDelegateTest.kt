package io.embrace.android.embracesdk.anr.sigquit

import io.embrace.android.embracesdk.logging.EmbLoggerImpl
import org.junit.Assert.assertEquals
import org.junit.Test

internal class AnrThreadIdDelegateTest {

    @Test
    fun findGoogleAnrThread() {
        val delegate = AnrThreadIdDelegate(EmbLoggerImpl())
        val threadId = delegate.findGoogleAnrThread()
        assertEquals(0, threadId)
    }
}
