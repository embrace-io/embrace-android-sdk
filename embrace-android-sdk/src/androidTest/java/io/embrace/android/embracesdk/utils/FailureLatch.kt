package io.embrace.android.embracesdk.utils

import android.os.Handler
import android.os.Looper
import org.junit.Assert.fail
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * important to run fail() calls on the testing thread. JUnit doesn't always pick up on test failures
 * from background threads, which request might be coming in on, so you could end up with false positives
 */
public class FailureLatch(
    public var timeout: Long,
    public val testingHandler: Handler = Handler(Looper.getMainLooper())
) : CountDownLatch(1) {
    public var timedOut: Boolean = false

    @Volatile
    private var finished = false

    override fun countDown() {
        finished = true
        super.countDown()
    }

    @Throws(InterruptedException::class)
    override fun await() {
        if (finished) {
            return
        }
        // this should never finish. Either FailureLatch#countDown() is called, and the
        // timeoutRunnable is removed from the handler, or the timeoutRunnable executes, failing
        // the test
        this.await(timeout, TimeUnit.MILLISECONDS)
        if (!finished) {
            fail("timed out. More than ${timeout}ms have elapsed")
        }
    }
}
