package io.embrace.android.embracesdk.concurrency

import io.embrace.android.embracesdk.internal.ConstantNameThreadFactory
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * A single-threaded [java.util.concurrent.ScheduledExecutorService] used for tests that exposes the last [Throwable] that interrupted
 * the execution of a task
 */
public class SingleThreadTestScheduledExecutor(
    threadFactory: ThreadFactory = defaultThreadFactory
) : ScheduledThreadPoolExecutor(1, threadFactory) {

    public val executing: AtomicBoolean = AtomicBoolean(false)
    private val lastThrowable = AtomicReference<Throwable?>()

    override fun beforeExecute(t: Thread?, r: Runnable?) {
        super.beforeExecute(t, r)
        synchronized(lastThrowable) {
            executing.set(true)
        }
    }

    override fun afterExecute(r: Runnable?, t: Throwable?) {
        synchronized(lastThrowable) {
            try {
                if (r is Future<*>) {
                    r.get(500, TimeUnit.MILLISECONDS)
                }
            } catch (e: ExecutionException) {
                lastThrowable.set(e.cause)
            } catch (t: Throwable) {
                lastThrowable.set(t)
            } finally {
                executing.set(false)
            }
        }
        super.afterExecute(r, t)
    }

    /**
     * The last [Throwable] thrown during the execution of a job. Note that is achieved by catching the [ExecutionException] thrown when
     * we try to get the result of the task via the [Future] associated with the execution, which is done after the [Future] completes. So
     * there may be a time when the task is done but this function returns the wrong value - you have to check that [executing] is false to
     * make sure that we are not in the process of setting a new [lastThrowable] in order for this value to be truly valid.
     */
    public fun lastThrowable(): Throwable? = synchronized(lastThrowable) { lastThrowable.get() }

    /**
     * Resets the error tracking
     */
    public fun reset(): Unit = synchronized(lastThrowable) { lastThrowable.set(null) }

    private companion object {
        private val defaultThreadFactory = ConstantNameThreadFactory("test")
    }
}
