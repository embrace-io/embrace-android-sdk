package io.embrace.android.embracesdk.internal.capture.state

import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.otel.state.StateAttributes
import io.embrace.android.embracesdk.internal.otel.state.StateEventLogger
import io.embrace.android.embracesdk.internal.session.persistence.StateRecord
import io.embrace.android.embracesdk.internal.session.persistence.StateValue
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.opentelemetry.kotlin.attributes.AttributesMutator
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Emits what a [StateRecorder] records as events for an app's own OpenTelemetry log exporters and log record
 * processors: one for each state change a session part lists, as it is made, which also carries the value the state had
 * before, and one with every state's value when a session part starts. A change made with no session part in progress
 * has no event, and the values after it are in the event for the next part's start.
 * Whatever receives them can replay the changes, and so resolve the sequence numbers a span carries. None of it is part
 * of Embrace's own payload, which has the changes as a span of the session part.
 *
 * The recorder calls this under its lock, so all that happens there is that the event is queued. Events are built and
 * emitted on [worker], one at a time and in the order they were queued, which is the order of the sequence. An app's log
 * record processor runs where an event is emitted, so [worker] should be one that nothing else waits on.
 *
 * Once [worker] turns a pass away it has shut down with the SDK, and nothing is queued from then on. A pass it had
 * already taken still emits what was queued before.
 *
 * At most [maxPendingEvents] events wait to be emitted. Each one holds the value of every state, and an app's log
 * record processor can take as long as it likes over an event, so without a limit a slow one would keep an unbounded
 * amount of the past in memory. An event that arrives when that many are waiting is left out and counted. Whatever
 * receives the events sees the sequence numbers skip, as it does where a limit refused a change, which the recorder
 * does not report here.
 *
 * @param includeValues whether the event for a change also carries the value of every state after that change.
 * @param maxPendingEvents the most events that wait to be emitted.
 */
class StateEventEmitter(
    private val logger: StateEventLogger,
    private val worker: BackgroundWorker,
    private val processIdentifierProvider: () -> String,
    private val includeValues: Boolean,
    private val maxPendingEvents: Int = MAX_PENDING_EVENTS,
) : StateChangeListener {

    private val pending = ConcurrentLinkedQueue<PendingEvent>()
    private val pendingCount = AtomicInteger(0)
    private val droppedCount = AtomicInteger(0)
    private val drainScheduled = AtomicBoolean(false)
    private val drainLock = Any()
    private val drain = Runnable(::emitPending)

    @Volatile
    private var workerShutDown = false

    override fun onChange(record: StateRecord, snapshot: StateSnapshot, previous: StateValue?) {
        queue(PendingEvent(record.time_ms, snapshot, record, previous))
    }

    override fun onPartStart(timeMs: Long, snapshot: StateSnapshot) {
        queue(PendingEvent(timeMs, snapshot, null, null))
    }

    /**
     * How many events are waiting to be emitted.
     */
    internal fun pendingEvents(): Int = pendingCount.get()

    /**
     * How many events were left out because [maxPendingEvents] were already waiting.
     */
    internal fun droppedEvents(): Int = droppedCount.get()

    private fun queue(event: PendingEvent) {
        if (workerShutDown) {
            return
        }
        if (pendingCount.incrementAndGet() > maxPendingEvents) {
            pendingCount.decrementAndGet()
            droppedCount.incrementAndGet()
            return
        }
        pending.add(event)
        if (drainScheduled.compareAndSet(false, true) && !submitDrain()) {
            // The worker has shut down with the SDK, and nothing is left to export to. This event is taken back, so that
            // nothing is kept that will never be emitted. Any queued before it are left for a pass already under way.
            workerShutDown = true
            if (pending.remove(event)) {
                pendingCount.decrementAndGet()
            }
            drainScheduled.set(false)
        }
    }

    /**
     * Asks [worker] for a pass, and says whether it took it. A worker that has shut down either throws or, as the SDK's
     * own does, hands back a task it has already cancelled.
     */
    private fun submitDrain(): Boolean = try {
        !worker.submit(drain).isCancelled
    } catch (ignored: RejectedExecutionException) {
        false
    }

    private fun emitPending() {
        // cleared before the queue is read, so an event queued from here on schedules the next pass if this one misses it
        drainScheduled.set(false)
        synchronized(drainLock) {
            while (true) {
                val event = pending.poll() ?: break
                pendingCount.decrementAndGet()
                try {
                    emit(event)
                } catch (ignored: Throwable) {
                    // an event that cannot be emitted is left out, and the ones behind it still go
                }
            }
        }
    }

    private fun emit(event: PendingEvent) {
        val change = event.change?.change
        val value = change?.value_
        if (change == null || value == null) {
            logger.emit(StateAttributes.SNAPSHOT_EVENT, event.timeMs.millisToNanos()) {
                describeProcessState(event.snapshot.sequence)
                setValues(event.snapshot)
            }
            return
        }
        logger.emit(StateAttributes.CHANGE_EVENT, event.timeMs.millisToNanos()) {
            // the change's own attributes first, so that none of them can stand in for one the SDK sets
            change.attributes.forEach { (key, attribute) -> setStringAttribute(key, attribute) }
            describeProcessState(event.change.sequence)
            setStringAttribute(StateAttributes.NAME, value.state)
            setStringAttribute(StateAttributes.NEW_VALUE, value.value_)
            if (value.system) {
                setStringAttribute(StateAttributes.NEW_VALUE_TYPE, StateAttributes.SYSTEM)
            }
            // the value the state is leaving, which the change's own attributes describe: absent for a state's first change
            event.previous?.let { previous ->
                setStringAttribute(StateAttributes.PREVIOUS_VALUE, previous.value_)
                if (previous.system) {
                    setStringAttribute(StateAttributes.PREVIOUS_VALUE_TYPE, StateAttributes.SYSTEM)
                }
            }
            if (includeValues) {
                // from the snapshot this change produced, which is not the current one by the time this runs
                setValues(event.snapshot)
            }
        }
    }

    private fun AttributesMutator.describeProcessState(sequence: Long) {
        setStringAttribute(EmbType.State.key, EmbType.State.value)
        setLongAttribute(StateAttributes.SEQUENCE, sequence)
        setStringAttribute(StateAttributes.ENTITY_TYPE, StateAttributes.PROCESS_ENTITY)
        setStringAttribute(StateAttributes.ENTITY_ID, processIdentifierProvider())
    }

    private fun AttributesMutator.setValues(snapshot: StateSnapshot) {
        snapshot.attributes.forEach { (key, attribute) -> setStringAttribute(key, attribute) }
    }

    /**
     * An event waiting to be emitted: a state change if it has a [change], and the start of a session part if not.
     * [snapshot] is the value of every state after the change, or when the part started.
     */
    private class PendingEvent(
        val timeMs: Long,
        val snapshot: StateSnapshot,
        val change: StateRecord?,
        val previous: StateValue?,
    )

    private companion object {
        /**
         * The most events that wait to be emitted, by default. It is a tenth of the changes a session part's journal
         * holds.
         */
        const val MAX_PENDING_EVENTS = 1_000
    }
}
