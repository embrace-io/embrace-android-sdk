package io.embrace.android.embracesdk.comms.delivery

// import io.embrace.android.embracesdk.concurrency.SingleThreadTestScheduledExecutor
// import io.embrace.android.embracesdk.fakes.FakeStorageService
// import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
// import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
// import org.junit.Assert.assertArrayEquals
// import org.junit.Assert.assertEquals
// import org.junit.Assert.assertNull
// import org.junit.Before
// import org.junit.Test
// import java.util.concurrent.CopyOnWriteArrayList
// import java.util.concurrent.CountDownLatch
// import java.util.concurrent.ExecutorService
// import java.util.concurrent.TimeUnit
// import java.util.concurrent.atomic.AtomicReference

internal class EmbraceCacheServiceConcurrentAccessTest {
//    private lateinit var embraceCacheService: EmbraceCacheService
//    private lateinit var storageService: FakeStorageService
//    private lateinit var thread1: ExecutorService
//    private lateinit var thread2: ExecutorService
//
//    @Before
//    fun setUp() {
//        storageService = FakeStorageService()
//        embraceCacheService = EmbraceCacheService(
//            storageService,
//            EmbraceSerializer(),
//            InternalEmbraceLogger()
//        )
//        thread1 = SingleThreadTestScheduledExecutor()
//        thread2 = SingleThreadTestScheduledExecutor()
//    }
//
//    @Test
//    fun `concurrent writes should lead to last finished written bytes to be persisted`() {
//        val filename = "testfile-pause"
//        val latch = CountDownLatch(2)
//
//        thread1.submit {
//            embraceCacheService.cacheBytes(filename, lettersBytes)
//            latch.countDown()
//        }
//        Thread.sleep(10)
//        thread2.submit {
//            embraceCacheService.cacheBytes(filename, numbersBytes)
//            latch.countDown()
//        }
//
//        latch.await(1, TimeUnit.SECONDS)
//        assertIsNumbersBytes(embraceCacheService.loadBytes(filename))
//    }
//
//    @Test
//    fun `access to files with different names should not block`() {
//        val filename1 = "testfile-pause"
//        val filename2 = "testfile"
//        val latch = CountDownLatch(2)
//        val finishOrder = CopyOnWriteArrayList<Int>()
//
//        thread1.submit {
//            embraceCacheService.cacheBytes(filename1, lettersBytes)
//            finishOrder.add(1)
//            latch.countDown()
//        }
//        Thread.sleep(10)
//        thread2.submit {
//            embraceCacheService.cacheBytes(filename2, numbersBytes)
//            checkNotNull(embraceCacheService.loadBytes(filename2))
//            finishOrder.add(2)
//            latch.countDown()
//        }
//
//        latch.await(1, TimeUnit.SECONDS)
//
//        assertEquals(listOf(2, 1), finishOrder)
//    }
//
//    @Test
//    fun `reads should not block other reads`() {
//        val filename = "testfile-read-alternating-pause"
//        val latch = CountDownLatch(2)
//        val finishOrder = CopyOnWriteArrayList<Int>()
//
//        embraceCacheService.cacheBytes(filename, lettersBytes)
//        thread1.submit {
//            embraceCacheService.loadBytes(filename)
//            finishOrder.add(1)
//            latch.countDown()
//        }
//        Thread.sleep(10)
//        thread2.submit {
//            embraceCacheService.loadBytes(filename)
//            finishOrder.add(2)
//            latch.countDown()
//        }
//
//        latch.await(1, TimeUnit.SECONDS)
//
//        assertEquals(listOf(2, 1), finishOrder)
//    }
//
//    @Test
//    fun `reads should block writes`() {
//        val filename = "testfile-read-alternating-pause"
//        val latch = CountDownLatch(2)
//        val loadedObject = AtomicReference<ByteArray?>()
//        val finishOrder = CopyOnWriteArrayList<Int>()
//
//        embraceCacheService.cacheBytes(filename, lettersBytes)
//        thread1.submit {
//            val content = embraceCacheService.loadBytes(filename)
//            finishOrder.add(1)
//            loadedObject.set(content)
//            latch.countDown()
//        }
//        Thread.sleep(10)
//        thread2.submit {
//            embraceCacheService.cacheBytes(filename, numbersBytes)
//            finishOrder.add(2)
//            latch.countDown()
//        }
//
//        latch.await(1, TimeUnit.SECONDS)
//        assertEquals(listOf(1, 2), finishOrder)
//        assertIsLettersBytes(loadedObject.get())
//        assertIsNumbersBytes(embraceCacheService.loadBytes(filename))
//    }
//
//    @Test
//    fun `reading a file that is being written to should block and succeed`() {
//        val filename = "testfile-pause"
//        val loadedObject = AtomicReference<ByteArray?>()
//        val latch = CountDownLatch(2)
//
//        thread1.submit {
//            embraceCacheService.cacheBytes(filename, lettersBytes)
//            latch.countDown()
//        }
//        Thread.sleep(10)
//        thread2.submit {
//            loadedObject.set(embraceCacheService.loadBytes(filename))
//            latch.countDown()
//        }
//
//        latch.await(1, TimeUnit.SECONDS)
//        assertIsLettersBytes(loadedObject.get())
//    }
//
//    @Test
//    fun `reading a file that is being rewritten should block and succeed`() {
//        val filename = "testfile-pause"
//        val loadedObject = AtomicReference<ByteArray?>()
//        val initialLatch = CountDownLatch(1)
//        val latch = CountDownLatch(2)
//
//        thread1.submit {
//            embraceCacheService.cacheBytes(filename, lettersBytes)
//            initialLatch.countDown()
//        }
//        initialLatch.await(1, TimeUnit.SECONDS)
//
//        thread1.submit {
//            embraceCacheService.cacheBytes(filename, numbersBytes)
//            latch.countDown()
//        }
//        Thread.sleep(10)
//        thread2.submit {
//            loadedObject.set(embraceCacheService.loadBytes(filename))
//            latch.countDown()
//        }
//        latch.await(1, TimeUnit.SECONDS)
//
//        assertIsNumbersBytes(loadedObject.get())
//    }
//
//    @Test
//    fun `interrupting a write should not leave partially written files`() {
//        val filename = "testfile-pause"
//        val latch = CountDownLatch(2)
//        thread1.submit {
//            embraceCacheService.cacheBytes(filename, lettersBytes)
//            latch.countDown()
//        }
//        Thread.sleep(10)
//        thread2.submit {
//            thread1.shutdownNow()
//            latch.countDown()
//        }
//
//        latch.await(1, TimeUnit.SECONDS)
//        val loadedObject = embraceCacheService.loadBytes(filename)
//
//        assertNull(loadedObject)
//    }
//
//    @Test
//    fun `partial writes aborted due to exception does not leave partially written files`() {
//        val filename = "testfile-truncate"
//        embraceCacheService.cacheBytes(filename, lettersBytes)
//
//        val loadedObject = embraceCacheService.loadBytes(filename)
//        assertNull(loadedObject)
//    }
//
//    @Test
//    fun `paused write eventually succeeds`() {
//        val filename = "testfile-pause"
//        val latch = CountDownLatch(1)
//        thread1.submit {
//            embraceCacheService.cacheBytes(filename, lettersBytes)
//            latch.countDown()
//        }
//
//        latch.await(1, TimeUnit.SECONDS)
//        assertIsLettersBytes(embraceCacheService.loadBytes(filename))
//    }
//
//    @Test
//    fun `paused read eventually succeeds`() {
//        val filename = "testfile-read-alternating-pause"
//        val loadedObject = AtomicReference<ByteArray?>()
//        embraceCacheService.cacheBytes(filename, lettersBytes)
//        val latch = CountDownLatch(1)
//        thread1.submit {
//            loadedObject.set(embraceCacheService.loadBytes(filename))
//            latch.countDown()
//        }
//
//        latch.await(1, TimeUnit.SECONDS)
//
//        assertIsLettersBytes(loadedObject.get())
//    }
//
//    private fun assertIsLettersBytes(bytes: ByteArray?) {
//        assertArrayEquals(
//            "Actual string: ${bytes?.toString(Charsets.UTF_8)}\n Expected string: $lettersString",
//            lettersBytes,
//            bytes
//        )
//    }
//
//    private fun assertIsNumbersBytes(bytes: ByteArray?) {
//        assertArrayEquals(
//            "Actual string: ${bytes?.toString(Charsets.UTF_8)}\n Expected string: $numbersString",
//            numbersBytes,
//            bytes
//        )
//    }
//
//    companion object {
//        private const val lettersString = "abcdefghijklmnopqrstuvwxyz"
//        private const val numbersString = "0123456789"
//        private val lettersBytes = lettersString.toByteArray(Charsets.UTF_8)
//        private val numbersBytes = numbersString.toByteArray(Charsets.UTF_8)
//    }
}
