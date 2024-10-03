package io.embrace.android.embracesdk.assertions

import io.embrace.android.embracesdk.internal.utils.Provider
import org.junit.Assert.assertEquals
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

fun CountDownLatch.assertCountedDown(waitTimeMs: Long = 1000L) {
    await(waitTimeMs, TimeUnit.MILLISECONDS)
    assertEquals("Operation timed out after $waitTimeMs ms", 0, count)
}

/**
 * Return the result of [desiredValueSupplier] if [condition] is true before [waitTimeMs]
 * elapses. Otherwise, throws [TimeoutException]
 */
inline fun <T, R> returnIfConditionMet(
    desiredValueSupplier: Provider<T>,
    waitTimeMs: Int = 10000,
    checkIntervalMs: Int = 10,
    dataProvider: () -> R,
    condition: (R) -> Boolean,
    errorMessageSupplier: () -> String = { "Timeout period elapsed before condition met." }
): T {
    val tries: Int = waitTimeMs / checkIntervalMs
    val countDownLatch = CountDownLatch(1)

    repeat(tries) {
        if (!condition(dataProvider())) {
            countDownLatch.await(checkIntervalMs.toLong(), TimeUnit.MILLISECONDS)
        } else {
            return desiredValueSupplier.invoke()
        }
    }
    throw TimeoutException(errorMessageSupplier())
}
