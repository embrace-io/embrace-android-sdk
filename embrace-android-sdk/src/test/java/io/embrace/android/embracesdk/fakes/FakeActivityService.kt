package io.embrace.android.embracesdk.fakes

import android.app.Activity
import android.content.res.Configuration
import android.os.Bundle
import io.embrace.android.embracesdk.session.ActivityListener
import io.embrace.android.embracesdk.session.ActivityService

internal class FakeActivityService(
    override var isInBackground: Boolean = false,
    override var foregroundActivity: Activity? = null
) : ActivityService {

    val listeners: MutableList<ActivityListener> = mutableListOf()
    var config: Configuration? = null

    override fun addListener(listener: ActivityListener) {
        listeners.add(listener)
    }

    override fun onActivityCreated(p0: Activity, p1: Bundle?) {
        TODO("Not yet implemented")
    }

    override fun onActivityStarted(p0: Activity) {
        TODO("Not yet implemented")
    }

    override fun onActivityResumed(p0: Activity) {
        TODO("Not yet implemented")
    }

    override fun onActivityPaused(p0: Activity) {
        TODO("Not yet implemented")
    }

    override fun onActivityStopped(p0: Activity) {
        TODO("Not yet implemented")
    }

    override fun onActivitySaveInstanceState(p0: Activity, p1: Bundle) {
        TODO("Not yet implemented")
    }

    override fun onActivityDestroyed(p0: Activity) {
        TODO("Not yet implemented")
    }

    override fun close() {
    }

    override fun onForeground() {
    }

    override fun onBackground() {
    }
}
