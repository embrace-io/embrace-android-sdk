package io.embrace.android.embracesdk.internal.worker

import java.util.concurrent.Callable
import java.util.concurrent.Future
import java.util.concurrent.ScheduledThreadPoolExecutor

internal class DecoratedExecutorService : ScheduledThreadPoolExecutor(1) {
    val runnables = mutableListOf<Runnable>()
    val callables = mutableListOf<Callable<*>>()

    override fun <T : Any?> submit(task: Callable<T>): Future<T> {
        callables.add(task)
        return super.submit(task)
    }

    override fun submit(task: Runnable): Future<*> {
        runnables.add(task)
        return super.submit(task)
    }
}
