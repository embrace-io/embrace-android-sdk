package io.embrace.android.embracesdk.anr.sigquit

import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import org.junit.Assert.assertEquals
import org.junit.Test

internal class AnrThreadIdDelegateTest {

    @Test
    fun findGoogleAnrThread() {
        val delegate = AnrThreadIdDelegate(InternalEmbraceLogger())
        val threadId = delegate.findGoogleAnrThread()
        assertEquals(0, threadId)
    }
}
