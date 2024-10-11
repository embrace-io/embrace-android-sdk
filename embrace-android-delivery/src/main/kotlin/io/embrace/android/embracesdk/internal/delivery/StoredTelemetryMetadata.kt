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
    val filename: String = "${envelopeType.priority}_${timestamp}_${uuid}_${processId}_${complete}_v1.json",
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
            return Result.success(StoredTelemetryMetadata(timestamp, uuid, processId, envelopeType, complete, filename))
        }
    }
}
