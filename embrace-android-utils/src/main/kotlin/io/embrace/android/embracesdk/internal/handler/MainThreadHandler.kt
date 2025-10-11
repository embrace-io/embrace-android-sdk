package io.embrace.android.embracesdk.internal.handler

import android.os.Handler
import android.os.Message

interface MainThreadHandler {
    val wrappedHandler: Handler

    fun postAtFrontOfQueue(function: () -> Unit)
    fun postDelayed(runnable: Runnable, delayMillis: Long)
    fun sendMessageAtFrontOfQueue(message: Message): Boolean
}
