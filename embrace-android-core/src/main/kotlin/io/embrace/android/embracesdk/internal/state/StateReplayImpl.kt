package io.embrace.android.embracesdk.internal.state

import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.StateKeys
import io.embrace.android.embracesdk.internal.otel.logs.EXPORT_ONLY_KEY
import io.embrace.android.embracesdk.internal.session.persistence.StateChangeRecord
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.opentelemetry.kotlin.attributes.AttributesMutator
import io.opentelemetry.kotlin.logging.Logger
import io.opentelemetry.kotlin.logging.SeverityNumber
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Replays state to a customer's exporters as OpenTelemetry events: a log record named `emb.app.state.change` for each
 * change made in a session part that a limit did not refuse, and one named `emb.app.state.snapshot` with every state's
 * value when a session part starts. Embrace's own payload never holds them.
 *
 * The recorder hands an event over under its lock, so this only queues it, at most [maxPending] at a time, and builds
 * the records on the [worker]. An event that finds as many waiting is left out, and the numbers on the events skip.
 */
internal class StateReplayImpl(
    private val worker: BackgroundWorker,
    private val loggerSupplier: () -> Logger,
    private val processIdentifier: String,
    private val verbose: Boolean,
    private val maxPending: Int = MAX_PENDING_EVENTS,
) : StateReplay {

    private sealed class Event {
        class Change(val change: StateChangeRecord, val previous: StateSnapshot, val after: StateSnapshot) : Event()
        class Snapshot(val snapshot: StateSnapshot, val timeMs: Long) : Event()
    }

    private val pending = ConcurrentLinkedQueue<Event>()
    private val pendingCount = AtomicInteger(0)
    private val drainScheduled = AtomicBoolean(false)

    override fun onPartStarted(snapshot: StateSnapshot, timeMs: Long) = enqueue(Event.Snapshot(snapshot, timeMs))

    override fun onChange(change: StateChangeRecord, previous: StateSnapshot, after: StateSnapshot) =
        enqueue(Event.Change(change, previous, after))

    private fun enqueue(event: Event) {
        if (pendingCount.incrementAndGet() > maxPending) {
            pendingCount.decrementAndGet()
            return
        }
        pending.add(event)
        if (drainScheduled.compareAndSet(false, true)) {
            worker.submit(::drain)
        }
    }

    private fun drain() {
        drainScheduled.set(false)
        while (true) {
            val event = pending.poll() ?: return
            pendingCount.decrementAndGet()
            runCatching { emit(event) }
        }
    }

    private fun emit(event: Event) {
        val timeNanos = TimeUnit.MILLISECONDS.toNanos(
            when (event) {
                is Event.Change -> event.change.time_ms
                is Event.Snapshot -> event.timeMs
            },
        )
        loggerSupplier().emit(
            eventName = if (event is Event.Change) StateKeys.CHANGE_EVENT else StateKeys.SNAPSHOT_EVENT,
            timestamp = timeNanos,
            observedTimestamp = timeNanos,
            severityNumber = SeverityNumber.INFO,
            severityText = "INFO",
        ) {
            setStringAttribute(EXPORT_ONLY_KEY, "true")
            setStringAttribute(EmbType.State.key, EmbType.State.value)
            setStringAttribute(StateKeys.ENTITY_TYPE, StateKeys.ENTITY_TYPE_PROCESS)
            setStringAttribute(StateKeys.ENTITY_ID, processIdentifier)
            when (event) {
                is Event.Change -> change(event)
                is Event.Snapshot -> {
                    if (event.snapshot.seq > 0) {
                        setLongAttribute(StateKeys.SEQ, event.snapshot.seq)
                    }
                    values(event.snapshot)
                }
            }
        }
    }

    private fun AttributesMutator.change(event: Event.Change) {
        val change = event.change
        // the change's own attributes go first, so that the SDK's value wins where one shares a key with them
        change.attributes.forEach { (key, value) -> setStringAttribute(key, value) }
        setLongAttribute(StateKeys.SEQ, change.seq)
        setStringAttribute(StateKeys.NAME, change.name)
        setStringAttribute(StateKeys.NEW_VALUE, change.state_value)
        if (change.system) {
            setStringAttribute(StateKeys.NEW_VALUE_TYPE, StateKeys.SYSTEM)
        }
        val previousIndex = event.previous.indexOf(change.name)
        if (previousIndex >= 0) {
            setStringAttribute(StateKeys.PREVIOUS_VALUE, event.previous.value(previousIndex))
            if (event.previous.isSystem(previousIndex)) {
                setStringAttribute(StateKeys.PREVIOUS_VALUE_TYPE, StateKeys.SYSTEM)
            }
        }
        if (verbose) {
            values(event.after)
        }
    }

    private fun AttributesMutator.values(snapshot: StateSnapshot) {
        for (index in 0 until snapshot.size) {
            val name = snapshot.name(index)
            setStringAttribute(StateKeys.value(name), snapshot.value(index))
            if (snapshot.isSystem(index)) {
                setStringAttribute(StateKeys.valueType(name), StateKeys.SYSTEM)
            }
        }
    }

    private companion object {
        private const val MAX_PENDING_EVENTS = 1000
    }
}
