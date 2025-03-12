package io.embrace.android.embracesdk.internal.ui

import android.app.Activity
import android.os.Build.VERSION_CODES.M
import android.os.Handler
import android.os.Looper
import android.os.Message
import androidx.annotation.RequiresApi

@RequiresApi(M)
class HandlerMessageDrawDetector(
    mainLooper: Looper
) : DrawEventEmitter {

    private val handler = Handler(mainLooper)

    override fun registerFirstDrawCallback(
        activity: Activity,
        drawBeginCallback: () -> Unit,
        firstFrameDeliveredCallback: () -> Unit
    ) {
        drawBeginCallback()
        handler.sendMessageAtFrontOfQueue(
            Message.obtain(handler, firstFrameDeliveredCallback).apply {
                isAsynchronous = true
            }
        )
    }

    override fun unregisterFirstDrawCallback(activity: Activity) {
    }
}
