package io.embrace.android.embracesdk.internal.delivery

import io.embrace.android.embracesdk.internal.worker.comparator.extractPriorityFromRunnable

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
internal val storedTelemetryRunnableComparator =
    Comparator<Runnable> { lhs: Runnable, rhs: Runnable ->
        val (lhsPrio, rhsPrio) = extractPriorityFromRunnable<StoredTelemetryMetadata>(lhs, rhs)
        return@Comparator storedTelemetryComparator.compare(lhsPrio, rhsPrio)
    }

internal val storedTelemetryComparator: java.util.Comparator<StoredTelemetryMetadata> =
    compareBy(StoredTelemetryMetadata::envelopeType)
        .thenBy(StoredTelemetryMetadata::timestamp)
