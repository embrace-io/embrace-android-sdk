package io.embrace.android.embracesdk.fakes

import android.app.Activity
import io.embrace.android.embracesdk.internal.ui.DrawEventEmitter

class FakeDrawEventEmitter : DrawEventEmitter {

    var lastRegisteredActivity: Activity? = null
    var lastUnregisteredActivity: Activity? = null
    var lastCallback: (() -> Unit)? = null

    override fun registerFirstDrawCallback(activity: Activity, completionCallback: () -> Unit) {
        lastRegisteredActivity = activity
        lastCallback = completionCallback
    }

    override fun unregisterFirstDrawCallback(activity: Activity) {
        lastUnregisteredActivity = activity
    }
}
