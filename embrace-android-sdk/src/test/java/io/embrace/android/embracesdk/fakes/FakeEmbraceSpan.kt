package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.sdk.trace.IdGenerator

internal class FakeEmbraceSpan private constructor(
    override val parent: EmbraceSpan?
) : EmbraceSpan {

    private var started = false
    private var stopped = false
    private var errorCode: ErrorCode? = null

    override var traceId: String? = parent?.traceId

    override var spanId: String? = null
    override val isRecording: Boolean
        get() = started && !stopped

    override fun start(): Boolean {
        if (!started) {
            spanId = IdGenerator.random().generateSpanId()
            if (parent == null) {
                traceId = IdGenerator.random().generateTraceId()
            }
            started = true
        }
        return true
    }

    override fun stop(): Boolean {
        stop(errorCode = null)
        return true
    }

    override fun stop(errorCode: ErrorCode?): Boolean {
        if (!stopped) {
            this.errorCode = errorCode
            stopped = true
        }
        return true
    }

    override fun addEvent(name: String): Boolean {
        TODO("Not yet implemented")
    }

    override fun addEvent(name: String, time: Long?, attributes: Map<String, String>?): Boolean {
        TODO("Not yet implemented")
    }

    override fun addAttribute(key: String, value: String): Boolean {
        TODO("Not yet implemented")
    }

    companion object {
        fun notStarted(parent: EmbraceSpan? = null): FakeEmbraceSpan = FakeEmbraceSpan(parent)

        fun started(parent: EmbraceSpan? = null): FakeEmbraceSpan {
            val span = notStarted(parent)
            span.start()
            return span
        }

        fun stopped(parent: EmbraceSpan? = null): FakeEmbraceSpan {
            val span = started(parent)
            span.stop()
            return span
        }
    }
}
