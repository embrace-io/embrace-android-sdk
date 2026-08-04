package io.embrace.android.embracesdk.instrumentation.leaks

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import java.lang.ref.PhantomReference
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class ActivityLeakDetectionLifecycleCallbacks : Application.ActivityLifecycleCallbacks {
    private val possibleLeaks = AtomicReference<ArrayList<PossibleLeakRef>>(ArrayList<PossibleLeakRef>())

    private val queue = ReferenceQueue<Any>()

    init {
        thread {
            while (true) {
                val ref = PhantomReference(Any(), queue)
                queue.remove()
                ref.clear()

                // GC Detected
                val toBeChecked = possibleLeaks.getAndSet(ArrayList())
                toBeChecked.forEach { ref ->
                    if (ref.get() != null) {
                        // we need to provide a grace period here, this "leak" might have only just been destroyed
                        Log.i("LeakDetection", "GC: ${ref.name} was leaked!")
                    }
                }
            }
        }
    }

    override fun onActivityDestroyed(activity: Activity) {
        possibleLeaks.get().add(PossibleLeakRef(activity, activity.componentName.className))
    }

    private class PossibleLeakRef(referent: Activity, val name: String) : WeakReference<Activity>(referent)

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
}
