package io.embrace.android.embracesdk.fakes

import android.app.Application

class FakeApplication : Application() {

    val lifecycleCallbacks = mutableListOf<ActivityLifecycleCallbacks>()
    val systemServices = mutableMapOf<String, Any>()

    override fun registerActivityLifecycleCallbacks(callback: ActivityLifecycleCallbacks) {
        lifecycleCallbacks.add(callback)
    }

    override fun unregisterActivityLifecycleCallbacks(callback: ActivityLifecycleCallbacks) {
        lifecycleCallbacks.remove(callback)
    }

    override fun getSystemService(name: String): Any? = systemServices[name]
}
