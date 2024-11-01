package io.embrace.android.embracesdk.internal.utils

import android.os.Handler
import android.os.Looper

object ThreadUtils {

    private val mainLooper = Looper.getMainLooper()
    private val mainThread = mainLooper.thread

    fun runOnMainThread(runnable: Runnable) {
        val wrappedRunnable = Runnable {
            runCatching {
                runnable.run()
            }
        }
        if (Thread.currentThread() !== mainThread) {
            val mainHandler = Handler(mainLooper)
            mainHandler.post(wrappedRunnable)
        } else {
            wrappedRunnable.run()
        }
    }
}
