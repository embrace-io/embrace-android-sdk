package io.embrace.android.embracesdk.concurrency

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Runs each of [actions] on its own thread, releasing them together from a shared start barrier so that they overlap as
 * much as possible, then waits for all of them to finish.
 *
 * Fails if any action throws, including assertion failures made inside an action, or if the actions don't all finish
 * within [timeoutMs]. The first failure is rethrown as the cause, and any others are attached as suppressed exceptions.
 *
 * This is deliberately indeterministic, but the idea is that flakiness here is a sign that there's a race condition
 * that isn't properly handled. It's a best-effort attempt, rather than something fully deterministic.
 */
fun runActionsConcurrently(
    actions: List<() -> Unit>,
    timeoutMs: Long = DEFAULT_CONCURRENT_TIMEOUT_MS,
) {
    val errors = ConcurrentLinkedQueue<Throwable>()
    val startSignal = CountDownLatch(1)
    val doneSignal = CountDownLatch(actions.size)
    actions.forEachIndexed { index, action ->
        Thread(
            {
                try {
                    startSignal.await()
                    action()
                } catch (throwable: Throwable) {
                    errors.add(throwable)
                } finally {
                    doneSignal.countDown()
                }
            },
            "run-concurrently-$index",
        ).apply {
            isDaemon = true
            start()
        }
    }
    startSignal.countDown()

    if (!doneSignal.await(timeoutMs, TimeUnit.MILLISECONDS)) {
        throw AssertionError("${doneSignal.count} of ${actions.size} concurrent actions did not finish within $timeoutMs ms")
    }
    errors.firstOrNull()?.let { first ->
        throw AssertionError("${errors.size} of ${actions.size} concurrent actions failed. Race condition? Likely.", first).apply {
            errors.drop(1).forEach(::addSuppressed)
        }
    }
}

/**
 * Runs [action] on [threadCount] threads at once, passing each its zero-based thread index. See [runActionsConcurrently]
 * for how the threads are started and how failures are reported.
 */
fun runConcurrently(
    threadCount: Int,
    timeoutMs: Long = DEFAULT_CONCURRENT_TIMEOUT_MS,
    action: (threadIndex: Int) -> Unit,
) {
    runActionsConcurrently(
        actions = List(threadCount) { threadIndex -> { action(threadIndex) } },
        timeoutMs = timeoutMs,
    )
}

const val DEFAULT_CONCURRENT_TIMEOUT_MS: Long = 5_000L
