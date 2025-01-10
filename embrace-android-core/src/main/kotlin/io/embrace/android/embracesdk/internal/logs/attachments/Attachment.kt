package io.embrace.android.embracesdk.internal.logs.attachments

import io.embrace.android.embracesdk.internal.logs.attachments.AttachmentErrorCode.ATTACHMENT_TOO_LARGE
import io.embrace.android.embracesdk.internal.logs.attachments.AttachmentErrorCode.OVER_MAX_ATTACHMENTS
import io.embrace.android.embracesdk.internal.logs.attachments.AttachmentErrorCode.UNKNOWN
import io.embrace.android.embracesdk.internal.utils.toNonNullMap
import java.util.UUID

/**
 * Holds attributes that describe an attachment to a log record.
 */
internal sealed class Attachment(
    val size: Long,
    val id: String,
    val counter: AttachmentCounter,
) {

    internal companion object {
        const val ATTR_KEY_SIZE = "emb.attachment_size"
        const val ATTR_KEY_URL = "emb.attachment_url"
        const val ATTR_KEY_ID = "emb.attachment_id"
        const val ATTR_KEY_ERR_CODE = "emb.attachment_error_code"
        private const val LIMIT_MB = 1 * 1024 * 1024
    }

    abstract val attributes: Map<String, String>

    protected fun constructAttributes(
        size: Long,
        id: String,
        errorCode: AttachmentErrorCode? = null
    ) = mapOf(
        ATTR_KEY_SIZE to size.toString(),
        ATTR_KEY_ID to id,
        ATTR_KEY_ERR_CODE to errorCode?.name
    ).toNonNullMap()

    /**
     * An attachment that is uploaded to Embrace's backend.
     */
    class EmbraceHosted(
        val bytes: ByteArray,
        counter: AttachmentCounter,
    ) : Attachment(
        bytes.size.toLong(),
        UUID.randomUUID().toString(),
        counter
    ) {

        private val errorCode: AttachmentErrorCode? = when {
            !counter.incrementAndCheckAttachmentLimit() -> OVER_MAX_ATTACHMENTS
            size > LIMIT_MB -> ATTACHMENT_TOO_LARGE
            else -> null
        }

        override val attributes: Map<String, String> = constructAttributes(size, id, errorCode)
    }

    /**
     * An attachment that is uploaded to a user-supplied backend.
     */
    class UserHosted(
        size: Long,
        id: String,
        val url: String,
        counter: AttachmentCounter,
    ) : Attachment(size, id, counter) {

        private val errorCode: AttachmentErrorCode? = when {
            !counter.incrementAndCheckAttachmentLimit() -> OVER_MAX_ATTACHMENTS
            size < 0 -> UNKNOWN
            url.isEmpty() -> UNKNOWN
            isNotUuid() -> UNKNOWN
            else -> null
        }

        override val attributes: Map<String, String> =
            constructAttributes(size, id, errorCode).plus(
                ATTR_KEY_URL to url
            )

        private fun isNotUuid(): Boolean = try {
            UUID.fromString(id)
            false
        } catch (e: IllegalArgumentException) {
            true
        }
    }
}
