package io.embrace.android.embracesdk.internal.logs.attachments

import io.embrace.android.embracesdk.internal.logs.attachments.AttachmentErrorCode.ATTACHMENT_TOO_LARGE
import io.embrace.android.embracesdk.internal.logs.attachments.AttachmentErrorCode.OVER_MAX_ATTACHMENTS
import io.embrace.android.embracesdk.internal.logs.attachments.AttachmentErrorCode.UNKNOWN
import io.embrace.android.embracesdk.internal.utils.toNonNullMap
import io.embrace.android.embracesdk.semconv.EmbAttachmentAttributes
import java.util.UUID

/**
 * Holds attributes that describe an attachment to a log record.
 */
sealed class Attachment(val id: String) {

    internal companion object {
        private const val LIMIT_MB = 1 * 1024 * 1024
    }

    abstract val attributes: Map<String, String>
    abstract val errorCode: AttachmentErrorCode?

    protected fun constructAttributes(
        id: String,
        errorCode: AttachmentErrorCode? = null,
    ): Map<String, String> = mapOf(
        EmbAttachmentAttributes.EMB_ATTACHMENT_ID to id,
        EmbAttachmentAttributes.EMB_ATTACHMENT_ERROR_CODE to errorCode?.name
    ).toNonNullMap()

    /**
     * An attachment that is uploaded to Embrace's backend.
     */
    class EmbraceHosted(
        val bytes: ByteArray,
        counter: () -> Boolean,
    ) : Attachment(
        UUID.randomUUID().toString()
    ) {

        private val size: Long = bytes.size.toLong()

        override val errorCode: AttachmentErrorCode? = when {
            !counter() -> OVER_MAX_ATTACHMENTS
            size > LIMIT_MB -> ATTACHMENT_TOO_LARGE
            else -> null
        }

        override val attributes: Map<String, String> = constructAttributes(id, errorCode).plus(
            EmbAttachmentAttributes.EMB_ATTACHMENT_SIZE to size.toString(),
        )

        fun shouldAttemptUpload(): Boolean = errorCode == null
    }

    /**
     * An attachment that is uploaded to a user-supplied backend.
     */
    class UserHosted(
        id: String,
        val url: String,
        counter: () -> Boolean,
    ) : Attachment(id) {

        override val errorCode: AttachmentErrorCode? = when {
            !counter() -> OVER_MAX_ATTACHMENTS
            url.isEmpty() -> UNKNOWN
            isNotUuid() -> UNKNOWN
            else -> null
        }

        override val attributes: Map<String, String> = constructAttributes(id, errorCode).plus(
            EmbAttachmentAttributes.EMB_ATTACHMENT_URL to url
        )

        private fun isNotUuid(): Boolean = try {
            UUID.fromString(id)
            false
        } catch (e: IllegalArgumentException) {
            true
        }
    }
}
