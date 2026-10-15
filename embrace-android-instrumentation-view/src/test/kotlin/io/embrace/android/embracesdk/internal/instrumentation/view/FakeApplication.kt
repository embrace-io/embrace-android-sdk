package io.embrace.android.embracesdk.internal.instrumentation.view

import android.app.Application

internal class FakeApplication : Application() {

    val lifecycleCallbacks = mutableListOf<ActivityLifecycleCallbacks>()

    override fun registerActivityLifecycleCallbacks(callback: ActivityLifecycleCallbacks) {
        lifecycleCallbacks.add(callback)
    }

    override fun unregisterActivityLifecycleCallbacks(callback: ActivityLifecycleCallbacks) {
        lifecycleCallbacks.remove(callback)
    }
}
