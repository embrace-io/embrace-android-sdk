package io.embrace.android.embracesdk.internal.handler

import android.os.Handler
import android.os.Looper
import android.os.Message

class AndroidMainThreadHandler : MainThreadHandler {
    override val wrappedHandler = Handler(checkNotNull(Looper.getMainLooper()))

    override fun postAtFrontOfQueue(function: () -> Unit) {
        wrappedHandler.postAtFrontOfQueue(function)
    }

    override fun postDelayed(runnable: Runnable, delayMillis: Long) {
        wrappedHandler.postDelayed(runnable, delayMillis)
    }

    override fun sendMessageAtFrontOfQueue(message: Message): Boolean {
        return wrappedHandler.sendMessageAtFrontOfQueue(message)
    }
}
