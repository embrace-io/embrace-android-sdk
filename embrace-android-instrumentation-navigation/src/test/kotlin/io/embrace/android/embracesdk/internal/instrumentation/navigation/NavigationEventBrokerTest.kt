package io.embrace.android.embracesdk.internal.instrumentation.navigation

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal class NavigationEventBrokerTest {
    private val states = CopyOnWriteArrayList<String>()
    private lateinit var broker: NavigationEventBroker

    @Before
    fun setUp() {
        states.clear()
        broker = NavigationEventBroker { route ->
            states.add(route)
        }
    }

    @Test
    fun `events processed serially and in order`() {
        val latch = CountDownLatch(1)
        broker.queueEvent(NavigationEvent.ActivityStarted("home"))
        broker.queueEvent(NavigationEvent.ActivityStarted("settings"))
        Thread {
            Thread.sleep(20)
            broker.queueEvent(NavigationEvent.Backgrounded)
            latch.countDown()
        }.start()
        latch.await(1, TimeUnit.SECONDS)
        broker.queueEvent(NavigationEvent.ActivityStarted("profile"))

        assertEquals(listOf("home", "settings", NavigationEvent.Backgrounded.name, "profile"), states)
    }

    @Test
    fun `duplicate events are dropped`() {
        broker.queueEvent(NavigationEvent.ActivityStarted("home"))
        broker.queueEvent(NavigationEvent.ActivityStarted("home"))
        broker.queueEvent(NavigationEvent.Backgrounded)
        broker.queueEvent(NavigationEvent.Backgrounded)

        assertEquals(listOf("home", NavigationEvent.Backgrounded.name), states)
    }
}
