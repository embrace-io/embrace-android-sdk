package io.embrace.android.embracesdk.internal.instrumentation.navigation

import android.app.Activity
import android.app.Application

internal class FakeApplication : Application() {

    private val lifecycleCallbacks = mutableListOf<ActivityLifecycleCallbacks>()

    /**
     * When set, reported as started and resumed to each callback as it is registered, as callbacks can fire as soon as they
     * are registered.
     */
    var resumedActivity: Activity? = null

    override fun registerActivityLifecycleCallbacks(callback: ActivityLifecycleCallbacks) {
        lifecycleCallbacks.add(callback)
        resumedActivity?.let { activity ->
            callback.start(activity)
            callback.resume(activity)
        }
    }

    override fun unregisterActivityLifecycleCallbacks(callback: ActivityLifecycleCallbacks) {
        lifecycleCallbacks.remove(callback)
    }

    fun start(activity: Activity) = lifecycleCallbacks.forEach { it.start(activity) }

    fun resume(activity: Activity) = lifecycleCallbacks.forEach { it.resume(activity) }

    fun pause(activity: Activity) = lifecycleCallbacks.forEach {
        it.onActivityPrePaused(activity)
        it.onActivityPaused(activity)
        it.onActivityPostPaused(activity)
    }

    private fun ActivityLifecycleCallbacks.start(activity: Activity) {
        onActivityPreStarted(activity)
        onActivityStarted(activity)
        onActivityPostStarted(activity)
    }

    private fun ActivityLifecycleCallbacks.resume(activity: Activity) {
        onActivityPreResumed(activity)
        onActivityResumed(activity)
        onActivityPostResumed(activity)
    }
}
