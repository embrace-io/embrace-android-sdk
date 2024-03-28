package io.embrace.android.embracesdk.utils

import android.os.Handler
import android.os.Looper
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger

internal object ThreadUtils {

    private val mainLooper = Looper.getMainLooper()
    private val mainThread = mainLooper.thread

    fun runOnMainThread(logger: InternalEmbraceLogger, runnable: Runnable) {
        val wrappedRunnable = Runnable {
            try {
                runnable.run()
            } catch (ex: Exception) {
                logger.logError("Failed to run wrapped runnable on Main thread.", ex)
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
