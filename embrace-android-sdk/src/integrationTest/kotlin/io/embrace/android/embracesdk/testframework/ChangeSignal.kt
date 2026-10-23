package io.embrace.android.embracesdk.testframework

import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Lets the test thread wait for data that another thread records, re-checking it whenever [notifyChanged]
 * is called rather than polling on an interval.
 */
internal class ChangeSignal {

    private val lock = ReentrantLock()
    private val changed = lock.newCondition()

    /**
     * Call after recording data that a waiter might be checking.
     */
    fun notifyChanged() {
        lock.withLock { changed.signalAll() }
    }

    /**
     * Returns what [supplier] returns once [condition] is true of it. Throws [TimeoutException] if that doesn't
     * happen before [waitTimeMs] elapses.
     */
    fun <T> await(
        waitTimeMs: Int = 1000,
        supplier: () -> T,
        condition: (T) -> Boolean,
        errorMessageSupplier: () -> String,
    ): T {
        var remainingNs = TimeUnit.MILLISECONDS.toNanos(waitTimeMs.toLong())
        lock.withLock {
            while (true) {
                val data = supplier()
                if (condition(data)) {
                    return data
                }
                if (remainingNs <= 0) {
                    throw TimeoutException(errorMessageSupplier())
                }
                remainingNs = changed.awaitNanos(remainingNs)
            }
        }
    }
}
