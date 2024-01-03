package io.embrace.android.embracesdk.utils

import com.google.common.util.concurrent.MoreExecutors
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.Callable

internal class ExecutorExtensionsTest {

    @Test
    fun testEagerLazyLoadNormal() {
        val executor = MoreExecutors.newDirectExecutorService()
        assertEquals(1, executor.eagerLazyLoad(Callable { 1 }).value)
    }

    @Test(expected = IllegalStateException::class)
    fun testEagerLazyLoadException() {
        val executor = MoreExecutors.newDirectExecutorService()
        executor.eagerLazyLoad(Callable { error("Whoops") }).value
    }
}
