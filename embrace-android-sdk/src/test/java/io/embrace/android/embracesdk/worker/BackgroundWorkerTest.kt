package io.embrace.android.embracesdk.worker

import io.embrace.android.embracesdk.concurrency.BlockableExecutorService
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Callable

internal class BackgroundWorkerTest {

    @Test
    fun testSubmitRunnable() {
        val impl = BlockableExecutorService()
        var ran = false
        val runnable = Runnable {
            ran = true
        }
        BackgroundWorker(impl).submit(runnable)
        impl.runNext()
        assertTrue(ran)
    }

    @Test
    fun testSubmitCallable() {
        val impl = BlockableExecutorService()
        var ran = false
        val callable = Callable {
            ran = true
        }
        BackgroundWorker(impl).submit(callable)
        impl.runNext()
        assertTrue(ran)
    }
}
