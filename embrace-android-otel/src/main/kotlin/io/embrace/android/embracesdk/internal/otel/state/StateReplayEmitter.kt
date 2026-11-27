package io.embrace.android.embracesdk.internal.otel.state

import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import io.embrace.android.embracesdk.internal.state.AppStateKeys
import io.embrace.android.embracesdk.internal.state.StateChange
import io.embrace.android.embracesdk.internal.state.StateReplay
import io.embrace.android.embracesdk.internal.state.StateSnapshot
import io.embrace.android.embracesdk.internal.telemetry.AppliedLimitType
import io.embrace.android.embracesdk.internal.telemetry.InternalTelemetryService
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.opentelemetry.kotlin.attributes.AttributesMutator
import io.opentelemetry.kotlin.logging.Logger
import io.opentelemetry.kotlin.logging.SeverityNumber
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Replays state for the app's own exporters, in the form OpenTelemetry uses for events: a log record named
 * `emb.app.state.change` for each change made in a session part, with the value the state had before, and one named
 * `emb.app.state.snapshot` with every state's value when a session part starts.
 *
 * The recorder hands these over under its lock, so they are only queued here and built on [worker]. At most [maxQueued] wait at
 * once: one that finds as many waiting is left out, and the numbers on the events then skip.
 *
 * The records are not sent to Embrace, whose payload has the change log, and carry no session ids: the sequence and the process
 * identifier are what tie them to other telemetry.
 */
class StateReplayEmitter(
    private val loggerProvider: Provider<Logger>,
    private val worker: BackgroundWorker,
    private val processIdentifier: Provider<String>,
    private val stateTelemetry: StateTelemetry,
    private val errorHandler: ErrorHandler,
    private val telemetryService: InternalTelemetryService,
    private val maxQueued: Int = MAX_QUEUED_EVENTS,
) : StateReplay {

    private val queue = ConcurrentLinkedQueue<Pending>()
    private val queued = AtomicInteger()
    private val draining = AtomicBoolean()

    override fun onPartOpened(snapshot: StateSnapshot, timeMs: Long) {
        // a part that starts before any state has a value has nothing to say, as a log made then carries no stamp
        if (snapshot.size > 0) {
            enqueue(Pending(snapshot, timeMs, null))
        }
    }

    override fun onChange(change: StateChange) {
        enqueue(Pending(change.after, change.timeMs, change))
    }

    private fun enqueue(pending: Pending) {
        if (queued.get() >= maxQueued) {
            telemetryService.trackAppliedLimit(LIMIT_TELEMETRY_TYPE, AppliedLimitType.DROP)
            return
        }
        queued.incrementAndGet()
        queue.add(pending)
        if (draining.compareAndSet(false, true)) {
            val scheduled = errorHandler.guardSdkCode(SdkErrorType.StateReplayFail, false) {
                worker.submit(::drain)
                true
            }
            if (!scheduled) {
                // nothing will drain the queue for this attempt, so let the next event try again
                draining.set(false)
            }
        }
    }

    private fun drain() {
        // cleared first, so that an event queued while this runs schedules another drain rather than being left behind
        draining.set(false)
        while (true) {
            val pending = queue.poll() ?: break
            queued.decrementAndGet()
            errorHandler.guardSdkCode(SdkErrorType.StateReplayFail) {
                emit(pending)
            }
        }
    }

    private fun emit(pending: Pending) {
        val change = pending.change
        val timeNanos = pending.timeMs.millisToNanos()
        loggerProvider().emit(
            eventName = if (change == null) AppStateKeys.SNAPSHOT_EVENT_NAME else AppStateKeys.CHANGE_EVENT_NAME,
            timestamp = timeNanos,
            observedTimestamp = timeNanos,
            severityNumber = SeverityNumber.INFO,
            severityText = SeverityNumber.INFO.name,
        ) {
            // the change's own attributes go first, so that where one has the key of an attribute set below, that one is sent
            change?.attributes?.forEach { (key, value) -> setStringAttribute(key, value) }
            setStringAttribute(EmbType.State.key, EmbType.State.value)
            setLongAttribute(AppStateKeys.SEQ, change?.seq ?: pending.snapshot.seq)
            setStringAttribute(AppStateKeys.ENTITY_TYPE, AppStateKeys.ENTITY_TYPE_PROCESS)
            setStringAttribute(AppStateKeys.ENTITY_ID, processIdentifier())
            if (change != null) {
                setChange(change)
            }
            if (change == null || stateTelemetry.isVerbose()) {
                setValues(pending.snapshot)
            }
        }
    }

    private fun AttributesMutator.setChange(change: StateChange) {
        setStringAttribute(AppStateKeys.NAME, change.definition.name)
        setStringAttribute(AppStateKeys.NEW_VALUE, change.value)
        if (change.system) {
            setStringAttribute(AppStateKeys.NEW_VALUE_TYPE, AppStateKeys.VALUE_TYPE_SYSTEM)
        }
        val previous = change.previousValue ?: return
        setStringAttribute(AppStateKeys.PREVIOUS_VALUE, previous)
        if (change.previousSystem) {
            setStringAttribute(AppStateKeys.PREVIOUS_VALUE_TYPE, AppStateKeys.VALUE_TYPE_SYSTEM)
        }
    }

    private fun AttributesMutator.setValues(snapshot: StateSnapshot) {
        for (i in 0 until snapshot.size) {
            val definition = snapshot.definition(i)
            setStringAttribute(definition.valueKey, snapshot.value(i))
            if (snapshot.isSystem(i)) {
                setStringAttribute(definition.valueTypeKey, AppStateKeys.VALUE_TYPE_SYSTEM)
            }
        }
    }

    /**
     * A change waiting to be replayed, or the start of a session part when [change] is null. [snapshot] is every state's value
     * after the change, or as the part started.
     */
    private class Pending(val snapshot: StateSnapshot, val timeMs: Long, val change: StateChange?)

    private companion object {
        const val MAX_QUEUED_EVENTS = 1000
        const val LIMIT_TELEMETRY_TYPE = "state_replay_event"
    }
}
