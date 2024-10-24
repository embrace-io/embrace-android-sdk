package io.embrace.android.embracesdk.internal.delivery

import io.embrace.android.embracesdk.internal.delivery.PayloadType.Companion.toFilenamePart
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
    val payloadType: PayloadType = PayloadType.UNKNOWN,
    val filename: String = "${envelopeType.priority}_${timestamp}_${uuid}_${processId}_${complete}_${
        toFilenamePart(
            payloadType
        )
    }_v1.json",
) {

    companion object {
        /**
         * Parses a filename and constructs a [StoredTelemetryMetadata] object. This returns a
         * [Result] because the filename may be invalid.
         */
        fun fromFilename(filename: String): Result<StoredTelemetryMetadata> {
            val parts = filename.split("_")
            if (parts.size != 7) {
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
            val payloadType = PayloadType.fromFilenameComponent(parts[5])
            return Result.success(
                StoredTelemetryMetadata(
                    timestamp,
                    uuid,
                    processId,
                    envelopeType,
                    complete,
                    payloadType,
                    filename
                )
            )
        }
    }
}
