package io.embrace.android.embracesdk.utils

import java.util.concurrent.atomic.AtomicInteger

/**
 * Safe-wrapper around list's subscript.
 * Returns the element at the specified index.
 * Returns null if the index is out of bounds, instead of the out-of-bounds exception.
 */
internal fun <T> List<T>.at(index: Int): T? {
    return if (index >= 0 && index < count()) {
        this[index]
    } else {
        null
    }
}

/**
 * Dynamically allocate a set of locks in this map to support mutual exclusion for executing blocks of code when using the same key
 */
internal fun <T> MutableMap<String, AtomicInteger>.lockAndRun(key: String, code: () -> T): T {
    var lock: AtomicInteger

    // Find the lock for the given key if it exists - create a new one if it doesn't
    // This locks the entire instance to ensure only one lock exists per key
    synchronized(this) {
        lock = this[key] ?: AtomicInteger(0)
        if (this[key] == null) {
            this[key] = lock
        }
    }

    // Increment the ref count
    lock.incrementAndGet()

    // Acquire lock and execute the code
    synchronized(lock) {
        try {
            return code()
        } finally {
            // Remove lock if ref count for this key's lock is 0, i.e. nothing is waiting to acquire the lock
            if (lock.decrementAndGet() == 0) {
                this.remove(key)
            }
        }
    }
}

/**
 * A version of [take] that is useful for a [Collection] expected to be threadsafe but doesn't synchronize the reads with writes such
 * that the underlying data change during iteration. [take] has an optimization that returns the whole [Collection] if the size is less than
 * or equal to the size of the number of elements requested, but since the underlying data can change after the size check, you can
 * get more elements than requested if they were added during the [toList] call.
 */
internal fun <T> Collection<T>.threadSafeTake(n: Int): List<T> {
    return if (n == 0) {
        emptyList()
    } else {
        val returnList = ArrayList<T>(n)

        for ((count, item) in this.withIndex()) {
            returnList.add(item)
            if (count + 1 == n) {
                break
            }
        }

        return if (returnList.size <= 1) {
            take(returnList.size)
        } else {
            returnList
        }
    }
}
