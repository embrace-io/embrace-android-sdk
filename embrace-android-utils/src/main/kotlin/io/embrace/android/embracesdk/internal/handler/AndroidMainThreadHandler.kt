package io.embrace.android.embracesdk.internal.handler

import android.os.Handler
import android.os.Looper
import android.os.Message
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode

class AndroidMainThreadHandler(private val errorHandler: ErrorHandler) : MainThreadHandler {

    override val wrappedHandler: Handler = object : Handler(checkNotNull(Looper.getMainLooper())) {
        override fun dispatchMessage(msg: Message) {
            errorHandler.guardSdkCode(SdkErrorType.UiCallbackFail) { super.dispatchMessage(msg) }
        }
    }

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
