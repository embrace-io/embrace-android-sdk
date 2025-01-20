package io.embrace.android.embracesdk.internal.logs.attachments

import io.embrace.android.embracesdk.internal.logs.attachments.Attachment.EmbraceHosted
import io.embrace.android.embracesdk.internal.logs.attachments.Attachment.UserHosted
import io.embrace.android.embracesdk.internal.logs.attachments.AttachmentErrorCode.ATTACHMENT_TOO_LARGE
import io.embrace.android.embracesdk.internal.logs.attachments.AttachmentErrorCode.OVER_MAX_ATTACHMENTS
import io.embrace.android.embracesdk.internal.logs.attachments.AttachmentErrorCode.UNKNOWN
import io.embrace.android.embracesdk.internal.opentelemetry.embAttachmentErrorCode
import io.embrace.android.embracesdk.internal.opentelemetry.embAttachmentId
import io.embrace.android.embracesdk.internal.opentelemetry.embAttachmentSize
import io.embrace.android.embracesdk.internal.opentelemetry.embAttachmentUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.UUID

internal class AttachmentTest {

    private companion object {
        private const val URL = "https://example.com/my-attachment"
        private const val SIZE = 2L
        private const val LIMIT_MB = 1 * 1024 * 1024
        private val ID = UUID.randomUUID().toString()
        private val BYTES = "{}".toByteArray()
    }

    private val counter: () -> Boolean = { true }

    @Test
    fun `create embrace hosted attachment`() {
        val attachment = EmbraceHosted(BYTES, counter)
        attachment.assertEmbraceHostedAttributesMatch()
    }

    @Test
    fun `embrace hosted attachment empty byte array`() {
        val attachment = EmbraceHosted(ByteArray(0), counter)
        attachment.assertEmbraceHostedAttributesMatch(size = 0)
    }

    @Test
    fun `embrace hosted attachment at max size`() {
        val attachment = EmbraceHosted(ByteArray(LIMIT_MB), counter)
        attachment.assertEmbraceHostedAttributesMatch(size = LIMIT_MB.toLong())
    }

    @Test
    fun `embrace hosted attachment obeys max size constraints`() {
        val size = LIMIT_MB + 1
        val attachment = EmbraceHosted(ByteArray(size), counter)
        attachment.assertEmbraceHostedAttributesMatch(
            size = size.toLong(),
            errorCode = ATTACHMENT_TOO_LARGE
        )
    }

    @Test
    fun `embrace hosted attachment exceeds session limit`() {
        var limit = true
        val smallCounter: () -> Boolean = { limit }
        val attachment = EmbraceHosted(BYTES, smallCounter)
        attachment.assertEmbraceHostedAttributesMatch()

        val size = LIMIT_MB + 1L
        val bytes = ByteArray(size.toInt())
        limit = false
        val limitedAttachment = EmbraceHosted(bytes, smallCounter)
        limitedAttachment.assertEmbraceHostedAttributesMatch(
            size = size,
            errorCode = OVER_MAX_ATTACHMENTS
        )
    }

    @Test
    fun `create user hosted attachment`() {
        val attachment = UserHosted(ID, URL, counter)
        attachment.assertUserHostedAttributesMatch()
    }

    @Test
    fun `user hosted attachment invalid url`() {
        val url = ""
        val attachment = UserHosted(ID, url, counter)
        attachment.assertUserHostedAttributesMatch(url = url, errorCode = UNKNOWN)
    }

    @Test
    fun `user hosted attachment invalid ID`() {
        val id = "my-id"
        val attachment = UserHosted(id, URL, counter)
        attachment.assertUserHostedAttributesMatch(id = id, errorCode = UNKNOWN)
    }

    @Test
    fun `user hosted attachment exceeds session limit`() {
        var limit = true
        val smallCounter: () -> Boolean = { limit }
        val attachment = UserHosted(ID, URL, smallCounter)
        attachment.assertUserHostedAttributesMatch()

        limit = false
        val limitedAttachment = UserHosted(ID, URL, smallCounter)
        limitedAttachment.assertUserHostedAttributesMatch(
            errorCode = OVER_MAX_ATTACHMENTS
        )
    }

    private fun EmbraceHosted.assertEmbraceHostedAttributesMatch(
        size: Long = SIZE,
        errorCode: AttachmentErrorCode? = null,
    ) {
        val observedId = checkNotNull(attributes[embAttachmentId])
        assertNotNull(UUID.fromString(observedId))
        assertEquals(size, checkNotNull(attributes[embAttachmentSize]).toLong())
        assertEquals(errorCode?.toString(), attributes[embAttachmentErrorCode])
        assertEquals(errorCode == null, shouldAttemptUpload())
    }

    private fun UserHosted.assertUserHostedAttributesMatch(
        url: String = URL,
        id: String = ID,
        errorCode: AttachmentErrorCode? = null,
    ) {
        assertEquals(id, checkNotNull(attributes[embAttachmentId]))
        assertEquals(errorCode?.toString(), attributes[embAttachmentErrorCode])
        assertEquals(url, checkNotNull(attributes[embAttachmentUrl]))
    }
}
