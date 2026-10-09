package io.embrace.android.embracesdk.internal.logging

import io.embrace.android.embracesdk.internal.arch.datasource.LogSeverity
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.clock.Clock
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

class BufferedInternalErrorHandler(
    private val clock: Clock,
    private val limit: Int = 10,
) : InternalErrorHandler {

    private class BufferedError(val timestampMs: Long, val throwable: Throwable)

    private val buffer = ConcurrentLinkedQueue<BufferedError>()
    private val count = AtomicInteger(0)

    @Volatile
    private var destination: TelemetryDestination? = null

    @Volatile
    private var enabled = true

    override fun trackInternalError(type: InternalErrorType, throwable: Throwable) {
        runCatching {
            if (!type.shouldCapture() || !enabled || count.incrementAndGet() > limit) {
                return
            }
            val dst = destination
            if (dst != null) {
                dst.addInternalError(throwable, null)
                return
            }
            buffer.add(BufferedError(clock.now(), throwable))
            destination?.let(::drain) // destination may have been set after the null check
        }
    }

    fun drainTo(destination: TelemetryDestination, enabled: Boolean) {
        this.enabled = enabled
        if (enabled) {
            this.destination = destination
            drain(destination)
        } else {
            buffer.clear()
        }
    }

    fun resetLimit() {
        count.set(0)
    }

    private fun drain(destination: TelemetryDestination) {
        while (true) {
            val error = buffer.poll() ?: return
            try {
                destination.addInternalError(error.throwable, error.timestampMs)
            } catch (ignored: Throwable) {
            }
        }
    }

    private fun TelemetryDestination.addInternalError(throwable: Throwable, timestampMs: Long?) {
        addLog(SchemaType.InternalError(throwable), LogSeverity.ERROR, "", isPrivate = true, timestampMs = timestampMs)
    }
}
