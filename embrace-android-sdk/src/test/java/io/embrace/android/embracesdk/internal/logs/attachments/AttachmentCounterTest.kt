package io.embrace.android.embracesdk.internal.logs.attachments

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class AttachmentCounterTest {

    @Test
    fun `limit exceeded`() {
        val counter = AttachmentCounter()
        assertLimitRespected(counter)

        counter.cleanCollections()
        assertLimitRespected(counter)
    }

    private fun assertLimitRespected(counter: AttachmentCounter) {
        repeat(5) {
            assertTrue(counter.incrementAndCheckAttachmentLimit())
        }
        assertFalse(counter.incrementAndCheckAttachmentLimit())
    }
}
