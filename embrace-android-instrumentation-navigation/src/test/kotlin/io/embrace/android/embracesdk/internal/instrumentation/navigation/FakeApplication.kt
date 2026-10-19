package io.embrace.android.embracesdk.internal.instrumentation.navigation

import android.app.Activity
import android.app.Application

internal class FakeApplication : Application() {

    private val lifecycleCallbacks = mutableListOf<ActivityLifecycleCallbacks>()

    override fun registerActivityLifecycleCallbacks(callback: ActivityLifecycleCallbacks) {
        lifecycleCallbacks.add(callback)
    }

    override fun unregisterActivityLifecycleCallbacks(callback: ActivityLifecycleCallbacks) {
        lifecycleCallbacks.remove(callback)
    }

    fun start(activity: Activity) = lifecycleCallbacks.forEach {
        it.onActivityPreStarted(activity)
        it.onActivityStarted(activity)
        it.onActivityPostStarted(activity)
    }

    fun resume(activity: Activity) = lifecycleCallbacks.forEach {
        it.onActivityPreResumed(activity)
        it.onActivityResumed(activity)
        it.onActivityPostResumed(activity)
    }

    fun pause(activity: Activity) = lifecycleCallbacks.forEach {
        it.onActivityPrePaused(activity)
        it.onActivityPaused(activity)
        it.onActivityPostPaused(activity)
    }
}
