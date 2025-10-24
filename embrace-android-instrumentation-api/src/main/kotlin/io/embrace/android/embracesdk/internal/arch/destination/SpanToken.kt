package io.embrace.android.embracesdk.internal.arch.destination

interface SpanToken {
    fun stop(endTimeMs: Long? = null)
}
