package io.embrace.android.embracesdk.internal.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

internal class CollectionExtensionsKtTest {

    @Test
    fun testSafeGet() {
        val list = listOf("a", "b", "c")
        assertEquals("a", list.at(0))
        assertEquals("c", list.at(2))
        assertNull(list.at(-1))
        assertNull(list.at(100))
    }

    @Test
    fun `verify lockAndRun`() {
        val threadpool = Executors.newFixedThreadPool(3)
        val expectedOrder = arrayListOf("third", "first", "second")
        val key1 = "abc"
        val key2 = "def"
        val firstTaskLatch = CountDownLatch(1)
        val finalTaskLatch = CountDownLatch(1)
        val gatingLatch = CountDownLatch(1)
        val locks: MutableMap<String, AtomicInteger> = ConcurrentHashMap()
        val completedTasks: MutableList<String> = CopyOnWriteArrayList()

        threadpool.submit {
            locks.lockAndRun(key1) {
                // Ensure the lock is acquired before submitting another task that tries to acquire the lock
                threadpool.submit {
                    locks.lockAndRun(key1) {
                        completedTasks.add("second")
                        finalTaskLatch.countDown()
                    }
                }
                firstTaskLatch.await(1, TimeUnit.SECONDS)
                completedTasks.add("first")
            }
        }

        threadpool.submit {
            // This should run before any checks take place
            locks.lockAndRun(key2) {
                completedTasks.add("third")
                gatingLatch.countDown()
            }
        }

        gatingLatch.await(1, TimeUnit.SECONDS)
        firstTaskLatch.countDown()
        finalTaskLatch.await(1, TimeUnit.SECONDS)
        assertEquals("Execution count wrong. Order found: $completedTasks", 3, completedTasks.size)

        // "third" should be first in the results array because there is nothing holding the lock for the key "abc"
        // "first" should be second in the results array because it needs to wait for the latch to be released by "third"
        // "second should be last because it needs the lock for the key "def" which is held by "first", so it will run after it

        completedTasks.forEachIndexed { index, task ->
            assertEquals("Execution order wrong. Order found: $completedTasks", expectedOrder[index], task)
        }
    }

    @Test
    fun `drain removes and returns all elements in queue order`() {
        val queue = ConcurrentLinkedQueue(listOf("a", "b", "c"))

        assertEquals(listOf("a", "b", "c"), queue.drain())
        assertTrue(queue.isEmpty())
        assertEquals(emptyList<String>(), queue.drain())

        queue.add("d")
        assertEquals(listOf("d"), queue.drain())
    }

    @Test
    fun `elements added during a drain are returned or retained but never dropped`() {
        // Add logic to the polling to modify the queue to ensure modifications during iteration and processed in the expected way.
        var addedOnEmpty = false
        val queue = object : ConcurrentLinkedQueue<String>(listOf("a", "b")) {
            override fun poll(): String? {
                val polled = super.poll()
                if (polled == "a") {
                    add("c")
                } else if (polled == null && !addedOnEmpty) {
                    addedOnEmpty = true
                    add("d")
                }
                return polled
            }
        }

        // "c" is enqueued before the drain is finished, so it is part of this drain
        assertEquals(listOf("a", "b", "c"), queue.drain())

        // "d" is enqueued right after the drains sees the queue being empty, so it is retained for the next one rather than dropped
        assertEquals(listOf("d"), queue.toList())
    }
}
