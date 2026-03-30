package io.embrace.android.embracesdk.internal.instrumentation.navigation

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Receives instance of [NavigationEvent] from various sources, processes them serially, and update the
 */
internal class NavigationEventBroker(
    private val onScreenLoad: (newScreenName: String) -> Unit,
) {
    private val eventQueue = ConcurrentLinkedQueue<NavigationEvent>()
    private val isProcessing = AtomicBoolean(false)
    private val lastEvent = AtomicReference<NavigationEvent>(null)

    fun queueEvent(event: NavigationEvent) {
        eventQueue.add(event)
        processEvents()
    }

    private fun processEvents() {
        if (isProcessing.compareAndSet(false, true)) {
            try {
                var event = eventQueue.poll()
                while (event != null) {
                    processEvent(event)
                    event = eventQueue.poll()
                }
            } finally {
                isProcessing.set(false)
            }

            if (eventQueue.isNotEmpty()) {
                processEvents()
            }
        }
    }

    private fun processEvent(event: NavigationEvent) {
        if (event != lastEvent.getAndSet(event)) {
            onScreenLoad(event.name)
        }
    }
}
