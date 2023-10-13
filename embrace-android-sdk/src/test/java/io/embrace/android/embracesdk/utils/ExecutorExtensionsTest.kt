package io.embrace.android.embracesdk.utils

import com.google.common.util.concurrent.MoreExecutors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.Callable

internal class ExecutorExtensionsTest {

    @Test
    fun testSubmitSafe() {
        val executor = MoreExecutors.newDirectExecutorService()
        assertEquals(1, executor.submitSafe(Callable { 1 })?.get())
    }

    @Test
    fun testSubmitSafeClosed() {
        val executor = MoreExecutors.newDirectExecutorService()
        executor.shutdown()
        assertNull(executor.submitSafe(Callable { 1 }))
    }

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

    @Test
    fun testEagerLazyLoadClosed() {
        val executor = MoreExecutors.newDirectExecutorService()
        executor.shutdown()
        assertEquals(1, executor.eagerLazyLoad(Callable { 1 }).value)
    }
}
