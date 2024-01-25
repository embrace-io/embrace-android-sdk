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
