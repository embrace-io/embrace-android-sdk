package io.embrace.android.embracesdk.internal.instrumentation.startup.ui

import android.app.Activity
import android.os.Build.VERSION_CODES.M
import android.os.Message
import androidx.annotation.RequiresApi
import io.embrace.android.embracesdk.internal.handler.MainThreadHandler

@RequiresApi(M)
internal class HandlerMessageDrawDetector(
    private val handler: MainThreadHandler,
) : DrawEventEmitter {

    @Volatile
    private var closed = false

    override fun registerFirstDrawCallback(
        activity: Activity,
        drawBeginCallback: () -> Unit,
        drawCompleteCallback: () -> Unit,
    ) {
        if (closed) {
            return
        }
        drawBeginCallback()
        handler.sendMessageAtFrontOfQueue(
            Message.obtain(handler.wrappedHandler) {
                if (!closed) {
                    drawCompleteCallback()
                }
            }.apply {
                isAsynchronous = true
            },
        )
    }

    override fun unregisterFirstDrawCallback(activity: Activity) {
    }

    override fun close() {
        closed = true
    }
}
