package io.embrace.android.embracesdk.internal.logs.attachments

import io.embrace.android.embracesdk.internal.arch.schema.EmbraceAttributeKey
import io.embrace.android.embracesdk.internal.logs.attachments.AttachmentErrorCode.ATTACHMENT_TOO_LARGE
import io.embrace.android.embracesdk.internal.logs.attachments.AttachmentErrorCode.OVER_MAX_ATTACHMENTS
import io.embrace.android.embracesdk.internal.logs.attachments.AttachmentErrorCode.UNKNOWN
import io.embrace.android.embracesdk.internal.opentelemetry.embAttachmentErrorCode
import io.embrace.android.embracesdk.internal.opentelemetry.embAttachmentId
import io.embrace.android.embracesdk.internal.opentelemetry.embAttachmentSize
import io.embrace.android.embracesdk.internal.opentelemetry.embAttachmentUrl
import io.embrace.android.embracesdk.internal.utils.toNonNullMap
import java.util.UUID

/**
 * Holds attributes that describe an attachment to a log record.
 */
sealed class Attachment(
    val size: Long,
    val id: String,
) {

    internal companion object {
        private const val LIMIT_MB = 1 * 1024 * 1024
    }

    abstract val attributes: Map<EmbraceAttributeKey, String>

    protected fun constructAttributes(
        size: Long,
        id: String,
        errorCode: AttachmentErrorCode? = null
    ): Map<EmbraceAttributeKey, String> = mapOf(
        embAttachmentSize to size.toString(),
        embAttachmentId to id,
        embAttachmentErrorCode to errorCode?.name
    ).toNonNullMap()

    /**
     * An attachment that is uploaded to Embrace's backend.
     */
    class EmbraceHosted(
        val bytes: ByteArray,
        counter: () -> Boolean
    ) : Attachment(
        bytes.size.toLong(),
        UUID.randomUUID().toString()
    ) {

        private val errorCode: AttachmentErrorCode? = when {
            !counter() -> OVER_MAX_ATTACHMENTS
            size > LIMIT_MB -> ATTACHMENT_TOO_LARGE
            else -> null
        }

        override val attributes: Map<EmbraceAttributeKey, String> =
            constructAttributes(size, id, errorCode)

        fun shouldAttemptUpload(): Boolean = errorCode == null
    }

    /**
     * An attachment that is uploaded to a user-supplied backend.
     */
    class UserHosted(
        size: Long,
        id: String,
        val url: String,
        counter: () -> Boolean,
    ) : Attachment(size, id) {

        private val errorCode: AttachmentErrorCode? = when {
            !counter() -> OVER_MAX_ATTACHMENTS
            size < 0 -> UNKNOWN
            url.isEmpty() -> UNKNOWN
            isNotUuid() -> UNKNOWN
            else -> null
        }

        override val attributes: Map<EmbraceAttributeKey, String> =
            constructAttributes(size, id, errorCode).plus(
                embAttachmentUrl to url
            )

        private fun isNotUuid(): Boolean = try {
            UUID.fromString(id)
            false
        } catch (e: IllegalArgumentException) {
            true
        }
    }
}
