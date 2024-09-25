package io.embrace.android.embracesdk.assertions

import org.junit.Assert.assertEquals
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

fun CountDownLatch.assertCountedDown() {
    await(1, TimeUnit.SECONDS)
    assertEquals(0, count)
}
