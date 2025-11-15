package io.embrace.android.embracesdk.fakes

import android.app.Activity
import io.embrace.android.embracesdk.internal.instrumentation.startup.activity.traceInstanceId
import io.embrace.android.embracesdk.internal.instrumentation.startup.ui.DrawEventEmitter
import java.util.concurrent.ConcurrentHashMap

class FakeDrawEventEmitter : DrawEventEmitter {

    val registeredActivities: MutableMap<Int, Pair<() -> Unit, () -> Unit>> = ConcurrentHashMap()
    var lastRegisteredActivity: Activity? = null
    var lastUnregisteredActivity: Activity? = null
    var lastFirstFrameDeliveredCallback: (() -> Unit)? = null

    override fun registerFirstDrawCallback(
        activity: Activity,
        drawBeginCallback: () -> Unit,
        drawCompleteCallback: () -> Unit
    ) {
        registeredActivities[traceInstanceId(activity)] = drawBeginCallback to drawCompleteCallback
        lastRegisteredActivity = activity
        lastFirstFrameDeliveredCallback = drawCompleteCallback
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
