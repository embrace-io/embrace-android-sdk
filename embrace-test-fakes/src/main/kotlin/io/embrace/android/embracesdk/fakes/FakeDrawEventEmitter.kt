package io.embrace.android.embracesdk.fakes

import android.app.Activity
import io.embrace.android.embracesdk.internal.capture.activity.traceInstanceId
import io.embrace.android.embracesdk.internal.ui.DrawEventEmitter
import java.util.concurrent.ConcurrentHashMap

class FakeDrawEventEmitter : DrawEventEmitter {

    val registeredActivities: MutableMap<Int, () -> Unit> = ConcurrentHashMap()
    var lastRegisteredActivity: Activity? = null
    var lastUnregisteredActivity: Activity? = null
    var lastCallback: (() -> Unit)? = null

    override fun registerFirstDrawCallback(activity: Activity, completionCallback: () -> Unit) {
        registeredActivities[traceInstanceId(activity)] = completionCallback
        lastRegisteredActivity = activity
        lastCallback = completionCallback
    }

    override fun unregisterFirstDrawCallback(activity: Activity) {
        registeredActivities.remove(traceInstanceId(activity))
        lastUnregisteredActivity = activity
    }

    fun draw(activity: Activity) {
        registeredActivities[traceInstanceId(activity)]?.invoke()
    }
}
