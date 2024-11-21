package io.embrace.android.embracesdk.internal.handler

interface MainThreadHandler {
    fun postAtFrontOfQueue(function: () -> Unit)
    fun postDelayed(runnable: Runnable, delayMillis: Long)
}
