package io.embrace.android.embracesdk.internal.logs.attachments

/**
 * Enumerates the states where an attachment could not be added to a log record.
 */
internal enum class AttachmentErrorCode {
    ATTACHMENT_TOO_LARGE,
    UNSUCCESSFUL_UPLOAD,
    OVER_MAX_ATTACHMENTS,
    UNKNOWN
}
