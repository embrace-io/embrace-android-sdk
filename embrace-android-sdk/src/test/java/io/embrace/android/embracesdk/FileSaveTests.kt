package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.comms.delivery.CacheService
import io.embrace.android.embracesdk.comms.delivery.EmbraceCacheService
import io.embrace.android.embracesdk.concurrency.SingleThreadTestScheduledExecutor
import io.embrace.android.embracesdk.fakes.FakeStorageService
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

internal class FileSaveTests {

    private lateinit var service: CacheService
    private lateinit var storageManager: FakeStorageService
    private val serializer = EmbraceSerializer()

    @Before
    fun setUp() {
        storageManager = FakeStorageService()
        service = EmbraceCacheService(
            storageManager,
            serializer,
            InternalEmbraceLogger()
        )
    }

    @Test
    fun `concurrent writes should lead to last finished written bytes to be persisted`() {
        val filename = "testfile-pause"
        val thread1 = SingleThreadTestScheduledExecutor()
        val thread2 = SingleThreadTestScheduledExecutor()

        val latch = CountDownLatch(2)
        thread1.submit {
            service.cacheBytes(filename, lettersBytes)
            latch.countDown()
        }
        thread2.submit {
            Thread.sleep(50)
            service.cacheBytes(filename, numbersBytes)
            latch.countDown()
        }

        latch.await(5, TimeUnit.SECONDS)
        val loadedObject = service.loadBytes(filename)

        // Failed - file contains contents from both writes, e.g. 0123456789klmnopqrstuvwxyz
        assertArrayEquals(
            "error! actual: ${loadedObject?.toString(StandardCharsets.UTF_8)}\n expected: $numbersString",
            numbersBytes,
            loadedObject
        )
    }

    @Test
    fun `partial writes aborted due to exception does not leave partially written files`() {
        val filename = "testfile-truncate"
        service.cacheBytes(filename, lettersBytes)

        val loadedObject = service.loadBytes(filename)

        // Failure - whatever gets written is read
        assertNull(loadedObject?.toString(Charsets.UTF_8))
    }

    @Test
    fun `reading a file that is being written to should block and succeed`() {
        val filename = "testfile-pause"
        val loadedObject = AtomicReference<ByteArray?>()
        val thread1 = SingleThreadTestScheduledExecutor()
        val thread2 = SingleThreadTestScheduledExecutor()

        val latch = CountDownLatch(2)

        thread1.submit {
            service.cacheBytes(filename, lettersBytes)
            latch.countDown()
        }

        thread2.submit {
            Thread.sleep(50)
            val loadedBytes = service.loadBytes(filename)
            loadedObject.set(loadedBytes)
            latch.countDown()
        }

        latch.await(1, TimeUnit.SECONDS)

        // Failure - no bytes read
        assertArrayEquals(
            "error! actual: ${loadedObject.get()?.toString(StandardCharsets.UTF_8)}\n expected: $lettersString",
            lettersBytes,
            loadedObject.get()
        )
    }

    @Test
    fun `interrupting a write should not leave partially written files`() {
        val filename = "testfile-pause"
        val thread1 = SingleThreadTestScheduledExecutor()
        val thread2 = SingleThreadTestScheduledExecutor()
        val latch = CountDownLatch(2)
        thread1.submit {
            service.cacheBytes(filename, lettersBytes)
            latch.countDown()
        }
        thread2.submit {
            thread1.shutdownNow()
            latch.countDown()
        }

        latch.await(1, TimeUnit.SECONDS)
        val loadedObject = service.loadBytes(filename)

        // Failed: what was written already is read
        assertNull(loadedObject)
    }

    @Test
    fun `paused write eventually succeeds`() {
        val filename = "testfile-pause"
        val thread1 = SingleThreadTestScheduledExecutor()
        val latch = CountDownLatch(1)
        thread1.submit {
            service.cacheBytes(filename, lettersBytes)
            latch.countDown()
        }

        latch.await(2, TimeUnit.SECONDS)
        val loadedObject = service.loadBytes(filename)

        // This works
        assertArrayEquals(
            "error! actual: ${loadedObject?.toString(StandardCharsets.UTF_8)}\n expected: $lettersString",
            lettersBytes,
            loadedObject
        )
    }

    companion object {
        private const val lettersString = "abcdefghijklmnopqrstuvwxyz"
        private const val numbersString = "0123456789"
        private val lettersBytes = lettersString.toByteArray(Charsets.UTF_8)
        private val numbersBytes = numbersString.toByteArray(Charsets.UTF_8)
    }
}
