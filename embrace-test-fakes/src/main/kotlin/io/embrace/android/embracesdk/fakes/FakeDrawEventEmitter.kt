package io.embrace.android.embracesdk.fakes

import android.app.Activity
import io.embrace.android.embracesdk.internal.capture.activity.traceInstanceId
import io.embrace.android.embracesdk.internal.ui.DrawEventEmitter
import java.util.concurrent.ConcurrentHashMap

class FakeDrawEventEmitter : DrawEventEmitter {

    val registeredActivities: MutableMap<Int, Pair<() -> Unit, () -> Unit>> = ConcurrentHashMap()
    var lastRegisteredActivity: Activity? = null
    var lastUnregisteredActivity: Activity? = null
    var lastFirstFrameDeliveredCallback: (() -> Unit)? = null

    override fun registerFirstDrawCallback(
        activity: Activity,
        drawBeginCallback: () -> Unit,
        firstFrameDeliveredCallback: () -> Unit
    ) {
        registeredActivities[traceInstanceId(activity)] = drawBeginCallback to firstFrameDeliveredCallback
        lastRegisteredActivity = activity
        lastFirstFrameDeliveredCallback = firstFrameDeliveredCallback
    }

    override fun unregisterFirstDrawCallback(activity: Activity) {
        registeredActivities.remove(traceInstanceId(activity))
        lastUnregisteredActivity = activity
    }

    fun draw(activity: Activity, gapCallback: () -> Unit = {}) {
        registeredActivities[traceInstanceId(activity)]?.run {
            first()
            gapCallback()
            second()
        }
    }
}
