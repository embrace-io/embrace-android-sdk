package io.embrace.android.embracesdk.internal.worker

/**
 * The priority of a task submitted to the [BackgroundWorker].
 *
 * Tasks with higher priority will be executed first.
 */
enum class TaskPriority(

    /**
     * The delay threshold in milliseconds that should be added to the task's submit time
     * when comparing queue priority.
     *
     * The higher the priority, the lower the delay threshold.
     *
     * See [PriorityThreadPoolExecutor] for further detail.
     */
    val delayThresholdMs: Long,
) {
    CRITICAL(0),
    HIGH(5_000),
    NORMAL(30_000),
    LOW(60_000)
}
