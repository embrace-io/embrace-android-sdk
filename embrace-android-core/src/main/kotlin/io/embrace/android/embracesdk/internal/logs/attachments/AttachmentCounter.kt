package io.embrace.android.embracesdk.internal.logs.attachments

import io.embrace.android.embracesdk.internal.session.MemoryCleanerListener
import java.util.concurrent.atomic.AtomicInteger

/**
 * Counts the number of attachments that should be added to log records.
 */
class AttachmentCounter(private val limit: Int = 5) : MemoryCleanerListener {

    private val count: AtomicInteger = AtomicInteger(0)

    override fun cleanCollections() = count.set(0)

    /**
     * Increments the counter of attachments for this session and returns true if an attachment can be uploaded.
     */
    fun incrementAndCheckAttachmentLimit(): Boolean = count.incrementAndGet() <= limit
}
