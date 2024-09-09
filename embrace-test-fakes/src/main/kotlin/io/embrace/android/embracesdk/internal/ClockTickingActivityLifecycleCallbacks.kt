package io.embrace.android.embracesdk.internal

import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Bundle
import io.embrace.android.embracesdk.fakes.FakeClock

class ClockTickingActivityLifecycleCallbacks(
    private val clock: FakeClock
) : ActivityLifecycleCallbacks {

    override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
        clock.tick(2)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        clock.tick(10)
    }

    override fun onActivityPostCreated(activity: Activity, savedInstanceState: Bundle?) {
        clock.tick(1)
    }

    override fun onActivityPreStarted(activity: Activity) {
        clock.tick(2)
    }

    override fun onActivityStarted(activity: Activity) {
        clock.tick(10)
    }

    override fun onActivityPostStarted(activity: Activity) {
        clock.tick(1)
    }

    override fun onActivityPreResumed(activity: Activity) {
        clock.tick(2)
    }

    override fun onActivityResumed(activity: Activity) {
        clock.tick(10)
    }

    override fun onActivityPostResumed(activity: Activity) {
        clock.tick(1)
    }

    override fun onActivityPrePaused(activity: Activity) {
        clock.tick(2)
    }

    override fun onActivityPaused(activity: Activity) {
        clock.tick(10)
    }

    override fun onActivityPostPaused(activity: Activity) {
        clock.tick(1)
    }

    override fun onActivityStopped(activity: Activity) {
        clock.tick(10)
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
    }

    override fun onActivityDestroyed(activity: Activity) {
    }

    companion object {
        const val PRE_DURATION = 2
        const val STATE_DURATION = 10
        const val POST_DURATION = 1
    }
}
