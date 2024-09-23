package io.embrace.android.embracesdk.internal.worker.comparator

import io.embrace.android.embracesdk.internal.comms.api.ApiRequest
import io.embrace.android.embracesdk.internal.comms.api.isSessionRequest
import io.embrace.android.embracesdk.internal.worker.PriorityRunnableFuture
import io.embrace.android.embracesdk.internal.worker.TaskPriority

/**
 * Prioritises based on [TaskPriority]
 */
internal val taskPriorityComparator = Comparator { lhs: Runnable, rhs: Runnable ->
    val (lhsPrio, rhsPrio) = extractPriorityFromRunnable<TaskPriority>(lhs, rhs)
    return@Comparator lhsPrio.compareTo(rhsPrio)
}

/**
 * Prioritises based on whether the [ApiRequest] is a session or not.
 */
internal val apiRequestComparator = Comparator { lhs: Runnable, rhs: Runnable ->
    val (lhsRequest, rhsRequest) = extractPriorityFromRunnable<ApiRequest>(lhs, rhs)
    return@Comparator when {
        lhsRequest.isSessionRequest() -> -1
        rhsRequest.isSessionRequest() -> 1
        else -> 0
    }
}

private inline fun <reified T> extractPriorityFromRunnable(
    lhs: Runnable,
    rhs: Runnable
): Pair<T, T> {
    require(lhs is PriorityRunnableFuture<*> && rhs is PriorityRunnableFuture<*>) {
        "Runnables must be PriorityRunnableFuture"
    }
    require(lhs.priorityInfo is T && rhs.priorityInfo is T) {
        "PriorityInfo must be of type T"
    }
    return Pair(lhs.priorityInfo as T, rhs.priorityInfo as T)
}
