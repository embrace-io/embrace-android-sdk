package io.embrace.android.embracesdk.internal.delivery

import kotlin.Result.Companion.failure

/**
 * Metadata about the telemetry payload.
 *
 * This information is encoded in the filename.
 */
data class StoredTelemetryMetadata(
    val timestamp: Long,
    val uuid: String,
    val processId: String,
    val envelopeType: SupportedEnvelopeType,
    val complete: Boolean = true,
    val filename: String = "${timestamp}_${envelopeType.description}_${uuid}_${processId}_${complete}_v1.json",
) {

    companion object {

        /**
         * Parses a filename and constructs a [StoredTelemetryMetadata] object. This returns a
         * [Result] because the filename may be invalid.
         */
        fun fromFilename(filename: String): Result<StoredTelemetryMetadata> {
            val parts = filename.split("_")
            if (parts.size != 6) {
                return failure(IllegalArgumentException("Invalid filename: $filename"))
            }
            val timestamp = parts[0].toLongOrNull() ?: return failure(
                IllegalArgumentException("Invalid timestamp: $filename")
            )
            val type = SupportedEnvelopeType.fromDescription(parts[1]) ?: return failure(
                IllegalArgumentException("Invalid type: $filename")
            )
            val uuid = parts[2]
            val processId = parts[3]
            val complete = parts[4].toBooleanStrictOrNull() ?: return failure(
                IllegalArgumentException("Invalid completeness state: $filename")
            )
            return Result.success(StoredTelemetryMetadata(timestamp, uuid, processId, type, complete, filename))
        }
    }
}
