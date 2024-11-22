package io.embrace.android.embracesdk.internal.handler

import android.os.Handler
import android.os.Looper

class AndroidMainThreadHandler : MainThreadHandler {
    val handler = Handler(checkNotNull(Looper.getMainLooper()))

    override fun postAtFrontOfQueue(function: () -> Unit) {
        handler.postAtFrontOfQueue(function)
    }

    override fun postDelayed(runnable: Runnable, delayMillis: Long) {
        handler.postDelayed(runnable, delayMillis)
    }
}
