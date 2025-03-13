package io.embrace.android.embracesdk.internal.ui

import android.app.Activity
import android.os.Build.VERSION_CODES.M
import android.os.Message
import androidx.annotation.RequiresApi
import io.embrace.android.embracesdk.internal.handler.MainThreadHandler

@RequiresApi(M)
class HandlerMessageDrawDetector(
    private val handler: MainThreadHandler
) : DrawEventEmitter {

    override fun registerFirstDrawCallback(
        activity: Activity,
        drawBeginCallback: () -> Unit,
        firstFrameDeliveredCallback: () -> Unit,
    ) {
        drawBeginCallback()
        handler.sendMessageAtFrontOfQueue(
            Message.obtain(handler.wrappedHandler, firstFrameDeliveredCallback).apply {
                isAsynchronous = true
            }
        )
    }

    override fun unregisterFirstDrawCallback(activity: Activity) {
    }
}
