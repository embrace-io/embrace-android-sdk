package io.embrace.android.embracesdk.internal.delivery

import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.utils.Uuid
import kotlin.Result.Companion.failure

/**
 * Metadata about the telemetry payload.
 *
 * This information is encoded in the filename.
 */
class StoredTelemetryMetadata(
    val timestamp: Long,
    val uuid: String,
    val envelopeType: SupportedEnvelopeType,
    val filename: String = "${timestamp}_${envelopeType.description}_${uuid}_v1.json"
) {

    companion object {

        /**
         * Parses a filename and constructs a [StoredTelemetryMetadata] object. This returns a
         * [Result] because the filename may be invalid.
         */
        fun fromFilename(filename: String): Result<StoredTelemetryMetadata> {
            val parts = filename.split("_")
            if (parts.size != 4) {
                return failure(IllegalArgumentException("Invalid filename: $filename"))
            }
            val timestamp = parts[0].toLongOrNull() ?: return failure(
                IllegalArgumentException("Invalid timestamp: $filename")
            )
            val type = SupportedEnvelopeType.fromDescription(parts[1]) ?: return failure(
                IllegalArgumentException("Invalid type: $filename")
            )
            val uuid = parts[2].removeSuffix(".json")
            return Result.success(StoredTelemetryMetadata(timestamp, uuid, type, filename))
        }

        /**
         * Constructs a [StoredTelemetryMetadata] object from the given [Envelope].
         */
        fun fromEnvelope(
            clock: Clock,
            type: SupportedEnvelopeType,
            uuid: String = Uuid.getEmbUuid()
        ): StoredTelemetryMetadata = StoredTelemetryMetadata(clock.now(), uuid, type)
    }
}
