package io.embrace.android.embracesdk.internal.network.parent

import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.otel.spans.SpanService
import io.embrace.android.embracesdk.internal.utils.concurrent.OneShot
import io.embrace.android.embracesdk.network.NetworkSpanParentToken
import io.opentelemetry.kotlin.tracing.SpanContext

internal class NetworkSpanParentServiceImpl(
    private val spanService: SpanService,
    private val errorHandler: ErrorHandler,
    private val currentUserSessionIdProvider: () -> String,
) : NetworkSpanParentService {

    private val lock = Any()

    @Volatile
    private var active: ActiveParent? = null

    private val reportRejectedOnce = OneShot()

    override fun set(spanContext: SpanContext?): NetworkSpanParentToken {
        val trackedSpan = spanContext?.takeIf { it.isValid }?.let { spanService.getSpan(it.spanId) }
        if (spanContext == null || !spanContext.isValid || trackedSpan?.isRecording == false) {
            reportRejectedOnce {
                errorHandler.log("Rejected an invalid or ended network span parent")
            }
            return NoopNetworkSpanParentToken
        }
        val parent = ActiveParent(
            spanContext = spanContext,
            trackedSpanId = trackedSpan?.let { spanContext.spanId },
            userSessionId = currentUserSessionIdProvider(),
        )
        synchronized(lock) {
            active = parent
        }
        // a span that stops between the check above and becoming the parent is never seen by onSpanStopped
        if (trackedSpan?.isRecording == false) {
            clearIfActive(parent)
        }
        return parent
    }

    override fun clear(token: NetworkSpanParentToken): Boolean = clearIfActive(token)

    // no check-then-act, so the lock isn't needed
    override fun clear() {
        active = null
    }

    override fun currentParent(): SpanContext? = active?.spanContext

    override fun onSpanStopped(spanId: String) {
        val parent = active ?: return
        if (parent.trackedSpanId == spanId) {
            clearIfActive(parent)
        }
    }

    override fun onUserSessionEnded(userSessionId: String) {
        val parent = active ?: return
        if (parent.userSessionId == userSessionId || parent.userSessionId.isEmpty()) {
            clearIfActive(parent)
        }
    }

    private fun clearIfActive(token: NetworkSpanParentToken): Boolean {
        if (active !== token) {
            return false
        }
        synchronized(lock) {
            if (active !== token) {
                return false
            }
            active = null
            return true
        }
    }

    private class ActiveParent(
        val spanContext: SpanContext,
        val trackedSpanId: String?,
        val userSessionId: String,
    ) : NetworkSpanParentToken
}
