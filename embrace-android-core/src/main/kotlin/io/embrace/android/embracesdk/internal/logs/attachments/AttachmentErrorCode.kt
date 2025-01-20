package io.embrace.android.embracesdk.internal.logs.attachments

/**
 * Enumerates the states where an attachment could not be added to a log record.
 */
enum class AttachmentErrorCode {
    ATTACHMENT_TOO_LARGE,
    OVER_MAX_ATTACHMENTS,
    UNKNOWN
}
