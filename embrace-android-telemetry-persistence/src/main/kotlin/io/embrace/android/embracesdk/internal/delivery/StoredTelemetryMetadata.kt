package io.embrace.android.embracesdk.internal.delivery

import kotlin.Result.Companion.failure
import kotlin.Result.Companion.success

/**
 * Metadata about the telemetry payload.
 *
 * This information is encoded in the filename.
 */
data class StoredTelemetryMetadata(
    val timestamp: Long,
    val uuid: String,
    val processIdentifier: String,
    val envelopeType: SupportedEnvelopeType,
    val complete: Boolean = true,
    val payloadType: PayloadType = PayloadType.UNKNOWN,
    val payloadTypesHeader: String = payloadType.value,
    val userSessionId: String = "",
    val sessionPartId: String = "",
) {
    val filename: String = "${envelopeType.priority}_${timestamp}_${uuid}_${processIdentifier}_${complete}_${
        payloadType.filenameComponent
    }_${encodeId(userSessionId)}_${encodeId(sessionPartId)}_$V2_TOKEN"

    companion object {
        private const val V1_TOKEN = "v1.json"
        private const val V2_TOKEN = "v2.json"
        private const val EMPTY_ID_TOKEN = "none"

        private fun encodeId(id: String): String = id.ifEmpty { EMPTY_ID_TOKEN }

        private fun decodeId(token: String): String = when (token) {
            EMPTY_ID_TOKEN -> ""
            else -> token
        }

        /**
         * Parses a filename and constructs a [StoredTelemetryMetadata] object. This returns a
         * [Result] because the filename may be invalid.
         *
         * Supports both v1 (no session IDs) and v2 (with userSessionId & sessionPartId) formats.
         * v1 filenames will default v2 fields to empty strings.
         */
        fun fromFilename(filename: String): Result<StoredTelemetryMetadata> {
            val parts = filename.split("_")
            val version = parts.lastOrNull()
            return when {
                version == V1_TOKEN && parts.size == 7 -> parseV1(filename, parts)
                version == V2_TOKEN && parts.size == 9 -> parseV2(filename, parts)
                else -> failure(IllegalArgumentException("Invalid filename: $filename"))
            }
        }

        private fun parseV1(filename: String, parts: List<String>): Result<StoredTelemetryMetadata> {
            val envelopeType = SupportedEnvelopeType.fromPriority(parts[0]) ?: return failure(
                IllegalArgumentException("Invalid priority: $filename")
            )
            val timestamp = parts[1].toLongOrNull() ?: return failure(
                IllegalArgumentException("Invalid timestamp: $filename")
            )
            val uuid = parts[2]
            val processId = parts[3]
            val complete = parts[4].toBooleanStrictOrNull() ?: return failure(
                IllegalArgumentException("Invalid completeness state: $filename")
            )
            val payloadType = PayloadType.fromFilenameComponent(parts[5])
            return success(
                StoredTelemetryMetadata(
                    timestamp = timestamp,
                    uuid = uuid,
                    processIdentifier = processId,
                    envelopeType = envelopeType,
                    complete = complete,
                    payloadType = payloadType,
                )
            )
        }

        private fun parseV2(filename: String, parts: List<String>): Result<StoredTelemetryMetadata> {
            val envelopeType = SupportedEnvelopeType.fromPriority(parts[0]) ?: return failure(
                IllegalArgumentException("Invalid priority: $filename")
            )
            val timestamp = parts[1].toLongOrNull() ?: return failure(
                IllegalArgumentException("Invalid timestamp: $filename")
            )
            val uuid = parts[2]
            val processId = parts[3]
            val complete = parts[4].toBooleanStrictOrNull() ?: return failure(
                IllegalArgumentException("Invalid completeness state: $filename")
            )
            val payloadType = PayloadType.fromFilenameComponent(parts[5])
            val userSessionId = decodeId(parts[6])
            val sessionPartId = decodeId(parts[7])
            return success(
                StoredTelemetryMetadata(
                    timestamp = timestamp,
                    uuid = uuid,
                    processIdentifier = processId,
                    envelopeType = envelopeType,
                    complete = complete,
                    payloadType = payloadType,
                    userSessionId = userSessionId,
                    sessionPartId = sessionPartId,
                )
            )
        }
    }
}
