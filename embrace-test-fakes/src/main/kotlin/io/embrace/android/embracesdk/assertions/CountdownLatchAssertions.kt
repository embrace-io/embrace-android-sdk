package io.embrace.android.embracesdk.assertions

import org.junit.Assert.assertEquals
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

fun CountDownLatch.assertCountedDown(waitTimeMs: Long = 1000L) {
    await(waitTimeMs, TimeUnit.MILLISECONDS)
    assertEquals("Operation timed out after $waitTimeMs ms", 0, count)
}
