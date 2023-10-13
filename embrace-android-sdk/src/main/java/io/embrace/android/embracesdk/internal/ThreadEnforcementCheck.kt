package io.embrace.android.embracesdk.internal

import io.embrace.android.embracesdk.BuildConfig
import java.util.concurrent.atomic.AtomicReference

/**
 * Asserts that a function is called on a thread with a specific name
 */
internal fun enforceThread(expectedThreadReference: AtomicReference<Thread>) {
    if (BuildConfig.DEBUG) {
        val expectedThread = expectedThreadReference.get()
        val currentThread = Thread.currentThread()
        if (expectedThread.name != currentThread.name) {
            throw WrongThreadException(
                "Called on wrong thread. Expected ${expectedThread.name}, got ${currentThread.name}"
            )
        }
    }
}

internal class WrongThreadException(message: String) : IllegalStateException(message)
