package io.embrace.android.embracesdk.internal.arch.datasource

interface StateToken {
    fun update(timestampMs: Long, newValue: String)

    fun end(timestampMs: Long)
}
