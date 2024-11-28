package io.embrace.android.embracesdk.internal

import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Bundle
import io.embrace.android.embracesdk.fakes.FakeClock

class ClockTickingActivityLifecycleCallbacks(
    private val clock: FakeClock
) : ActivityLifecycleCallbacks {

    override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
        clock.tick(PRE_DURATION)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        clock.tick(STATE_DURATION)
    }

    override fun onActivityPostCreated(activity: Activity, savedInstanceState: Bundle?) {
        clock.tick(POST_DURATION)
    }

    override fun onActivityPreStarted(activity: Activity) {
        clock.tick(PRE_DURATION)
    }

    override fun onActivityStarted(activity: Activity) {
        clock.tick(STATE_DURATION)
    }

    override fun onActivityPostStarted(activity: Activity) {
        clock.tick(POST_DURATION)
    }

    override fun onActivityPreResumed(activity: Activity) {
        clock.tick(PRE_DURATION)
    }

    override fun onActivityResumed(activity: Activity) {
        clock.tick(STATE_DURATION)
    }

    override fun onActivityPostResumed(activity: Activity) {
        clock.tick(POST_DURATION)
    }

    override fun onActivityPrePaused(activity: Activity) {
        clock.tick(PRE_DURATION)
    }

    override fun onActivityPaused(activity: Activity) {
        clock.tick(STATE_DURATION)
    }

    override fun onActivityPostPaused(activity: Activity) {
        clock.tick(POST_DURATION)
    }

    override fun onActivityStopped(activity: Activity) {
        clock.tick(STATE_DURATION)
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
    }

    override fun onActivityDestroyed(activity: Activity) {
    }

    companion object {
        const val PRE_DURATION = 2L
        const val STATE_DURATION = 10L
        const val POST_DURATION = 1L
    }
}
