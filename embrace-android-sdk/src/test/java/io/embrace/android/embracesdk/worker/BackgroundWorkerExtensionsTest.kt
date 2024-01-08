package io.embrace.android.embracesdk.worker

import com.google.common.util.concurrent.MoreExecutors
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.Callable

internal class BackgroundWorkerExtensionsTest {

    @Test
    fun testEagerLazyLoadNormal() {
        val executor = BackgroundWorker(MoreExecutors.newDirectExecutorService())
        assertEquals(1, executor.eagerLazyLoad(Callable { 1 }).value)
    }

    @Test(expected = IllegalStateException::class)
    fun testEagerLazyLoadException() {
        val executor = BackgroundWorker(MoreExecutors.newDirectExecutorService())
        executor.eagerLazyLoad(Callable { error("Whoops") }).value
    }
}
