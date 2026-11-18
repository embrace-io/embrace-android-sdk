package io.embrace.android.embracesdk.internal.delivery.storage

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

internal class AttachmentStorageTest {

    @Test
    fun `a stored attachment is loaded with its id`() {
        val attachment = byteArrayOf(1, 2, 3)
        val loaded = checkNotNull(loadAttachment(stored(attachment)))
        assertArrayEquals(attachment, loaded.first)
        assertEquals(ID, loaded.second)
    }

    @Test
    fun `an attachment at the size limit is loaded`() {
        val attachment = ByteArray(MAX_ATTACHMENT_BYTES)
        val loaded = checkNotNull(loadAttachment(stored(attachment)))
        assertEquals(MAX_ATTACHMENT_BYTES, loaded.first.size)
        assertEquals(ID, loaded.second)
    }

    @Test
    fun `a file larger than any stored attachment returns null`() {
        assertNull(loadAttachment(stored(ByteArray(MAX_ATTACHMENT_BYTES + 1024))))
    }

    private fun stored(attachment: ByteArray): ByteArrayInputStream {
        val out = ByteArrayOutputStream()
        storeAttachment(out, attachment, ID)
        return ByteArrayInputStream(out.toByteArray())
    }

    private companion object {
        const val ID = "6c1a0b8e-6a3f-4b8e-9d4e-3f2a1c0b9e8d"
    }
}
