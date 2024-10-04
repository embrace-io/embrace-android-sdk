package io.embrace.android.embracesdk.internal.worker.comparator

import io.embrace.android.embracesdk.internal.comms.api.ApiRequest
import io.embrace.android.embracesdk.internal.comms.api.isSessionRequest
import io.embrace.android.embracesdk.internal.delivery.extractPriorityFromRunnable
import io.embrace.android.embracesdk.internal.worker.TaskPriority

/**
 * Prioritises based on [TaskPriority]
 */
val taskPriorityComparator: Comparator<Runnable> = Comparator { lhs: Runnable, rhs: Runnable ->
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
