package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.schema.AppTerminationCause
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSdkSpan
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.spans.CurrentSessionPartSpan
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.ErrorCode
import java.util.concurrent.atomic.AtomicInteger

class FakeCurrentSessionPartSpan(
    private val clock: FakeClock = FakeClock(),
) : CurrentSessionPartSpan {
    val addedEvents = mutableListOf<SpanEventData>()
    val attributes = mutableMapOf<String, String>()
    val stoppedSpans = mutableSetOf<String>()
    var initializedCallCount: Int = 0
    var sessionPartSpan: FakeEmbraceSdkSpan? = null

    private val sessionIteration = AtomicInteger(1)

    override fun initializeService(sdkInitStartTimeMs: Long) {
        sessionPartSpan = newSessionPartSpan(sdkInitStartTimeMs)
    }

    override fun current(): EmbraceSdkSpan? {
        return sessionPartSpan
    }

    override fun initialized(): Boolean {
        initializedCallCount++
        return true
    }

    override fun readySession(): Boolean {
        sessionPartSpan = newSessionPartSpan(clock.now())
        return true
    }

    override fun endSession(
        startNewSession: Boolean,
        appTerminationCause: AppTerminationCause?,
    ): List<Span> {
        val endingSessionPartSpan = checkNotNull(sessionPartSpan)
        val errorCode = if (appTerminationCause != null) {
            ErrorCode.FAILURE
        } else {
            null
        }
        endingSessionPartSpan.stop(errorCode, clock.now())
        val payload = listOf(checkNotNull(endingSessionPartSpan.snapshot()))
        sessionIteration.incrementAndGet()
        sessionPartSpan = if (appTerminationCause == null) newSessionPartSpan(clock.now()) else null
        return payload
    }

    override fun canStartNewSpan(parent: EmbraceSpan?, internal: Boolean): Boolean {
        return true
    }

    override fun getId(): String {
        return "testSessionId$sessionIteration"
    }

    override fun spanStopCallback(spanId: String) {
        stoppedSpans.add(spanId)
    }

    private fun newSessionPartSpan(startTimeMs: Long) =
        FakeEmbraceSdkSpan.sessionPartSpan(
            userSessionId = "fake-session-span-id",
            startTimeMs = startTimeMs,
            lastHeartbeatTimeMs = startTimeMs
        )
}
