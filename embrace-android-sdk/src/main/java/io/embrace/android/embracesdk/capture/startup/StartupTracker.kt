package io.embrace.android.embracesdk.capture.startup

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import io.embrace.android.embracesdk.annotation.StartupActivity
import io.embrace.android.embracesdk.internal.utils.VersionChecker

/**
 * Component that captures various timestamps throughout the startup process and uses that information to log spans that approximates to
 * the canonical cold and warm "Time to First Display" metric.
 *
 * In the abstract, we want to be as close as possible to approximate the duration between the start of the app process, to when the first
 * frame is completely rendered in the first useful activity. Because of the different capabilities of each Android version, we are
 * measuring slightly different events during startup, so there will be several flavors the startup trace depending on which version of
 * Android the app is currently running on.
 *
 * For app process start time:
 *
 * - Android 13 onwards, it is determined by when the app process is requested to be specialized, which could be some time after the
 *   the zygote process is created, depending on, perhaps among other things, the manufacturer of the device. If this value is used,
 *   it value will be captured in an attribute on the root span of the trace.
 *
 * - Android 7.0 to 12 (inclusive), it is determined by when the app process is created. Some OEMs on some versions of Android are known to
 *   pre-created a bunch of zygotes in order to speed up start time, to mixed success. The fact that Pixel devices don't do this should
 *   tell you how effectively that strategy overall is.
 *
 * - Older Android version that are supported, we use the SDK startup time (for now).
 *
 * For approximating the first frame being completely drawn:
 *
 * - Android 10 onwards, we use a [ViewTreeObserver.OnDrawListener] callback to detect that the first frame from the first activity load
 *   has been fully rendered and queued for display.
 *
 * - Older Android versions that are supported, we just use when the first Activity was resumed.
 *
 * Note that this implementation has benefited from the work of Pierre-Yves Ricau and his blog post about Android application launch time
 * that can be found here: https://blog.p-y.wtf/tracking-android-app-launch-in-production. PY's code was adapted and tweaked for use here.
 */
internal class StartupTracker(
    private val appStartupTraceEmitter: AppStartupTraceEmitter,
    private val versionChecker: VersionChecker,
) : Application.ActivityLifecycleCallbacks {
    private var isFirstDraw = false

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity.observeForStartup()) {
            appStartupTraceEmitter.startupActivityInitStart()
            if (versionChecker.isAtLeast(Build.VERSION_CODES.Q)) {
                if (!isFirstDraw) {
                    val window = activity.window
                    window.onDecorViewReady {
                        val decorView = window.decorView
                        decorView.onNextDraw {
                            if (!isFirstDraw) {
                                isFirstDraw = true
                                val callback = { appStartupTraceEmitter.firstFrameRendered() }
                                decorView.viewTreeObserver.registerFrameCommitCallback(callback)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity.observeForStartup()) {
            appStartupTraceEmitter.startupActivityInitStart()
        }
    }

    override fun onActivityStarted(activity: Activity) {
        if (activity.observeForStartup()) {
            appStartupTraceEmitter.startupActivityInitEnd()
        }
    }

    override fun onActivityResumed(activity: Activity) {
        if (activity.observeForStartup()) {
            appStartupTraceEmitter.startupActivityResumed()
        }
    }

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {}

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {}

    companion object {
        private class PyNextDrawListener(
            val view: View,
            val onDrawCallback: () -> Unit
        ) : ViewTreeObserver.OnDrawListener {
            val handler = Handler(Looper.getMainLooper())
            var invoked = false

            override fun onDraw() {
                if (!invoked) {
                    invoked = true
                    onDrawCallback()
                    handler.post {
                        if (view.viewTreeObserver.isAlive) {
                            view.viewTreeObserver.removeOnDrawListener(this)
                        }
                    }
                }
            }
        }

        private class PyWindowDelegateCallback(
            private val delegate: Window.Callback
        ) : Window.Callback by delegate {

            val onContentChangedCallbacks = mutableListOf<() -> Boolean>()

            override fun onContentChanged() {
                onContentChangedCallbacks.removeAll { callback ->
                    !callback()
                }
                delegate.onContentChanged()
            }
        }

        fun Activity.observeForStartup(): Boolean = !javaClass.isAnnotationPresent(StartupActivity::class.java)

        fun View.onNextDraw(onDrawCallback: () -> Unit) {
            viewTreeObserver.addOnDrawListener(
                PyNextDrawListener(this, onDrawCallback)
            )
        }

        fun Window.onDecorViewReady(callback: () -> Unit) {
            if (peekDecorView() == null) {
                onContentChanged {
                    callback()
                    return@onContentChanged false
                }
            } else {
                callback()
            }
        }

        private fun Window.onContentChanged(onDrawCallbackInvocation: () -> Boolean) {
            val currentCallback = callback
            val callback = if (currentCallback is PyWindowDelegateCallback) {
                currentCallback
            } else {
                val newCallback = PyWindowDelegateCallback(currentCallback)
                callback = newCallback
                newCallback
            }
            callback.onContentChangedCallbacks += onDrawCallbackInvocation
        }
    }
}
