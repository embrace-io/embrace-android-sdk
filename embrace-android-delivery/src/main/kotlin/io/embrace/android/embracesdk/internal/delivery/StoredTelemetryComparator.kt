package io.embrace.android.embracesdk.internal.delivery

import io.embrace.android.embracesdk.internal.worker.PriorityRunnableFuture

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
val storedTelemetryRunnableComparator =
    Comparator<Runnable> { lhs: Runnable, rhs: Runnable ->
        val (lhsPrio, rhsPrio) = extractPriorityFromRunnable<StoredTelemetryMetadata>(lhs, rhs)
        return@Comparator storedTelemetryComparator.compare(lhsPrio, rhsPrio)
    }

internal val storedTelemetryComparator: java.util.Comparator<StoredTelemetryMetadata> =
    compareBy(StoredTelemetryMetadata::envelopeType)
        .thenBy(StoredTelemetryMetadata::timestamp)

inline fun <reified T> extractPriorityFromRunnable(
    lhs: Runnable,
    rhs: Runnable
): Pair<T, T> {
    require(lhs is PriorityRunnableFuture<*> && rhs is PriorityRunnableFuture<*>) {
        "Runnables must be PriorityRunnableFuture"
    }
    require(lhs.priorityInfo is T && rhs.priorityInfo is T) {
        "PriorityInfo must be of type ${T::class.java.simpleName}"
    }
    return Pair(lhs.priorityInfo as T, rhs.priorityInfo as T)
}
