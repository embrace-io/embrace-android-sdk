package io.embrace.android.embracesdk.internal.delivery

/**
 * Compares [StoredTelemetryMetadata] in priority order. Our priority rules are:
 *
 * Crash > Session
 * Session > Log
 * Log > Network
 * Otherwise, older timestamps take precedence as they are more likely to get deleted if the SDK
 * runs out of disk space.
 *
 * We considered the possibility of starvation & scoring payloads based on age, but we decided
 * that crashes/sessions are generally far more important so should always be delivered first.
 */
internal object StoredTelemetryComparator : Comparator<StoredTelemetryMetadata> {

    override fun compare(lhs: StoredTelemetryMetadata, rhs: StoredTelemetryMetadata): Int {
        return compareBy(StoredTelemetryMetadata::envelopeType)
            .thenBy(StoredTelemetryMetadata::timestamp)
            .compare(lhs, rhs)
    }
}
