/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.embrace.android.embracesdk.internal.session.lifecycle

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import io.embrace.android.embracesdk.internal.arch.state.ProcessState
import io.embrace.android.embracesdk.internal.arch.state.ProcessStateListener
import io.embrace.android.embracesdk.internal.injection.getSystemServiceSafe

/**
 * Reports process state transitions by counting started activities, without depending on
 * androidx.lifecycle. This very closely follows the implementation of androidx's ProcessLifecycleOwner (Apache 2.0)
 * https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:lifecycle/lifecycle-process/src/main/java/androidx/lifecycle/ProcessLifecycleOwner.kt
 *
 * There are a few differences:
 *
 * 1. Tracking begins when the class is constructed, rather than in `InitializationProvider`. We attempt to
 * address this by setting the initial process state via ActivityManager if Context is an activity at init. Otherwise,
 * we assume we're in the background.
 * 2. The implementation is simplified as several concepts/classes are not relevant to our SDK
 */
class ActivityProcessLifecycleTracker(
    private val application: Application,
    startupContext: Context?,
    private val handler: Handler = Handler(Looper.getMainLooper()),
) : LifecycleTracker, Application.ActivityLifecycleCallbacks {

    private val lock = Any()

    /**
     * Number of activities whose start we have observed and whose stop we have not.
     */
    private var startedCounter = 0

    /**
     * Whether an activity was already started when this class was constructed. It absorbs the one
     * unmatched stop that will eventually be reported for that activity.
     */
    private var unobservedStartedActivity = false

    @Volatile
    private var foreground = false

    @Volatile
    private var listener: ProcessStateListener? = null

    private val backgroundRunnable = Runnable {
        val transitioned = synchronized(lock) {
            val shouldTransition = foreground && startedActivityCount() == 0
            if (shouldTransition) {
                foreground = false
            }
            shouldTransition
        }
        if (transitioned) {
            listener?.onBackground()
        }
    }

    init {
        // resolve the initial process state here. If Context is an Activity it indicates misuse of the SDK
        // that would suggest the process could be in the foreground. Otherwise, we assume Application#onCreate()
        // or a ContentProvider, that would mean we're in the background.
        val hasLiveActivity = startupContext.findLiveActivity() != null
        foreground = hasLiveActivity || startupContext.isForegroundProcess()
        unobservedStartedActivity = foreground
    }

    override fun getProcessState(): ProcessState = when {
        foreground -> ProcessState.FOREGROUND
        else -> ProcessState.BACKGROUND
    }

    override fun register(listener: ProcessStateListener) {
        this.listener = listener
        runCatching {
            application.registerActivityLifecycleCallbacks(this)
        }

        // androidx replays the current state to a newly added observer, so report the transition
        // that was missed by registering late.
        if (foreground) {
            listener.onForeground()
        }
    }

    override fun onActivityStarted(activity: Activity) {
        handler.removeCallbacks(backgroundRunnable)

        val transitioned = synchronized(lock) {
            startedCounter++
            val wasBackground = !foreground
            foreground = true
            wasBackground
        }
        if (transitioned) {
            listener?.onForeground()
        }
    }

    override fun onActivityStopped(activity: Activity) {
        val debounce = synchronized(lock) {
            if (startedCounter > 0) {
                startedCounter--
            } else {
                unobservedStartedActivity = false
            }
            foreground && startedActivityCount() == 0
        }
        if (debounce) {
            handler.postDelayed(backgroundRunnable, BACKGROUND_DEBOUNCE_MS)
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

    override fun onActivityResumed(activity: Activity) {}

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {}

    private fun startedActivityCount(): Int = startedCounter + if (unobservedStartedActivity) 1 else 0

    /**
     * Walks the [ContextWrapper] chain looking for an activity that has neither finished nor been
     * destroyed. The SDK is frequently started from an activity, which is a strong signal that the
     * process is already in the foreground.
     */
    private fun Context?.findLiveActivity(): Activity? {
        var ctx = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) {
                return ctx.takeUnless { it.isFinishing || it.isDestroyed }
            }
            ctx = ctx.baseContext
        }
        return null
    }

    /**
     * Fallback for when the SDK was not started from an activity. Only IMPORTANCE_FOREGROUND counts -
     * a process running a foreground service is not showing any UI.
     */
    private fun Context?.isForegroundProcess(): Boolean = runCatching {
        val manager = this?.getSystemServiceSafe<ActivityManager>(Context.ACTIVITY_SERVICE)
        val pid = Process.myPid()
        val process = manager?.runningAppProcesses?.firstOrNull { it.pid == pid }
        process?.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }.getOrDefault(false)

    internal companion object {

        /**
         * Matches the TIMEOUT_MS value used by androidx's ProcessLifecycleOwner.
         */
        const val BACKGROUND_DEBOUNCE_MS = 700L
    }
}
