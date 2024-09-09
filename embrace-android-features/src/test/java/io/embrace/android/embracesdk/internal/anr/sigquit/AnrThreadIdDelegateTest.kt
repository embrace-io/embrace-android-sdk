package io.embrace.android.embracesdk.internal.anr.sigquit

import org.junit.Assert.assertEquals
import org.junit.Test

internal class AnrThreadIdDelegateTest {

    @Test
    fun findGoogleAnrThread() {
        val delegate = AnrThreadIdDelegate()
        val threadId = delegate.findGoogleAnrThread()
        assertEquals(0, threadId)
    }
}
