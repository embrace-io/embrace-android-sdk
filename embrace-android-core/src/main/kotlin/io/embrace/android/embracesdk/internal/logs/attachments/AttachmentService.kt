package io.embrace.android.embracesdk.internal.logs.attachments

import io.embrace.android.embracesdk.internal.logs.attachments.Attachment.EmbraceHosted
import io.embrace.android.embracesdk.internal.logs.attachments.Attachment.UserHosted
import io.embrace.android.embracesdk.internal.session.MemoryCleanerListener
import java.util.concurrent.atomic.AtomicInteger

/**
 * Counts the number of attachments that should be added to log records.
 */
class AttachmentService(private val limit: Int = 5) : MemoryCleanerListener {

    fun createAttachment(attachment: ByteArray): EmbraceHosted =
        EmbraceHosted(attachment, ::incrementAndCheckAttachmentLimit)

    fun createAttachment(
        attachmentId: String,
        attachmentUrl: String,
        attachmentSize: Long,
    ): UserHosted = UserHosted(
        attachmentSize,
        attachmentId,
        attachmentUrl,
        ::incrementAndCheckAttachmentLimit
    )

    private val count: AtomicInteger = AtomicInteger(0)

    override fun cleanCollections() = count.set(0)

    /**
     * Increments the counter of attachments for this session and returns true if an attachment can be uploaded.
     */
    private fun incrementAndCheckAttachmentLimit(): Boolean = count.incrementAndGet() <= limit
}
